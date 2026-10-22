#!/usr/bin/env python3
"""Converts text files to speech with Piper, optionally with a robotic effect.

Usage:
  ./text-to-voice.py [options] INPUT.txt [INPUT.txt ...]
  echo "Bonjour" | ./text-to-voice.py -o bonjour.wav -
  ./text-to-voice.py --list-voices

Each input is written next to itself (message.txt -> message.wav) unless --output or
--output-dir is given. The output format follows the extension: .wav (PCM 16-bit mono),
.ogg / .opus (Opus) or .mp3. Existing files are kept unless --force is given.

Piper is looked up in $PIPER_HOME (default ~/.local/share/piper), installed by setup-piper.sh.
ffmpeg is needed for the robot effect, resampling and non-WAV formats.
"""

import os
import sys
from pathlib import Path

PIPER_HOME = Path(os.environ.get("PIPER_HOME", Path.home() / ".local/share/piper"))
VENV_PYTHON = PIPER_HOME / "venv/bin/python"

if Path(sys.prefix).resolve() != (PIPER_HOME / "venv").resolve() and VENV_PYTHON.exists():
    os.execv(VENV_PYTHON, [str(VENV_PYTHON), __file__, *sys.argv[1:]])

import argparse
import json
import shutil
import subprocess
import tempfile
import wave

try:
    import numpy as np
    from piper import PiperVoice, SynthesisConfig
except ImportError:
    sys.exit(f"Piper not found in {PIPER_HOME}: run setup-piper.sh first (or set PIPER_HOME)")

DEFAULT_VOICE = "fr_FR-siwis-medium"
DEFAULT_SAMPLE_RATE = 22050
# Piper normalizes every sentence to full scale; keep headroom for resampling and encoding.
PEAK = 0.89
ENCODERS = {
    ".wav": ["-c:a", "pcm_s16le"],
    ".ogg": ["-c:a", "libopus", "-b:a", "32k"],
    ".opus": ["-c:a", "libopus", "-b:a", "32k"],
    ".mp3": ["-c:a", "libmp3lame", "-q:a", "4"],
}
# libopus rejects 22.05 and 44.1 kHz.
OPUS_SAMPLE_RATE = 48000


def robot_filter(sample_rate, frequency, mix):
    """Ring modulation blended with the dry voice, then metallic flanger, short echo and
    a small-speaker band-pass. Loudness is normalized because the blend lowers the level."""
    return (
        f"[0:a]aresample={sample_rate},aformat=sample_fmts=fltp:channel_layouts=mono,asplit[dry][mod];"
        f"aevalsrc='0.6+0.4*sin(2*PI*{frequency}*t)':s={sample_rate}:c=mono[carrier];"
        "[mod][carrier]amultiply[ring];"
        f"[dry][ring]amix=inputs=2:weights='{1 - mix} {mix}':duration=first,"
        "flanger=delay=1.5:depth=1.5:regen=40:speed=0.3,"
        "aecho=0.8:0.5:12:0.25,"
        "highpass=f=180,lowpass=f=6500,"
        "loudnorm=I=-16:TP=-2[out]"
    )


def list_voices():
    voices = sorted((PIPER_HOME / "voices").glob("*.onnx"))
    if not voices:
        print(f"No voice in {PIPER_HOME / 'voices'}: run setup-piper.sh")
        return
    for model in voices:
        config = json.loads(Path(f"{model}.json").read_text(encoding="utf-8"))
        speakers = ", ".join(config.get("speaker_id_map", {})) or "-"
        print("%-28s %6d Hz  speakers: %s" % (model.stem, config["audio"]["sample_rate"], speakers))


def load_voice(name):
    model = Path(name) if name.endswith(".onnx") else PIPER_HOME / "voices" / f"{name}.onnx"
    if not model.exists():
        sys.exit(f"Voice not found: {model} (install it with: setup-piper.sh --voice {name})")
    return PiperVoice.load(model)


def speaker_id(voice, speaker):
    if speaker is None:
        return None
    names = voice.config.speaker_id_map
    if speaker.isdigit() and int(speaker) < voice.config.num_speakers:
        return int(speaker)
    if speaker in names:
        return names[speaker]
    available = ", ".join(names) or "this voice has a single speaker"
    sys.exit(f"Unknown speaker '{speaker}' ({available})")


def read_text(source):
    raw = sys.stdin.read() if source == "-" else Path(source).read_text(encoding="utf-8")
    return " ".join(raw.replace("\u00ad", "").split())


def output_path(source, args):
    if args.output:
        return Path(args.output)
    name = Path(source).with_suffix("." + args.format).name
    return Path(args.output_dir) / name if args.output_dir else Path(source).with_name(name)


def synthesize(voice, text, syn_config, sentence_silence, lead_silence):
    rate = voice.config.sample_rate
    gap = np.zeros(int(rate * sentence_silence), dtype=np.float32)
    parts = [np.zeros(int(rate * lead_silence), dtype=np.float32)]
    for index, chunk in enumerate(voice.synthesize(text, syn_config)):
        if index:
            parts.append(gap)
        parts.append(chunk.audio_float_array)
    if len(parts) == 1:
        return None
    audio = np.clip(np.concatenate(parts) * PEAK, -1.0, 1.0)
    return (audio * 32767).astype(np.int16)


def write_wav(path, samples, sample_rate):
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        wav.writeframes(samples.tobytes())


def export(raw_wav, raw_rate, target, args):
    if args.effect != "robot" and target.suffix == ".wav" and raw_rate == args.sample_rate:
        shutil.move(raw_wav, target)
        return True
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg is required for the robot effect, resampling and non-WAV formats")
    rate = OPUS_SAMPLE_RATE if target.suffix in (".ogg", ".opus") else args.sample_rate
    cmd = ["ffmpeg", "-loglevel", "error", "-y", "-i", str(raw_wav)]
    if args.effect == "robot":
        cmd += ["-filter_complex", robot_filter(rate, args.robot_frequency, args.robot_mix), "-map", "[out]"]
    cmd += ["-ac", "1", "-ar", str(rate), *ENCODERS[target.suffix], str(target)]
    if subprocess.run(cmd).returncode != 0:
        target.unlink(missing_ok=True)
        return False
    return True


def parse_args():
    parser = argparse.ArgumentParser(
        description="Convert text files to speech with Piper.",
        epilog=f"Voices are read from {PIPER_HOME / 'voices'} (set PIPER_HOME to change it).",
    )
    parser.add_argument("inputs", nargs="*", metavar="INPUT", help="UTF-8 text file, or - for stdin")
    parser.add_argument("-o", "--output", help="output file (single input only)")
    parser.add_argument("-d", "--output-dir", help="directory for the outputs (default: next to each input)")
    parser.add_argument("-f", "--format", default="wav", choices=[e[1:] for e in ENCODERS],
                        help="output format when the name is derived from the input (default: wav)")
    parser.add_argument("-v", "--voice", default=DEFAULT_VOICE,
                        help=f"installed voice name or path to an .onnx model (default: {DEFAULT_VOICE})")
    parser.add_argument("-s", "--speaker", help="speaker name or id for multi-speaker voices")
    parser.add_argument("--speed", type=float, default=1.0, help="speaking rate, 0.9 = 10%% slower (default: 1.0)")
    parser.add_argument("--noise-scale", type=float,
                        help="intonation variability, lower is flatter and more robotic (voice default: 0.667)")
    parser.add_argument("--sentence-silence", type=float, default=0.3,
                        help="seconds of silence between sentences (default: 0.3)")
    parser.add_argument("--lead-silence", type=float, default=0.3,
                        help="seconds of silence before the first word (default: 0.3)")
    parser.add_argument("--effect", default="none", choices=["none", "robot"], help="post-processing (default: none)")
    parser.add_argument("--robot-mix", type=float, default=0.5,
                        help="share of ring-modulated voice, 0 to 1 (default: 0.5)")
    parser.add_argument("--robot-frequency", type=float, default=40,
                        help="ring modulation frequency in Hz, higher is harsher (default: 40)")
    parser.add_argument("-r", "--sample-rate", type=int, default=DEFAULT_SAMPLE_RATE,
                        help=f"output sample rate, Opus is always {OPUS_SAMPLE_RATE} (default: {DEFAULT_SAMPLE_RATE})")
    parser.add_argument("--force", action="store_true", help="overwrite existing outputs")
    parser.add_argument("--list-voices", action="store_true", help="list installed voices and exit")
    args = parser.parse_args()

    if args.list_voices:
        return args
    if not args.inputs:
        parser.error("no input file")
    if args.output and len(args.inputs) > 1:
        parser.error("--output only works with a single input, use --output-dir")
    if args.output and args.output_dir:
        parser.error("--output and --output-dir are mutually exclusive")
    if "-" in args.inputs and not args.output:
        parser.error("reading from stdin requires --output")
    if args.output and Path(args.output).suffix not in ENCODERS:
        parser.error(f"unsupported output extension, use one of: {', '.join(ENCODERS)}")
    if not 0 <= args.robot_mix <= 1:
        parser.error("--robot-mix must be between 0 and 1")
    if args.speed <= 0:
        parser.error("--speed must be positive")
    return args


def main():
    args = parse_args()
    if args.list_voices:
        list_voices()
        return

    voice = load_voice(args.voice)
    syn_config = SynthesisConfig(
        speaker_id=speaker_id(voice, args.speaker),
        length_scale=voice.config.length_scale / args.speed,
        noise_scale=args.noise_scale,
    )

    failures = 0
    for source in args.inputs:
        target = output_path(source, args)
        if target.exists() and not args.force:
            print(f"{source}: {target} exists, skipped (use --force)", file=sys.stderr)
            continue
        text = read_text(source)
        samples = synthesize(voice, text, syn_config, args.sentence_silence, args.lead_silence) if text else None
        if samples is None:
            print(f"{source}: no text to speak", file=sys.stderr)
            failures += 1
            continue

        target.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory() as tmp:
            raw_wav = Path(tmp) / "raw.wav"
            write_wav(raw_wav, samples, voice.config.sample_rate)
            if not export(raw_wav, voice.config.sample_rate, target, args):
                print(f"{source}: ffmpeg failed", file=sys.stderr)
                failures += 1
                continue
        print(f"{source} -> {target} ({len(samples) / voice.config.sample_rate:.1f} s)")

    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()

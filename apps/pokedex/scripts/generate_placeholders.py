#!/usr/bin/env python3
"""Creates placeholder images and descriptions for the Pokémon that have none yet,
plus the interface sounds. Real files are never overwritten.

Usage:
  ./generate_placeholders.py [--force] [--sounds-only]

  --force        also regenerate files that are still placeholders (never real files)
  --sounds-only  only (re)generate the interface sounds in assets/sounds

Placeholders are recorded in placeholders-manifest.json (path -> sha256), which lets
check_assets.py tell them apart from real recordings.
"""

import argparse
import colorsys
import math
import struct
import sys
import wave
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import pokedex_assets as pa

SAMPLE_RATE = 22050
FONT_CANDIDATES = [
    "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/google-noto/NotoSans-Bold.ttf",
]


def font(size):
    for candidate in FONT_CANDIDATES:
        if Path(candidate).exists():
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default(size=size)


def make_image(path, number, name):
    size = 512
    hue = (number * 0.618033988749895) % 1.0
    r, g, b = (int(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.45, 0.95))
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse((40, 40, size - 40, size - 40), fill=(r, g, b, 255), outline=(34, 34, 34, 255), width=10)
    draw.text((size / 2, size / 2 - 40), "%03d" % number, font=font(150), fill=(34, 34, 34, 255), anchor="mm")
    label = name if len(name) <= 12 else name[:11] + "…"
    draw.text((size / 2, size / 2 + 90), label, font=font(52), fill=(34, 34, 34, 255), anchor="mm")
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)


def tone(frequencies, durations, volume=0.35, gap=0.02):
    samples = []
    for freq, duration in zip(frequencies, durations):
        count = int(SAMPLE_RATE * duration)
        for i in range(count):
            t = i / SAMPLE_RATE
            envelope = min(1.0, i / (0.01 * SAMPLE_RATE)) * min(1.0, (count - i) / (0.03 * SAMPLE_RATE))
            value = math.sin(2 * math.pi * freq * t) + 0.3 * math.sin(4 * math.pi * freq * t)
            samples.append(volume * envelope * value / 1.3)
        samples.extend([0.0] * int(SAMPLE_RATE * gap))
    return samples


def write_wav(path, samples):
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in samples))


def description_placeholder(number):
    base = 392 + (number % 12) * 22
    return tone([base, base * 1.25, base * 1.5], [0.18, 0.18, 0.35], volume=0.25)


SOUNDS = {
    "sounds/listen.wav": lambda: tone([660, 880], [0.09, 0.14]),
    "sounds/success.wav": lambda: tone([523, 659, 784, 1047], [0.08, 0.08, 0.08, 0.22]),
    "sounds/error.wav": lambda: tone([392, 330], [0.15, 0.3]),
    "sounds/missing.wav": lambda: tone([440, 440], [0.2, 0.2], volume=0.2, gap=0.12),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--sounds-only", action="store_true")
    args = parser.parse_args()

    manifest = pa.load_manifest()

    def can_write(relative):
        target = pa.ASSETS / relative
        if not target.exists():
            return True
        return args.force and manifest.get(relative) == pa.sha256(target)

    created = 0
    for relative, generator in SOUNDS.items():
        target = pa.ASSETS / relative
        if not target.exists() or args.force:
            write_wav(target, generator())
            created += 1

    if not args.sounds_only:
        for entry in pa.load_pokedex()["pokemon"]:
            image = pa.image_path(entry)
            if can_write(image):
                make_image(pa.ASSETS / image, entry["id"], entry["name"])
                manifest[image] = pa.sha256(pa.ASSETS / image)
                created += 1
            audio = pa.audio_path(entry)
            if can_write(audio):
                write_wav(pa.ASSETS / audio, description_placeholder(entry["id"]))
                manifest[audio] = pa.sha256(pa.ASSETS / audio)
                created += 1
        pa.save_manifest(manifest)

    print("%d fichier(s) générés" % created)
    return 0


if __name__ == "__main__":
    sys.exit(main())

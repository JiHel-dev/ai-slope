#!/bin/sh
# Installs Piper (offline neural text-to-speech) and voice models without sudo:
#   - a Python virtual environment with the piper-tts package
#   - voice models downloaded from Hugging Face (rhasspy/piper-voices)
#
# Usage:
#   ./setup-piper.sh [--prefix DIR] [--voice NAME]... [--proxy host:port] [--ca-cert FILE]
#                    [--list-remote] [-h|--help]
#
# Running it again only downloads what is missing.

set -eu

PREFIX="${PIPER_HOME:-$HOME/.local/share/piper}"
VOICES=""
PROXY=""
CA_CERT=""
LIST_REMOTE=0
DEFAULT_VOICES="fr_FR-siwis-medium fr_FR-tom-medium fr_FR-upmc-medium fr_FR-gilles-low"

print_help() {
  echo "Install Piper and its voice models into a user directory"
  echo
  echo "Optional:"
  echo "  --prefix DIR           Install directory (default: $PREFIX, or \$PIPER_HOME)"
  echo "  --voice NAME           Voice to download, repeatable (default: $DEFAULT_VOICES)"
  echo "  --proxy HOST:PORT      HTTP(S) proxy used for pip and voice downloads"
  echo "  --ca-cert FILE         Extra CA certificate (PEM) to trust, e.g. a corporate TLS inspection CA."
  echo "                         It is only used by this script, never installed system-wide."
  echo "  --list-remote          List every voice available for download, then exit"
  echo "  -h, --help             Show this help"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --prefix) PREFIX="$2"; shift 2 ;;
    --voice) VOICES="$VOICES $2"; shift 2 ;;
    --proxy) PROXY="$2"; shift 2 ;;
    --ca-cert) CA_CERT="$2"; shift 2 ;;
    --list-remote) LIST_REMOTE=1; shift ;;
    -h|--help) print_help; exit 0 ;;
    *) echo "Unknown option: $1" >&2; print_help >&2; exit 1 ;;
  esac
done

[ -n "$VOICES" ] || VOICES="$DEFAULT_VOICES"

command -v python3 >/dev/null 2>&1 || { echo "Missing required command: python3" >&2; exit 1; }
command -v ffmpeg >/dev/null 2>&1 || echo "Warning: ffmpeg not found, text-to-voice.py will only write raw WAV files" >&2

if [ -n "$CA_CERT" ]; then
  [ -f "$CA_CERT" ] || { echo "CA certificate not found: $CA_CERT" >&2; exit 1; }
  CA_BUNDLE="$(mktemp)"
  trap 'rm -f "$CA_BUNDLE"' EXIT
  for f in /etc/pki/tls/certs/ca-bundle.crt /etc/ssl/certs/ca-certificates.crt /etc/ssl/cert.pem; do
    if [ -f "$f" ]; then cat "$f" > "$CA_BUNDLE"; break; fi
  done
  cat "$CA_CERT" >> "$CA_BUNDLE"
  export PIP_CERT="$CA_BUNDLE"
  export SSL_CERT_FILE="$CA_BUNDLE"
fi

if [ -n "$PROXY" ]; then
  export https_proxy="http://$PROXY"
  export http_proxy="http://$PROXY"
fi

VENV="$PREFIX/venv"
VOICES_DIR="$PREFIX/voices"
mkdir -p "$VOICES_DIR"

if [ ! -x "$VENV/bin/piper" ]; then
  echo "==> Installing piper-tts into $VENV"
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install --quiet --disable-pip-version-check piper-tts
else
  echo "==> piper-tts already installed"
fi

if [ "$LIST_REMOTE" -eq 1 ]; then
  "$VENV/bin/python" -m piper.download_voices
  exit 0
fi

for voice in $VOICES; do
  if [ -s "$VOICES_DIR/$voice.onnx" ] && [ -s "$VOICES_DIR/$voice.onnx.json" ]; then
    echo "==> Voice $voice already installed"
  else
    echo "==> Downloading voice $voice"
    "$VENV/bin/python" -m piper.download_voices --download-dir "$VOICES_DIR" "$voice"
  fi
done

echo
echo "Piper ready in $PREFIX"
if [ "$PREFIX" != "$HOME/.local/share/piper" ]; then
  echo "Export it before converting:  export PIPER_HOME=\"$PREFIX\""
fi
echo "List voices with:  ./text-to-voice.py --list-voices"

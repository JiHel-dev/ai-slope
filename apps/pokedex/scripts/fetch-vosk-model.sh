#!/bin/sh
# Downloads the French Vosk model into the app assets (not versioned).
#
# Usage:
#   ./fetch-vosk-model.sh [--model vosk-model-small-fr-0.22] [--force] [-h|--help]
#
# Honours https_proxy and CURL_CA_BUNDLE (see tools/setup-android-toolchain.sh).

set -eu

MODEL="vosk-model-small-fr-0.22"
FORCE="0"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TARGET="$SCRIPT_DIR/../app/src/main/assets/model-fr"

print_help() {
  echo "Download a Vosk model into app/src/main/assets/model-fr"
  echo
  echo "Optional:"
  echo "  --model NAME     Model name on alphacephei.com (default: $MODEL)"
  echo "  --force          Replace an existing model"
  echo "  -h, --help       Show this help"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --model) MODEL="$2"; shift 2 ;;
    --force) FORCE="1"; shift ;;
    -h|--help) print_help; exit 0 ;;
    *) echo "Unknown option: $1" >&2; print_help >&2; exit 1 ;;
  esac
done

if [ -f "$TARGET/uuid" ] && [ "$FORCE" = "0" ]; then
  echo "Model already present in $TARGET (use --force to replace it)"
  exit 0
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "==> Downloading $MODEL"
curl -fSL --retry 2 --connect-timeout 20 -o "$TMP_DIR/model.zip" "https://alphacephei.com/vosk/models/$MODEL.zip"
unzip -q "$TMP_DIR/model.zip" -d "$TMP_DIR"

rm -rf "$TARGET"
mkdir -p "$(dirname "$TARGET")"
mv "$TMP_DIR/$MODEL" "$TARGET"

# StorageService.unpack() re-extracts the model on the device only when this id changes.
python3 -c 'import uuid; print(uuid.uuid4())' > "$TARGET/uuid"

echo "Model installed in $TARGET ($(du -sh "$TARGET" | cut -f1))"

"""Shared helpers for the Pokédex asset scripts (mirrors the app's Kotlin rules)."""

import hashlib
import json
import re
import struct
import unicodedata
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent.parent
ASSETS = APP_DIR / "app" / "src" / "main" / "assets"
POKEDEX_JSON = ASSETS / "pokemon" / "pokedex.json"
MODEL_DIR = ASSETS / "model-fr"
PLACEHOLDER_MANIFEST = APP_DIR / "scripts" / "placeholders-manifest.json"
EXPECTED_COUNT = 151

# Keep in sync with NameNormalizer.kt
FILLERS = {
    "le", "la", "les", "l", "un", "une", "c", "cest", "est", "ce",
    "pokemon", "euh", "heu", "hum", "alors", "je", "veux", "voir",
    "dis", "montre", "moi", "unk",
}


def load_pokedex():
    with POKEDEX_JSON.open(encoding="utf-8") as f:
        return json.load(f)


def image_path(entry):
    return entry.get("image") or "pokemon/images/%04d.png" % entry["id"]


def audio_path(entry):
    return entry.get("audio") or "pokemon/audio/%04d.wav" % entry["id"]


def words(text):
    lower = text.lower().replace("œ", "oe").replace("æ", "ae")
    folded = "".join(c for c in unicodedata.normalize("NFD", lower) if unicodedata.category(c) != "Mn")
    return [w for w in re.split(r"[^a-z0-9]+", folded) if w and w not in FILLERS]


def normalize(text):
    return "".join(words(text))


def spoken_form(text):
    """Same as NameNormalizer.spokenForm: the phrase given to the Vosk grammar."""
    cleaned = re.sub(r"[^\w' ]+|[\d_]+", " ", text.lower())
    return re.sub(r"\s+", " ", cleaned).strip()


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_manifest():
    if PLACEHOLDER_MANIFEST.exists():
        return json.loads(PLACEHOLDER_MANIFEST.read_text(encoding="utf-8"))
    return {}


def save_manifest(manifest):
    PLACEHOLDER_MANIFEST.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def model_vocabulary():
    """Word list embedded in the Vosk graph (OpenFst symbol table of graph/Gr.fst), or None."""
    words_txt = MODEL_DIR / "graph" / "words.txt"
    if words_txt.exists():
        return {line.split()[0] for line in words_txt.read_text(encoding="utf-8").splitlines() if line.strip()}
    fst = MODEL_DIR / "graph" / "Gr.fst"
    if not fst.exists():
        return None
    with fst.open("rb") as f:
        def i32():
            return struct.unpack("<i", f.read(4))[0]

        def i64():
            return struct.unpack("<q", f.read(8))[0]

        def string():
            return f.read(i32()).decode("utf-8", "replace")

        if i32() != 2125659606:
            return None
        string(), string()  # fst type, arc type
        i32()  # version
        flags = i32()
        f.read(8 + 8 * 3)  # properties, start, states, arcs
        if not flags & 1:
            return None
        if i32() != 2125658996:
            return None
        string()  # table name
        i64()  # available key
        vocabulary = set()
        for _ in range(i64()):
            vocabulary.add(string())
            i64()
        return vocabulary

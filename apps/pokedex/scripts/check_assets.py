#!/usr/bin/env python3
"""Reports the state of the Pokédex data: structure of pokedex.json, alias collisions,
images and descriptions (real, placeholder or missing) and, if the Vosk model is present,
names that the restricted grammar cannot recognise.

Usage:
  ./check_assets.py [--strict] [--verbose]

  --strict   exit with an error while placeholders remain (final acceptance)
  --verbose  list every Pokémon instead of a summary

Exit code: 0 when the data is usable, 1 otherwise.
"""

import argparse
import sys
import wave
from collections import defaultdict

from PIL import Image

import pokedex_assets as pa

# Spoken names that are expected to be shared and resolved as ambiguous.
EXPECTED_COLLISIONS = {"nidoran"}


def file_status(relative, manifest, kind):
    path = pa.ASSETS / relative
    if not path.exists():
        return "manquant"
    try:
        if kind == "image":
            with Image.open(path) as img:
                img.verify()
        else:
            with wave.open(str(path)) as w:
                if w.getnframes() == 0:
                    return "invalide (vide)"
    except Exception as e:  # noqa: BLE001 - report any unreadable file
        return "invalide (%s)" % e
    if manifest.get(relative) == pa.sha256(path):
        return "provisoire"
    return "ok"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--strict", action="store_true")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    errors = []
    warnings = []
    data = pa.load_pokedex()
    entries = data["pokemon"]
    manifest = pa.load_manifest()

    ids = [e["id"] for e in entries]
    duplicates = sorted({i for i in ids if ids.count(i) > 1})
    if duplicates:
        errors.append("Numéros en double : %s" % duplicates)
    missing_ids = sorted(set(range(1, pa.EXPECTED_COUNT + 1)) - set(ids))
    if missing_ids:
        errors.append("Numéros manquants : %s" % missing_ids)
    if len(entries) != pa.EXPECTED_COUNT:
        errors.append("%d fiches au lieu de %d" % (len(entries), pa.EXPECTED_COUNT))

    owners = defaultdict(set)
    for e in entries:
        if not e.get("name", "").strip():
            errors.append("Nom vide pour le n° %s" % e["id"])
        for text in [e["name"]] + e.get("aliases", []):
            key = pa.normalize(text)
            if not key:
                errors.append("Alias inutilisable pour %s : %r" % (e["name"], text))
                continue
            owners[key].add(e["id"])
    for key, pokemon_ids in sorted(owners.items()):
        if len(pokemon_ids) > 1 and key not in EXPECTED_COLLISIONS:
            errors.append("« %s » désigne plusieurs Pokémon : %s" % (key, sorted(pokemon_ids)))

    vocabulary = pa.model_vocabulary()
    counts = defaultdict(int)
    unreachable = []
    rows = []
    for e in sorted(entries, key=lambda x: x["id"]):
        image = file_status(pa.image_path(e), manifest, "image")
        audio = file_status(pa.audio_path(e), manifest, "audio")
        counts["image " + image.split(" ")[0]] += 1
        counts["audio " + audio.split(" ")[0]] += 1
        for status, kind in ((image, "image"), (audio, "audio")):
            if status.startswith("invalide") or status == "manquant":
                errors.append("%s %03d %s : %s" % (kind, e["id"], e["name"], status))

        grammar_note = ""
        if vocabulary is not None:
            phrases = [pa.spoken_form(t) for t in [e["name"]] + e.get("aliases", [])]
            usable = [p for p in phrases if p and all(w in vocabulary for w in p.split())]
            if not usable:
                unreachable.append(e)
                oov = sorted({w for p in phrases for w in p.split() if w not in vocabulary})
                grammar_note = "aucune forme reconnaissable (mots inconnus : %s)" % ", ".join(oov)
            else:
                grammar_note = "reconnu via « %s »" % usable[0]
        rows.append((e, image, audio, grammar_note))

    if args.verbose:
        for e, image, audio, note in rows:
            print("%03d %-12s image=%-10s audio=%-10s %s" % (e["id"], e["name"], image, audio, note))
        print()

    print("Fiches : %d" % len(entries))
    print("Images : %d ok, %d provisoires, %d manquantes" % (
        counts["image ok"], counts["image provisoire"], counts["image manquant"]))
    print("Audio  : %d ok, %d provisoires, %d manquants" % (
        counts["audio ok"], counts["audio provisoire"], counts["audio manquant"]))

    if vocabulary is None:
        warnings.append("Modèle Vosk absent : vérification du vocabulaire ignorée (scripts/fetch-vosk-model.sh)")
    elif unreachable:
        warnings.append(
            "%d Pokémon n'ont aucun nom ni alias entièrement connu du modèle : en mode « liste des noms », "
            "ils ne peuvent pas être reconnus. Ajoute un alias en mots français courants, par exemple "
            "« bulle bizarre » pour Bulbizarre." % len(unreachable)
        )
        for e in unreachable:
            note = next(n for row_e, _, _, n in rows if row_e is e)
            warnings.append("  %03d %s : %s" % (e["id"], e["name"], note))

    if args.strict and (counts["image provisoire"] or counts["audio provisoire"]):
        errors.append("Des fichiers provisoires restent à remplacer (--strict)")

    for w in warnings:
        print("ATTENTION " + w)
    for err in errors:
        print("ERREUR " + err)
    print("OK" if not errors else "%d erreur(s)" % len(errors))
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())

# Génération de voix (Piper)

Convertit des fichiers texte en fichiers audio avec [Piper](https://github.com/OHF-Voice/piper1-gpl),
une synthèse vocale neuronale qui tourne entièrement hors ligne. Un effet « robot » optionnel
donne une voix métallique de machine parlante.

- `setup-piper.sh` installe Piper et des voix françaises, sans `sudo`.
- `text-to-voice.py` convertit un ou plusieurs fichiers texte en WAV, OGG (Opus) ou MP3.

Le réseau ne sert qu'à l'installation. La conversion elle-même n'envoie rien.

## Prérequis

- `python3` (3.9 ou plus récent) avec le module `venv` ;
- `ffmpeg`, pour l'effet robot, le rééchantillonnage et les formats autres que WAV.
  Sans lui, seule la sortie WAV brute à la fréquence native de la voix fonctionne.

## Installation

```sh
./setup-piper.sh
# Réseau d'entreprise avec inspection TLS : passer le proxy et le certificat racine (PEM)
./setup-piper.sh --proxy proxy.example:8080 --ca-cert ~/corporate-ca.pem
```

Le script crée un environnement Python dans `~/.local/share/piper/venv`, y installe `piper-tts`
puis télécharge quatre voix françaises dans `~/.local/share/piper/voices` (environ 65 à 75 Mo
chacune). Le relancer ne télécharge que ce qui manque.

- `--voice NOM` (répétable) choisit d'autres voix que celles par défaut ;
- `--list-remote` liste toutes les voix téléchargeables, toutes langues confondues ;
- `--prefix DIR` installe ailleurs ; exporter alors `PIPER_HOME=DIR` avant d'utiliser
  `text-to-voice.py`.

Désinstallation : `rm -rf ~/.local/share/piper`.

## Utilisation

```sh
./text-to-voice.py --list-voices

# message.txt -> message.wav, à côté du fichier texte
./text-to-voice.py message.txt

# Voix d'homme, un peu plus lente, avec l'effet robot, en Opus dans un dossier
./text-to-voice.py -v fr_FR-upmc-medium -s pierre --speed 0.9 --effect robot \
  -f ogg -d sorties/ descriptions/*.txt

# Depuis l'entrée standard
echo "Bonjour." | ./text-to-voice.py -o bonjour.wav -
```

Le texte est lu en UTF-8. Les retours à la ligne et espaces multiples sont fusionnés : un texte
copié depuis une page web ou une API (avec des retours à la ligne au milieu des phrases) est lu
d'un seul trait. Piper découpe ensuite en phrases et l'outil insère un court silence entre elles.

Un fichier de sortie existant n'est jamais écrasé sans `--force`. Un fichier texte vide est
signalé et le traitement continue avec les suivants ; le code de retour vaut alors 1.

### Options

| Option | Effet | Défaut |
| --- | --- | --- |
| `-o FICHIER` | fichier de sortie, pour une seule entrée ; le format suit l'extension | à côté de l'entrée |
| `-d DOSSIER` | dossier de sortie pour plusieurs entrées | à côté de chaque entrée |
| `-f FORMAT` | `wav`, `ogg`, `opus` ou `mp3`, quand le nom est déduit de l'entrée | `wav` |
| `-v VOIX` | nom d'une voix installée, ou chemin vers un modèle `.onnx` | `fr_FR-siwis-medium` |
| `-s LOCUTEUR` | nom ou numéro du locuteur, pour les voix qui en ont plusieurs | premier locuteur |
| `--speed` | débit ; `0.9` = 10 % plus lent | `1.0` |
| `--noise-scale` | variabilité de l'intonation ; plus bas = plus plat, plus robotique | réglage de la voix (0,667) |
| `--sentence-silence` | silence entre deux phrases, en secondes | `0.3` |
| `--lead-silence` | silence avant le premier mot, évite qu'il soit coupé à la lecture | `0.3` |
| `--effect` | `none` ou `robot` | `none` |
| `--robot-mix` | part de voix modulée dans l'effet robot, de 0 à 1 | `0.5` |
| `--robot-frequency` | fréquence de modulation en Hz ; plus haut = plus métallique | `40` |
| `-r` | fréquence d'échantillonnage de sortie (l'Opus est toujours en 48 kHz) | `22050` |
| `--force` | écrase les sorties existantes | non |

Les sorties sont toujours en mono. Le volume est ramené à environ −1 dB de crête sans effet, et
normalisé en sonie (−16 LUFS) avec l'effet robot, qui sinon fait chuter le niveau.

## Voix françaises installées par défaut

| Voix | Locuteurs | Remarques |
| --- | --- | --- |
| `fr_FR-siwis-medium` | une femme | voix claire et posée, bon point de départ |
| `fr_FR-tom-medium` | un homme | modèle en 44,1 kHz, rééchantillonné en sortie |
| `fr_FR-upmc-medium` | `jessica`, `pierre` | deux voix dans un même modèle |
| `fr_FR-gilles-low` | un homme | qualité basse (16 kHz) ; affiche parfois `Missing phoneme from id map` sur les voyelles nasales, sans bloquer la conversion |

Chaque voix a sa propre licence, indiquée dans sa fiche (`MODEL_CARD`) sur
[rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices/tree/main/fr/fr_FR).

## Effet robot

L'effet est appliqué par ffmpeg après la synthèse :

1. une partie de la voix est multipliée par une onde lente (modulation en anneau, l'effet robot
   classique), puis mélangée à la voix d'origine pour rester compréhensible ;
2. un flanger et un écho très court ajoutent le timbre métallique ;
3. un filtre passe-bande (180 Hz à 6,5 kHz) imite un petit haut-parleur.

Pour l'ajuster, écouter quelques variantes :

```sh
./text-to-voice.py --force --effect robot --robot-mix 0.3 -o doux.wav test.txt
./text-to-voice.py --force --effect robot --robot-mix 0.8 --robot-frequency 70 -o dur.wav test.txt
./text-to-voice.py --force --effect robot --noise-scale 0.3 -o monotone.wav test.txt
```

Pour rester compréhensible, mieux vaut un effet modéré (`--robot-mix` de 0,3 à 0,5) et un débit
un peu ralenti (`--speed 0.9`) : au-delà, la voix devient difficile à comprendre.

## Formats et taille

Pour 8 secondes de parole :

| Format | Taille |
| --- | --- |
| WAV 22 kHz 16 bits mono | environ 350 Ko |
| OGG (Opus, 32 kbit/s) | environ 33 Ko |

Pour embarquer beaucoup d'enregistrements dans une application, l'Opus divise la taille par dix
sans perte audible pour de la voix.

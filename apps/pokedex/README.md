# Pokédex vocal (Android, mode kiosque)

Application Android native qui transforme un smartphone dédié en Pokédex pour jeunes enfants :
l'enfant appuie sur un grand bouton, prononce le nom d'un Pokémon de Kanto, voit son illustration
et entend sa description enregistrée. Tout fonctionne hors ligne et le téléphone reste verrouillé
sur l'application.

- Kotlin, Jetpack Compose, Room (SQLite), DataStore, Media3 ExoPlayer, SoundPool
- Reconnaissance vocale embarquée : [Vosk](https://alphacephei.com/vosk/) avec `vosk-model-small-fr-0.22`
- Aucune permission `INTERNET` : l'application ne peut rien envoyer, même via une bibliothèque
- Mode kiosque Android (Lock Task Mode) en tant que *Device Owner*

Pour comprendre le code, le rôle de chaque dossier et la façon d'apporter des corrections sans
connaître Android, lire [ARCHITECTURE.md](ARCHITECTURE.md).

## Arborescence

```
apps/pokedex/
  app/src/main/
    assets/pokemon/pokedex.json      # les 151 fiches : nom, alias, chemins optionnels
    assets/pokemon/images/0001.png   # illustrations (numéro national sur 4 chiffres)
    assets/pokemon/audio/0001.wav    # descriptions enregistrées
    assets/sounds/                   # sons d'interface (écoute, succès, erreur, description absente)
    assets/model-fr/                 # modèle Vosk, téléchargé par script (non versionné)
    java/fr/jo/pokedex/
      data/    Room, import du JSON, dépôt
      domain/  normalisation des noms, phonétique, résolution (PokemonResolver)
      speech/  moteur de reconnaissance (interface + Vosk)
      audio/   lecture des descriptions et sons d'interface
      kiosk/   Device Owner, Lock Task, démarrage, réglages et code parental
      ui/      écran enfant, espace parent, ViewModel et machine à états
  scripts/
    fetch-vosk-model.sh         # télécharge le modèle français
    generate_placeholders.py    # crée les fichiers provisoires manquants + sons d'interface
    check_assets.py             # état des fiches et du vocabulaire reconnaissable
```

## Compilation

L'outillage est installé sans `sudo` dans `~/.local/android` par un script partagé entre les
applications du dépôt :

```sh
# Réseau d'entreprise avec inspection TLS : passer le proxy et le certificat racine (PEM)
../../tools/setup-android-toolchain.sh --proxy proxy.example:8080 --ca-cert ~/corporate-ca.pem
# Réseau classique
../../tools/setup-android-toolchain.sh

. ~/.local/android/env.sh
./scripts/fetch-vosk-model.sh
python3 scripts/generate_placeholders.py   # nécessite Pillow
python3 scripts/check_assets.py

./gradlew testDebugUnitTest assembleDebug  # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                  # APK réduit par R8 (signé avec la clé debug)
```

## Enrichir les fiches petit à petit

Tout part de [`pokedex.json`](app/src/main/assets/pokemon/pokedex.json) :

```json
{ "id": 25, "name": "Pikachu", "aliases": ["pika chu", "pikachou"] }
{ "id": 1, "name": "Bulbizarre", "aliases": ["bulle bizarre"], "image": "pokemon/images/bulbi.webp", "audio": "pokemon/audio/bulbi.ogg" }
```

- **Illustration** : déposer `pokemon/images/NNNN.png` (PNG transparent, environ 512 à 1024 px).
- **Description** : déposer `pokemon/audio/NNNN.wav` (mono, 16 bits, 22 ou 44 kHz ; MP3/OGG
  fonctionnent aussi via le champ `audio`). Écouter chaque enregistrement avant intégration.
- **Alias** : variantes de transcription ou approximations phonétiques, sans toucher au nom officiel.
- Les fichiers réels ne sont jamais écrasés par `generate_placeholders.py`. Ses fichiers provisoires
  sont listés dans `scripts/placeholders-manifest.json`, ce qui permet à `check_assets.py` de les
  distinguer des vrais (`--strict` échoue tant qu'il en reste).
- La base Room est réimportée automatiquement au démarrage dès que le contenu du JSON change
  (empreinte SHA-256) ; le champ `version` sert de repère humain.
- Une fiche sans image affiche son numéro sur une Poké Ball ; sans description, un son neutre est
  joué. L'application ne plante pas sur une ressource absente.

## Reconnaissance vocale

Le micro ne s'ouvre qu'après l'appui sur le bouton, pour 6 secondes au maximum, et se ferme au
premier énoncé, à l'expiration du délai ou dès que l'application passe en arrière-plan.

Deux modes, réglables dans l'espace parent :

- **Liste des noms (par défaut)** : Vosk est restreint aux noms et alias (plus `[unk]`). Une grammaire
  Vosk n'accepte que des mots connus du modèle : beaucoup de noms (« carapuce », « salamèche »…)
  n'y sont pas, d'où des alias en mots français courants (« cara puce », « sala mèche »).
  `check_assets.py` lit le vocabulaire du modèle et signale tout Pokémon qui n'aurait aucune forme
  reconnaissable.
- **Dictée libre** : transcription ouverte, puis rapprochement approximatif. Utile pour comparer.

La résolution ([`PokemonResolver`](app/src/main/java/fr/jo/pokedex/domain/PokemonResolver.kt))
normalise (casse, accents, ponctuation, mots de remplissage comme « c'est »), cherche une
correspondance exacte sur le nom ou un alias, puis une correspondance approchée (Levenshtein sur le
texte et sur une clé phonétique française). Si deux Pokémon sont trop proches, le résultat est
**ambigu** et l'enfant est invité à répéter : aucune fiche n'est choisie au hasard. « Nidoran » seul
est volontairement ambigu (dire « Nidoran fille » ou « Nidoran garçon »).

Réglages à ajuster pendant les essais réels :

- seuils de similarité et d'écart : `PokemonResolver.Config` ;
- durées d'écoute, d'affichage et de retour à l'accueil : `PokedexViewModel.Timing` ;
- les journaux `adb logcat -s PokedexViewModel` affichent chaque transcription et sa résolution.

## Tester l'application

### Sur un émulateur

L'émulateur nécessite KVM (`/dev/kvm`, disponible sous WSL2) et environ 3 Go de mémoire libre.
Sur une machine de 16 Go, ne pas compiler pendant que l'émulateur tourne (`./gradlew --stop` avant
de le lancer) : la combinaison des deux peut faire redémarrer WSL.

Installation (une fois) :

```sh
. ~/.local/android/env.sh
# Ajouter --proxy=http --proxy_host=HOTE --proxy_port=PORT derrière un proxy d'entreprise
sdkmanager --install "emulator" "system-images;android-35;default;x86_64"
echo no | avdmanager create avd -n pokedex -k "system-images;android-35;default;x86_64" -d pixel_5
```

L'image `default` (sans services Google) n'a aucun compte, ce qui permet aussi de tester le mode
géré.

Lancement :

```sh
. ~/.local/android/env.sh
# Avec fenêtre (WSLg sous Windows 11)
$ANDROID_HOME/emulator/emulator -avd pokedex -gpu swiftshader_indirect -memory 2048 &
# Ou sans fenêtre, piloté uniquement par adb
$ANDROID_HOME/emulator/emulator -avd pokedex -no-window -no-audio -gpu swiftshader_indirect -memory 2048 &

adb wait-for-device
adb install -r -g app/build/outputs/apk/debug/app-debug.apk   # -g accorde la permission micro
adb shell am start -n fr.jo.pokedex/.ui.MainActivity
adb logcat -s PokedexViewModel VoskSpeechEngine                # « Recognizer ready » = prêt
```

En mode sans fenêtre :

- capture d'écran : `adb exec-out screencap -p > capture.png` ;
- appui : `adb shell input tap X Y` (coordonnées en pixels de l'écran 1080 × 2340 du Pixel 5, par
  exemple `adb shell input tap 539 2100` pour le grand bouton) ;
- touches système : `adb shell input keyevent 3` (Accueil), `4` (Retour), `187` (Récents).

Micro : avec `-no-audio`, l'écoute aboutit toujours à « Je n'ai rien entendu », ce qui suffit pour
tester les écrans et les enchaînements. Pour parler à l'émulateur, le lancer sans `-no-audio` puis
activer l'entrée audio de l'hôte (`adb emu avd hostmicon`, ou *Extended controls > Microphone* dans
la fenêtre). Sous WSL, cela dépend de la prise en charge audio de WSLg et n'est pas garanti.

Mode géré sur l'émulateur :

```sh
adb shell dpm set-device-owner fr.jo.pokedex/.kiosk.PokedexAdminReceiver
adb reboot          # l'application doit revenir seule, verrouillée
adb shell dumpsys activity activities | grep mLockTaskModeState   # LOCKED attendu
```

Pour revenir à un émulateur vierge : « Retirer le mode administrateur » dans l'espace parent, ou
relancer l'émulateur avec `-wipe-data`. Arrêt : `adb emu kill`.

### Sur un téléphone personnel, sans mode géré

L'application fonctionne sans être propriétaire de l'appareil : aucune réinitialisation n'est
nécessaire et le téléphone reste entièrement utilisable. Ne **jamais** lancer
`dpm set-device-owner` sur un téléphone personnel (Android le refuse de toute façon dès qu'un compte
est configuré).

1. Construire l'APK : `./gradlew assembleDebug` (ou `assembleRelease`, plus léger).
2. L'installer, au choix :
   - par `adb` (débogage USB activé ; sous WSL, via usbipd ou `adb pair` / `adb connect` en Wi-Fi) :
     `adb install -r app/build/outputs/apk/debug/app-debug.apk` ;
   - en copiant l'APK sur le téléphone et en l'ouvrant (autoriser l'installation d'applications de
     sources inconnues pour l'explorateur de fichiers utilisé).
3. Au premier lancement, accepter la permission micro. Le chargement du modèle vocal prend quelques
   secondes la première fois (copie d'environ 66 Mo dans le stockage de l'application).
4. L'application demande ensuite à épingler l'écran :
   - **Non merci** : le téléphone reste normal, la touche Accueil permet de quitter l'application ;
   - **Accepter** : permet d'essayer le verrouillage. Pour en sortir, maintenir Retour + Aperçu
     (navigation à 3 boutons) ou balayer vers le haut en maintenant (navigation par gestes).

   La demande réapparaît à chaque retour dans l'application, puisqu'elle tente de se verrouiller à
   chaque reprise.
5. La touche Retour est volontairement sans effet ; utiliser Accueil pour quitter.

L'espace parent fonctionne comme sur le téléphone dédié, sans le bouton « Retirer le mode
administrateur ». Selon la version d'Android, l'application peut tenter de se relancer seule après
un redémarrage du téléphone ; Android 10 et suivants le bloquent généralement.

Désinstallation : appui long sur l'icône puis « Désinstaller », ou `adb uninstall fr.jo.pokedex`.
L'application n'étant pas administrateur de l'appareil, rien ne s'y oppose et aucune donnée ne
reste sur le téléphone.

## Installation et mode kiosque

### Provisionnement Device Owner (une fois, sur le téléphone dédié)

1. Réinitialiser le téléphone, passer l'assistant **sans ajouter de compte Google**.
2. Activer les options développeur et le débogage USB.
3. Installer et déclarer l'application propriétaire de l'appareil :

   ```sh
   adb install app/build/outputs/apk/release/app-release.apk
   adb shell dpm set-device-owner fr.jo.pokedex/.kiosk.PokedexAdminReceiver
   ```

4. Lancer l'application une fois, puis redémarrer le téléphone pour vérifier le démarrage automatique.

En mode géré, l'application devient l'écran d'accueil (redémarre seule après un boot), passe en
Lock Task Mode, désactive la barre d'état et l'écran de verrouillage (si aucun code n'est défini),
et s'accorde la permission micro. Le menu marche/arrêt reste disponible pour redémarrer ou éteindre.

Sans Device Owner, l'application utilise l'épinglage d'écran standard (Android demande confirmation
et un utilisateur peut en sortir) ; l'espace parent l'indique. Voir « Sur un téléphone personnel,
sans mode géré » pour les essais dans ce mode.

Mise à jour : `adb install -r app-release.apk` (même clé de signature).

Sous WSL, l'USB passe par [usbipd-win](https://github.com/dorssel/usbipd-win), ou utiliser
`adb pair` / `adb connect` en Wi-Fi.

### Espace parent

Geste caché, invisible pour l'enfant : **appui long de 3 s dans le coin supérieur gauche**, puis
**3 tapotements dans le coin supérieur droit** dans les 6 secondes, puis le code parental.

- Code par défaut : **1234**, à changer au premier accès (5 erreurs bloquent la saisie 60 s).
- Diagnostic : mode kiosque, fiches et ressources manquantes, état du modèle vocal, micro.
- Déverrouiller temporairement, ouvrir les paramètres Android, changer de mode de reconnaissance,
  recharger les fiches, changer le code.
- **Retirer le mode administrateur** : rend le téléphone normal (l'application peut alors être
  désinstallée).
- Le kiosque reprend automatiquement à la fermeture de l'espace parent, qui se ferme seul après
  3 minutes d'inactivité.

### Récupération en cas de problème

- Le menu marche/arrêt permet toujours de redémarrer.
- `adb` reste disponible tant que le débogage USB est actif (journaux, mise à jour).
- Code parental perdu ou application inutilisable : réinitialisation d'usine depuis le mode
  recovery (combinaison de touches propre au modèle), puis nouveau provisionnement.

## Recette

| Critère | Vérification |
| --- | --- |
| 151 fiches présentes, chacune avec image et audio | `check_assets.py --strict`, test `PokedexDataTest` |
| Reconnaissance sans réseau | mode avion ; aucune permission `INTERNET` dans le manifeste fusionné |
| Un appui = une seule écoute | test `button triggers a single listening session` |
| Bonne fiche affichée, jamais de fiche aléatoire | `PokemonResolverTest`, `unknown name shows a failure and never a card` |
| Description rejouable | bouton RÉÉCOUTER ; test `replay plays the description again` |
| Plusieurs dizaines de recherches sans plantage | test `dozens of searches in a row` + essai manuel |
| Micro jamais actif en permanence | indicateur micro d'Android visible uniquement pendant l'écoute |
| Application au premier plan, enfant bloqué | boutons Accueil/Récents inactifs, retour neutralisé |
| Démarrage et reprise | redémarrage du téléphone, appel entrant, écran éteint puis rallumé |
| Méthode de sortie adulte | espace parent, menu marche/arrêt |
| Aucune donnée audio envoyée | pas de permission réseau, audio traité en mémoire uniquement |

Restent à valider sur le terrain : taux de reconnaissance avec des enfants et du bruit domestique,
comportement au redémarrage sur le modèle de téléphone retenu.

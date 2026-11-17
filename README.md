# Spotify Fast Android

Client Android natif Jetpack Compose haute performance (`< 300 ms` au démarrage à froid) conçu avec une architecture **Offline-First** (Room + cache disque Coil 250 Mo), le pont local **Spotify App Remote** pré-chauffé post-Frame 1, **Spotify Connect**, les **paroles synchronisées LRCLIB** et le support des **Podcasts & Livres audio**.

## Prérequis & Configuration Spotify Developer Dashboard

1. Créer une application dans le [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Exécuter la tâche Gradle suivante pour obtenir les informations exactes à enregistrer :
   ```bash
   JAVA_HOME="$HOME/.jdks/temurin-17" ./gradlew printSpotifyDashboardSetup
   ```
3. Ajouter dans votre application Spotify Developer :
   - **Redirect URI** : `http://127.0.0.1:8888/callback`
   - **Android Package Name** : `com.tristan.spotifyfast`
   - **SHA-1 Fingerprint** : celui affiché par `printSpotifyDashboardSetup` (ou directement dans l'écran de connexion de l'application).

## Outillage CLI-Hub & Artcraft

- Provisioning JDK 17 + Android SDK (`platforms;android-35`, `build-tools;35.0.0`) :
  ```bash
  bash tools/setup_android_sdk.sh
  ```
- Génération des maquettes FigCraft, icônes SVG/Kotlin VectorCraft et animation égaliseur AniCraft :
  ```bash
  bash tools/generate_artcraft_assets.sh
  ```

## Commandes de Build & Tests

```bash
JAVA_HOME="$HOME/.jdks/temurin-17" ./gradlew testDebugUnitTest assembleDebug assembleRelease
```

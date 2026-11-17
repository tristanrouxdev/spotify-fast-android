#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="/home/tristanroux/Projets/spotify-fast-android"
TAG="${1:-v1.1.0}"
REPO_SLUG="${2:-}"
GH_BIN="${GH_BIN:-$HOME/.local/bin/gh}"

cd "$PROJECT_DIR"

if [ ! -x "$GH_BIN" ]; then
  echo "Erreur: gh CLI introuvable dans $GH_BIN" >&2
  exit 1
fi

if ! "$GH_BIN" auth status >/dev/null 2>&1; then
  echo "Authentification GitHub requise. Lancez d'abord :"
  echo "  $GH_BIN auth login -w"
  exit 1
fi

echo "=== [1/4] Build & tests avant publication ($TAG) ==="
JAVA_HOME="$HOME/.jdks/temurin-17" ./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintVitalRelease --console=plain

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
  echo "APK Release introuvable : $APK_PATH" >&2
  exit 1
fi

echo "=== [2/4] Création / Push du dépôt GitHub ==="
if ! git remote get-url origin >/dev/null 2>&1; then
  TARGET_NAME="${REPO_SLUG:-spotify-fast-android}"
  "$GH_BIN" repo create "$TARGET_NAME" --public --source="$PROJECT_DIR" --remote=origin --push
else
  git push -u origin HEAD
fi

echo "=== [3/4] Tag & Release GitHub ($TAG) ==="
if ! git rev-parse "$TAG" >/dev/null 2>&1; then
  git tag -a "$TAG" -m "Spotify Fast Android $TAG"
fi
git push origin "$TAG"

if "$GH_BIN" release view "$TAG" >/dev/null 2>&1; then
  "$GH_BIN" release upload "$TAG" "$APK_PATH" --clobber
else
  "$GH_BIN" release create "$TAG" "$APK_PATH" \
    --title "Spotify Fast Android $TAG" \
    --notes "Build Release R8 optimisé avec mise à jour automatique intégrée."
fi

echo "=== [4/4] Terminé ==="
"$GH_BIN" release view "$TAG" --json url,tagName -q '.url'

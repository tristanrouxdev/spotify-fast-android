#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="/home/tristanroux/Projets/spotify-fast-android"
JDK17_DIR="$HOME/.jdks/temurin-17"
ANDROID_SDK_DIR="$HOME/Android/Sdk"

echo "=== [1/4] Provisioning JDK 17 portable ($JDK17_DIR) ==="
if [ ! -x "$JDK17_DIR/bin/javac" ]; then
  mkdir -p "$HOME/.jdks"
  TMP_JDK_TAR="$(mktemp /tmp/temurin17.XXXXXX.tar.gz)"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" -o "$TMP_JDK_TAR"
  EXTRACT_DIR="$(mktemp -d /tmp/temurin17_extract.XXXXXX)"
  tar -xzf "$TMP_JDK_TAR" -C "$EXTRACT_DIR"
  TOP_DIR="$(find "$EXTRACT_DIR" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
  rm -rf "$JDK17_DIR"
  mv "$TOP_DIR" "$JDK17_DIR"
  rm -rf "$TMP_JDK_TAR" "$EXTRACT_DIR"
fi
export JAVA_HOME="$JDK17_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/javac" -version

echo "=== [2/4] Installing android-cli via cli-hub ==="
if command -v cli-hub >/dev/null 2>&1; then
  cli-hub install android-cli || true
elif [ -x "$HOME/.local/bin/cli-hub" ]; then
  "$HOME/.local/bin/cli-hub" install android-cli || true
fi

if ! command -v android >/dev/null 2>&1 && [ ! -x "$HOME/.local/bin/android" ]; then
  curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash || true
fi

echo "=== [3/4] Installing Android SDK (API 35, Build-Tools 35.0.0, Platform-Tools) ==="
mkdir -p "$ANDROID_SDK_DIR/cmdline-tools"
if [ ! -x "$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  TMP_CMDLINE="$(mktemp /tmp/cmdline-tools.XXXXXX.zip)"
  # Official Google Android commandlinetools Linux (latest r11076708 / r12774119)
  curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o "$TMP_CMDLINE"
  TMP_UNZIP="$(mktemp -d /tmp/cmdline_unzip.XXXXXX)"
  unzip -q "$TMP_CMDLINE" -d "$TMP_UNZIP"
  rm -rf "$ANDROID_SDK_DIR/cmdline-tools/latest"
  mv "$TMP_UNZIP/cmdline-tools" "$ANDROID_SDK_DIR/cmdline-tools/latest"
  rm -rf "$TMP_CMDLINE" "$TMP_UNZIP"
fi

export ANDROID_HOME="$ANDROID_SDK_DIR"
export ANDROID_SDK_ROOT="$ANDROID_SDK_DIR"
export PATH="$ANDROID_SDK_DIR/cmdline-tools/latest/bin:$ANDROID_SDK_DIR/platform-tools:$PATH"

yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager --install \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"

echo "=== [4/4] Generating local.properties ==="
cat > "$PROJECT_DIR/local.properties" <<EOF
sdk.dir=$ANDROID_SDK_DIR
EOF

echo "SDK setup complete at $ANDROID_SDK_DIR with JDK $("$JAVA_HOME/bin/javac" -version 2>&1)"

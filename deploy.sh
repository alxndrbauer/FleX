#!/bin/bash
set -e

# Usage: ./deploy.sh [app|wear]
# No argument = deploy both
TARGET="${1:-both}"

if [[ "$TARGET" != "app" && "$TARGET" != "wear" && "$TARGET" != "both" ]]; then
  echo "Usage: ./deploy.sh [app|wear]"
  exit 1
fi

# Load environment variables
if [ ! -f .env ]; then
  echo "Error: .env file not found. Copy .env.example to .env and fill in your values."
  exit 1
fi
source .env

GRADLE_SIGN=(
  -Pandroid.injected.signing.store.file="$KEYSTORE_FILE"
  -Pandroid.injected.signing.store.password="$KEYSTORE_PASSWORD"
  -Pandroid.injected.signing.key.alias="$KEY_ALIAS"
  -Pandroid.injected.signing.key.password="$KEY_PASSWORD"
)

GRADLE="ANDROID_HOME=/Users/abauer/Library/Android/sdk JAVA_TOOL_OPTIONS=-Djava.awt.headless=true ./gradlew"

# Build
if [[ "$TARGET" == "app" || "$TARGET" == "both" ]]; then
  echo "Building :app release..."
  ANDROID_HOME=/Users/abauer/Library/Android/sdk \
  JAVA_TOOL_OPTIONS=-Djava.awt.headless=true \
  ./gradlew :app:assembleRelease "${GRADLE_SIGN[@]}"
fi

if [[ "$TARGET" == "wear" || "$TARGET" == "both" ]]; then
  echo "Building :wear release..."
  ANDROID_HOME=/Users/abauer/Library/Android/sdk \
  JAVA_TOOL_OPTIONS=-Djava.awt.headless=true \
  ./gradlew :wear:assembleRelease "${GRADLE_SIGN[@]}"
fi

# Install
if [[ "$TARGET" == "wear" || "$TARGET" == "both" ]]; then
  echo "Connecting to watch at $WATCH_DEVICE..."
  adb connect "$WATCH_DEVICE"
  echo "Installing wear APK..."
  adb -s "$WATCH_DEVICE" uninstall com.flex 2>/dev/null || true
  adb -s "$WATCH_DEVICE" install wear/build/outputs/apk/release/wear-release.apk
fi

if [[ "$TARGET" == "app" || "$TARGET" == "both" ]]; then
  echo "Installing phone APK..."
  adb -s "$PHONE_DEVICE" install -r app/build/outputs/apk/release/app-release.apk
fi

echo "Done!"

#!/bin/bash
set -e

# Load environment variables
if [ ! -f .env ]; then
  echo "Error: .env file not found. Copy .env.example to .env and fill in your values."
  exit 1
fi
source .env

# Build phone app (release)
echo "Building :app release..."
ANDROID_HOME=/Users/abauer/Library/Android/sdk \
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true \
./gradlew :app:assembleRelease \
  -Pandroid.injected.signing.store.file="$KEYSTORE_FILE" \
  -Pandroid.injected.signing.store.password="$KEYSTORE_PASSWORD" \
  -Pandroid.injected.signing.key.alias="$KEY_ALIAS" \
  -Pandroid.injected.signing.key.password="$KEY_PASSWORD"

# Build wear app (release)
echo "Building :wear release..."
ANDROID_HOME=/Users/abauer/Library/Android/sdk \
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true \
./gradlew :wear:assembleRelease \
  -Pandroid.injected.signing.store.file="$KEYSTORE_FILE" \
  -Pandroid.injected.signing.store.password="$KEYSTORE_PASSWORD" \
  -Pandroid.injected.signing.key.alias="$KEY_ALIAS" \
  -Pandroid.injected.signing.key.password="$KEY_PASSWORD"

# Connect to watch
echo "Connecting to watch at $WATCH_IP:$WATCH_PORT..."
adb connect "$WATCH_IP:$WATCH_PORT"

# Install watch app
echo "Installing wear APK..."
adb -s "$WATCH_IP:$WATCH_PORT" uninstall com.flex 2>/dev/null || true
adb -s "$WATCH_IP:$WATCH_PORT" install wear/build/outputs/apk/release/wear-release.apk

# Install phone app (connected via USB or ADB)
echo "Installing phone APK..."
adb install -r app/build/outputs/apk/release/app-release.apk

echo "Done!"

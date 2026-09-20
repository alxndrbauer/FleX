#!/bin/bash
set -e

# Usage: ./deploy.sh
# Builds and installs phone app release APK

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

# Validate required environment variables
if [ -z "$ANDROID_HOME" ]; then
  echo "Error: ANDROID_HOME not set in .env"
  exit 1
fi
if [ -z "$JAVA_TOOL_OPTIONS" ]; then
  echo "Error: JAVA_TOOL_OPTIONS not set in .env"
  exit 1
fi

# Build
echo "Building :app release..."
ANDROID_HOME="$ANDROID_HOME" \
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS" \
./gradlew :app:assembleRelease "${GRADLE_SIGN[@]}"

# Install
echo "Installing phone APK..."
if ! adb -s "$PHONE_DEVICE" install -r app/build/outputs/apk/release/app-release.apk; then
  echo "⚠️  Phone installation failed (device: $PHONE_DEVICE)"
  exit 1
fi

echo "✅ Done!"

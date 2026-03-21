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
if [[ "$TARGET" == "app" || "$TARGET" == "both" ]]; then
  echo "Building :app release..."
  ANDROID_HOME="$ANDROID_HOME" \
  JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS" \
  ./gradlew :app:assembleRelease "${GRADLE_SIGN[@]}"
fi

if [[ "$TARGET" == "wear" || "$TARGET" == "both" ]]; then
  echo "Building :wear release..."
  ANDROID_HOME="$ANDROID_HOME" \
  JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS" \
  ./gradlew :wear:assembleRelease "${GRADLE_SIGN[@]}"
fi

# Install
INSTALL_FAILED=0

if [[ "$TARGET" == "wear" || "$TARGET" == "both" ]]; then
  echo "Connecting to watch at $WATCH_DEVICE..."
  if adb connect "$WATCH_DEVICE" 2>&1 | grep -q "connected"; then
    echo "Installing wear APK..."
    #adb -s "$WATCH_DEVICE" uninstall com.flex 2>/dev/null || true
    if ! adb -s "$WATCH_DEVICE" install wear/build/outputs/apk/release/wear-release.apk; then
      echo "⚠️  Wear installation failed (device: $WATCH_DEVICE)"
      INSTALL_FAILED=1
    fi
  else
    echo "⚠️  Watch not reachable at $WATCH_DEVICE — skipping wear install"
    INSTALL_FAILED=1
  fi
fi

if [[ "$TARGET" == "app" || "$TARGET" == "both" ]]; then
  echo "Installing phone APK..."
  if ! adb -s "$PHONE_DEVICE" install -r app/build/outputs/apk/release/app-release.apk; then
    echo "⚠️  Phone installation failed (device: $PHONE_DEVICE)"
    INSTALL_FAILED=1
  fi
fi

if [[ $INSTALL_FAILED -eq 0 ]]; then
  echo "✅ Done!"
else
  echo "⚠️  Done with skipped installs. Check messages above."
  exit 1
fi

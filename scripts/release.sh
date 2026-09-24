#!/usr/bin/env bash
# Builds every release artifact into dist/:
#   AudioBridge-<v>-macos.dmg, AudioBridge-<v>-macos.pkg, AudioBridge-<v>-android.apk, SHA256SUMS
#
# Needs the macOS signing identity (macos/make-signing-identity.sh) and the
# Android release key in ~/.gradle/gradle.properties (see docs/BUILDING.md).
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="$(tr -d '[:space:]' < VERSION)"
rm -rf dist && mkdir -p dist

echo "== macOS"
macos/package.sh
macos/AudioBridge.app/Contents/MacOS/AudioBridge --selftest >/dev/null
echo "   self-test passed"

echo "== Android"
if ! grep -qs AUDIOBRIDGE_KEYSTORE "$HOME/.gradle/gradle.properties" && [ -z "${ORG_GRADLE_PROJECT_AUDIOBRIDGE_KEYSTORE:-}" ]; then
  echo "   release key not configured — refusing to publish a debug-signed APK" >&2
  exit 1
fi
(cd android && ./gradlew --quiet :shared:jvmTest :androidApp:assembleRelease)
cp android/androidApp/build/outputs/apk/release/androidApp-release.apk "dist/AudioBridge-$VERSION-android.apk"

echo "== Checksums"
(cd dist && shasum -a 256 AudioBridge-* > SHA256SUMS && cat SHA256SUMS)

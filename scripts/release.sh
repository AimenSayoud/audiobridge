#!/usr/bin/env bash
# Builds every release artifact into dist/:
#   Tethertone-<v>-macos.dmg, Tethertone-<v>-macos.pkg, Tethertone-<v>-android.apk, SHA256SUMS
#
# Needs the macOS signing identity (macos/make-signing-identity.sh) and the
# Android release key in ~/.gradle/gradle.properties (see docs/BUILDING.md).
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="$(tr -d '[:space:]' < VERSION)"
rm -rf dist && mkdir -p dist

echo "== macOS"
macos/package.sh
macos/Tethertone.app/Contents/MacOS/Tethertone --selftest >/dev/null
echo "   self-test passed"

echo "== Android"
if ! grep -qs TETHERTONE_KEYSTORE "$HOME/.gradle/gradle.properties" && [ -z "${ORG_GRADLE_PROJECT_TETHERTONE_KEYSTORE:-}" ]; then
  echo "   release key not configured — refusing to publish a debug-signed APK" >&2
  exit 1
fi
(cd android && ./gradlew --quiet :shared:jvmTest :androidApp:assembleRelease)
cp android/androidApp/build/outputs/apk/release/androidApp-release.apk "dist/Tethertone-$VERSION-android.apk"

echo "== Checksums"
(cd dist && shasum -a 256 Tethertone-* > SHA256SUMS && cat SHA256SUMS)

#!/usr/bin/env bash
# Builds the macOS release artifacts into ../dist:
#
#   AudioBridge-<version>-macos.dmg   drag-to-Applications disk image
#   AudioBridge-<version>-macos.pkg   double-click installer
#
# Both contain the same universal (arm64 + x86_64) app.
set -euo pipefail
cd "$(dirname "$0")"

IDENTIFIER="dev.audiobridge.mac"
VERSION="$(tr -d '[:space:]' < ../VERSION)"
DIST="../dist"
DMG="$DIST/AudioBridge-$VERSION-macos.dmg"
PKG="$DIST/AudioBridge-$VERSION-macos.pkg"

./build.sh --universal
mkdir -p "$DIST"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# --- disk image -------------------------------------------------------------
echo "[dmg] building"
mkdir -p "$WORK/dmg"
cp -R AudioBridge.app "$WORK/dmg/AudioBridge.app"
ln -s /Applications "$WORK/dmg/Applications"
rm -f "$DMG"
hdiutil create -quiet -volname "AudioBridge $VERSION" -srcfolder "$WORK/dmg" \
  -fs HFS+ -format UDZO -ov "$DMG"

# --- installer package ------------------------------------------------------
echo "[pkg] building component"
mkdir -p "$WORK/root" "$WORK/pkgs"
cp -R AudioBridge.app "$WORK/root/AudioBridge.app"
pkgbuild --quiet \
  --root "$WORK/root" \
  --identifier "$IDENTIFIER" \
  --version "$VERSION" \
  --install-location /Applications \
  --scripts installer/scripts \
  "$WORK/pkgs/component.pkg"

echo "[pkg] building product archive"
sed "s/__VERSION__/$VERSION/" installer/distribution.xml > "$WORK/distribution.xml"
productbuild --quiet \
  --distribution "$WORK/distribution.xml" \
  --package-path "$WORK/pkgs" \
  --resources installer/resources \
  "$PKG"

# Signing an installer needs an Apple "Developer ID Installer" certificate.
if [ -n "${INSTALLER_IDENTITY:-}" ]; then
  echo "[pkg] signing with $INSTALLER_IDENTITY"
  productsign --sign "$INSTALLER_IDENTITY" "$PKG" "$PKG.signed"
  mv "$PKG.signed" "$PKG"
fi

echo "[done]"
ls -lh "$DMG" "$PKG" | awk '{print "  " $5 "\t" $NF}'

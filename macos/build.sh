#!/usr/bin/env bash
# Builds AudioBridge.app with the Command Line Tools only — Xcode is not needed.
#
#   ./build.sh                 release build for this Mac's architecture
#   ./build.sh --universal     arm64 + x86_64 in one binary (what releases ship)
#   ./build.sh --install       also copy the app into /Applications
#   ./build.sh --debug         debug configuration
#
# Flags combine: ./build.sh --universal --install
set -euo pipefail
cd "$(dirname "$0")"

CONFIG="release"
UNIVERSAL=0
INSTALL=0
for arg in "$@"; do
  case "$arg" in
    --universal) UNIVERSAL=1 ;;
    --install) INSTALL=1 ;;
    --debug) CONFIG="debug" ;;
    release) CONFIG="release" ;;   # accepted for compatibility with older docs
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

APP="AudioBridge.app"
BUNDLE_ID="dev.audiobridge.mac"
VERSION="$(tr -d '[:space:]' < ../VERSION)"
BINARY_NAME="AudioBridgeMac"

if [ "$UNIVERSAL" = 1 ]; then
  BINS=()
  for arch in arm64 x86_64; do
    echo "[build] swift build -c $CONFIG ($arch)"
    swift build -c "$CONFIG" --triple "$arch-apple-macosx14.0"
    BINS+=("$(swift build -c "$CONFIG" --triple "$arch-apple-macosx14.0" --show-bin-path)/$BINARY_NAME")
  done
  BIN="$(mktemp -d)/$BINARY_NAME"
  lipo -create "${BINS[@]}" -output "$BIN"
else
  echo "[build] swift build -c $CONFIG"
  swift build -c "$CONFIG"
  BIN="$(swift build -c "$CONFIG" --show-bin-path)/$BINARY_NAME"
fi

echo "[bundle] assembling $APP ($(lipo -archs "$BIN"))"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN" "$APP/Contents/MacOS/AudioBridge"
cp AudioBridge.icns "$APP/Contents/Resources/"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleExecutable</key><string>AudioBridge</string>
  <key>CFBundleIdentifier</key><string>$BUNDLE_ID</string>
  <key>CFBundleName</key><string>AudioBridge</string>
  <key>CFBundleDisplayName</key><string>AudioBridge</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>$VERSION</string>
  <key>CFBundleVersion</key><string>$VERSION</string>
  <key>CFBundleIconFile</key><string>AudioBridge</string>
  <key>LSMinimumSystemVersion</key><string>14.0</string>
  <key>LSApplicationCategoryType</key><string>public.app-category.utilities</string>
  <key>NSHumanReadableCopyright</key><string>MIT License · AudioBridge contributors</string>
  <!-- Deliberately NOT LSUIElement: the app has a real window and a Dock
       icon. Menu-bar-only is a runtime activation policy the user can toggle. -->
  <!-- BlackHole is an input device, so capturing it needs the microphone
       permission. Without this key the app is killed on first capture. -->
  <key>NSMicrophoneUsageDescription</key>
  <string>AudioBridge reads the audio your Mac is playing so it can stream it to your phone.</string>
</dict></plist>
PLIST

plutil -lint "$APP/Contents/Info.plist" >/dev/null

# macOS keys Screen Recording and Microphone permission to the code signature,
# and an ad-hoc signature changes with every build — so every update would
# silently revoke them. A stable self-signed identity means approving once.
# Create one with:  ./make-signing-identity.sh
#
# The entitlement matters: under the hardened runtime, microphone access is
# refused without com.apple.security.device.audio-input, whatever the toggle in
# System Settings says.
IDENTITY="${SIGNING_IDENTITY:-AudioBridge Local Signing}"
if security find-identity -v -p codesigning 2>/dev/null | grep -q "$IDENTITY"; then
  echo "[sign] using stable identity: $IDENTITY"
  codesign --force --sign "$IDENTITY" --identifier "$BUNDLE_ID" --options runtime \
    --entitlements AudioBridge.entitlements --timestamp=none "$APP"
else
  echo "[sign] no stable identity found, signing ad-hoc"
  echo "[sign] permissions will reset on every rebuild — run ./make-signing-identity.sh once"
  codesign --force --sign - --identifier "$BUNDLE_ID" --entitlements AudioBridge.entitlements "$APP"
fi
codesign --verify --deep --strict "$APP" && echo "[sign] signature verified"

if [ "$INSTALL" = 1 ]; then
  # Quit a running copy first so the bundle is not replaced underneath it.
  osascript -e 'quit app "AudioBridge"' >/dev/null 2>&1 || true
  echo "[install] copying to /Applications"
  rm -rf "/Applications/$APP"
  cp -R "$APP" "/Applications/$APP"
  echo "[done] /Applications/$APP"
else
  echo "[done] $(pwd)/$APP"
fi

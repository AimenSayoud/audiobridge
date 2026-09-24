#!/usr/bin/env bash
# Creates a stable self-signed code-signing identity in your login keychain.
#
# Why: macOS ties Screen Recording and Microphone permission to an app's code
# signature. An ad-hoc signature changes on every build, so every update revoked
# the permissions and the app came back silently broken. A fixed identity means
# you approve once.
set -euo pipefail
NAME="${1:-Tethertone Local Signing}"

if security find-identity -v -p codesigning 2>/dev/null | grep -q "$NAME"; then
  echo "Identity '$NAME' already exists."
  exit 0
fi

WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
PW="tethertone"

cat > "$WORK/ext.cnf" <<CNF
[req]
distinguished_name = dn
x509_extensions = v3
prompt = no
[dn]
CN = $NAME
[v3]
basicConstraints = critical,CA:false
keyUsage = critical,digitalSignature
extendedKeyUsage = critical,codeSigning
CNF

# System LibreSSL on purpose: OpenSSL 3 writes a PKCS#12 whose MAC the macOS
# Security framework refuses to verify.
/usr/bin/openssl req -new -newkey rsa:2048 -x509 -days 3650 -nodes \
  -config "$WORK/ext.cnf" -keyout "$WORK/key.pem" -out "$WORK/cert.pem" 2>/dev/null
/usr/bin/openssl pkcs12 -export -out "$WORK/id.p12" -inkey "$WORK/key.pem" \
  -in "$WORK/cert.pem" -passout "pass:$PW" -name "$NAME" 2>/dev/null

security import "$WORK/id.p12" -k "$HOME/Library/Keychains/login.keychain-db" \
  -P "$PW" -T /usr/bin/codesign -A
security add-trusted-cert -r trustRoot -p codeSign \
  -k "$HOME/Library/Keychains/login.keychain-db" "$WORK/cert.pem" 2>/dev/null || true

echo
security find-identity -v -p codesigning | grep "$NAME" || {
  echo "Identity was imported but is not valid for code signing."; exit 1; }
echo "Done. ./build.sh now signs with it."

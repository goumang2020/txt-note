#!/usr/bin/env bash
set -euo pipefail
txtnote_root=$(cd "$(dirname "$0")/.." && pwd)
source "$txtnote_root/scripts/java-env.sh"
apk=${1:?Usage: verify-release-apk.sh APK}
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [[ -z "$sdk" ]]; then
  sdk=$(sed -n 's/^sdk.dir=//p' "$txtnote_root/local.properties" | head -1)
fi
build_tools="$sdk/build-tools/35.0.0"
"$build_tools/apksigner" verify --verbose --print-certs "$apk"
expected=$(openssl x509 -in "$txtnote_root/androidApp/release-cert.pem" -outform DER | shasum -a 256 | awk '{print $1}')
actual=$("$build_tools/apksigner" verify --print-certs "$apk" | awk -F ': ' '/Signer #1 certificate SHA-256 digest:/ {print $2}')
[[ "$actual" == "$expected" ]] || { echo 'Release signing certificate mismatch'; exit 1; }
badging=$("$build_tools/aapt" dump badging "$apk")
[[ "$badging" != *'application-debuggable'* ]] || { echo 'APK must not be debuggable'; exit 1; }
echo 'Verified fixed release certificate and non-debuggable APK.'

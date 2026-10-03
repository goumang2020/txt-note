#!/usr/bin/env bash
set -euo pipefail
txtnote_root=$(cd "$(dirname "$0")/.." && pwd)
source "$txtnote_root/scripts/java-env.sh"
cd "$txtnote_root"
# Build the simulator bundle even when no simulator runtime has been installed.
exec xcodebuild -project iosApp/iosApp.xcodeproj -target txtNote -configuration Debug \
  -sdk iphonesimulator -arch arm64 CODE_SIGNING_ALLOWED=NO "$@"

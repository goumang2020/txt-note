#!/usr/bin/env bash
set -euo pipefail
txtnote_root=$(cd "$(dirname "$0")/.." && pwd)
source "$txtnote_root/scripts/java-env.sh"
cd "$txtnote_root"
exec ./gradlew :androidApp:assembleDebug "$@"

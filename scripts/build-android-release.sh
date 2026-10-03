#!/usr/bin/env bash
set -euo pipefail
txtnote_root=$(cd "$(dirname "$0")/.." && pwd)
source "$txtnote_root/scripts/java-env.sh"
cd "$txtnote_root"
# Credentials stay outside the repository; CI supplies the same variables via Actions Secrets.
exec python3 - "$@" <<'PY'
import json, os, sys
from pathlib import Path
if not os.environ.get('ANDROID_SIGNING_KEYSTORE'):
    folder = Path.home() / '.config/txtnote/signing'
    config = folder / 'signing.json'
    if not config.exists():
        sys.exit('Configure ANDROID_SIGNING_* variables or ~/.config/txtnote/signing/signing.json first.')
    data = json.loads(config.read_text())
    os.environ.update(ANDROID_SIGNING_KEYSTORE=str(folder / 'release.p12'),
                      ANDROID_SIGNING_STORE_PASSWORD=data['storePassword'],
                      ANDROID_SIGNING_KEY_ALIAS=data['keyAlias'],
                      ANDROID_SIGNING_KEY_PASSWORD=data.get('keyPassword', data['storePassword']))
os.execv('./gradlew', ['./gradlew', ':androidApp:assembleRelease', *sys.argv[1:]])
PY

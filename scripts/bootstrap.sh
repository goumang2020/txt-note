#!/usr/bin/env bash
set -euo pipefail
txtnote_root=$(cd "$(dirname "$0")/.." && pwd)
txtnote_cache="${XDG_CACHE_HOME:-$HOME/.cache}/txtnote-tools"
mkdir -p "$txtnote_cache"
if ! (source "$txtnote_root/scripts/java-env.sh") 2>/dev/null; then
  case "$(uname -s)" in Darwin) txtnote_os=mac ;; Linux) txtnote_os=linux ;; *) echo 'Install JDK 17+ and set JAVA_HOME on Windows.'; exit 1 ;; esac
  case "$(uname -m)" in arm64|aarch64) txtnote_arch=aarch64 ;; x86_64) txtnote_arch=x64 ;; *) exit 1 ;; esac
  echo 'Downloading a local Temurin JDK 21…'
  curl -fL --retry 3 "https://api.adoptium.net/v3/binary/latest/21/ga/$txtnote_os/$txtnote_arch/jdk/hotspot/normal/eclipse" -o "$txtnote_cache/jdk21.tar.gz"
  tar -xzf "$txtnote_cache/jdk21.tar.gz" -C "$txtnote_cache"
fi
source "$txtnote_root/scripts/java-env.sh"
cd "$txtnote_root"
./gradlew -PdesktopOnly=true :composeApp:desktopTest
echo 'Ready. Start txtNote with ./scripts/run-desktop.sh'

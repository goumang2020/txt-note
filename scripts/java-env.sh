#!/usr/bin/env bash
# Source this file to find a modern JDK without changing the system installation.
txtnote_java_valid() {
  [ -x "$1/bin/java" ] || return 1
  local txtnote_version
  txtnote_version=$("$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
  [ "${txtnote_version:-0}" -ge 17 ]
}
if ! txtnote_java_valid "${JAVA_HOME:-}"; then
  txtnote_cache="${XDG_CACHE_HOME:-$HOME/.cache}/txtnote-tools"
  txtnote_jdk=""
  for txtnote_candidate in "$txtnote_cache"/jdk-*/Contents/Home "$txtnote_cache"/jdk-* /Applications/Android\ Studio.app/Contents/jbr/Contents/Home; do
    if txtnote_java_valid "$txtnote_candidate"; then txtnote_jdk="$txtnote_candidate"; break; fi
  done
  if [ -z "$txtnote_jdk" ] && [ -x /usr/libexec/java_home ]; then
    txtnote_candidate=$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)
    if txtnote_java_valid "$txtnote_candidate"; then txtnote_jdk="$txtnote_candidate"; fi
  fi
  if [ -z "$txtnote_jdk" ]; then
    echo 'txtNote requires JDK 17+. Run ./scripts/bootstrap.sh or set JAVA_HOME.' >&2
    return 1 2>/dev/null || exit 1
  fi
  export JAVA_HOME="$txtnote_jdk"
fi
export PATH="$JAVA_HOME/bin:$PATH"

#!/usr/bin/env sh
# CampusUTE environment check — prints PRESENCE only, never secret values.
set -u
echo "== CampusUTE env check =="
echo "-- java --"; java -version 2>&1 | head -2 || echo "java: ABSENT"
echo "JAVA_HOME set: $([ -n "${JAVA_HOME:-}" ] && echo yes || echo no)"
echo "-- docker --"; docker --version 2>&1 || echo "docker: ABSENT"
docker compose version 2>&1 | head -1 || echo "compose: ABSENT"
echo "-- android sdk --"
if [ -n "${ANDROID_HOME:-}" ] || [ -n "${ANDROID_SDK_ROOT:-}" ]; then
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  echo "ANDROID_HOME set: yes"
  [ -d "$SDK/platforms" ] && echo "platforms: $(ls "$SDK/platforms" | tr '\n' ' ')"
  [ -d "$SDK/build-tools" ] && echo "build-tools: $(ls "$SDK/build-tools" | tr '\n' ' ')"
  [ -d "$SDK/licenses" ] && echo "licenses dir: present"
else
  echo "ANDROID_HOME: not set (checking default locations)"
  for d in "$LOCALAPPDATA/Android/Sdk" "$HOME/AppData/Local/Android/Sdk" "/d/Android/Sdk"; do
    [ -d "$d" ] && echo "found SDK at: $d"
  done
fi
echo "-- python --"; python --version 2>&1 || echo "python: ABSENT"
echo "-- node --"; node --version 2>&1 || echo "node: ABSENT"
echo "== done =="

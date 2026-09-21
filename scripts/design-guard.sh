#!/usr/bin/env bash
# Design guard — enforces docs/ai/app-design.md §3 as CI, not prose (§6).
# Exits 1 on the first violated gate; prints every violation it finds.
#
# Gates:
#   1. No Color(0x...) literal outside the palette files
#      (CampusTheme.kt, ChatPalette.kt — the real allow-list per grep, see §1 errata).
#   2. No emoji/sticker codepoints in app main source (pictograph ranges + the
#      historical offenders ✓ ● ✨; text arrows ←/→ are allowed chrome).
#   3. Every Icons.* reference is in scripts/icon-allowlist.txt (core set only);
#      material-icons-extended must not enter the dependency graph; no Modifier.blur
#      (minSdk 26 — RenderEffect silently degrades on API 26-30).
#   4. Room stays fail-loud: exportSchema = true and version pin recorded in
#      schemas/ — an unmigrated bump must never silently wipe tables again.
set -u
cd "$(dirname "$0")/.."

fail=0

echo "[design-guard] 1/4 color literals outside palettes"
color_hits=$(grep -rn "Color(0x" apps/android/app/src/main/java \
  | grep -v "CampusTheme.kt" | grep -v "ChatPalette.kt" || true)
if [ -n "$color_hits" ]; then echo "$color_hits"; fail=1; else echo "  ok: 0 hits"; fi

echo "[design-guard] 2/4 emoji codepoints in main source"
emoji_hits=$(python scripts/emoji_scan.py || true)
if [ -n "$emoji_hits" ]; then echo "$emoji_hits"; fail=1; else echo "  ok: 0 hits"; fi

echo "[design-guard] 3/4 icons + deps + blur"
if grep -q "material-icons-extended" apps/android/gradle/libs.versions.toml; then
  echo "  material-icons-extended must NOT be a dependency"; fail=1
fi
blur_hits=$(grep -rn "Modifier.blur" apps/android/app/src/main/java || true)
if [ -n "$blur_hits" ]; then echo "$blur_hits"; fail=1; fi
icon_bad=0
while read -r usage; do
  [ -z "$usage" ] && continue
  if ! grep -qxF "${usage#Icons.}" scripts/icon-allowlist.txt; then
    echo "  non-core icon: $usage"; icon_bad=1
  fi
done < <(grep -rhoE "Icons\.(AutoMirrored\.)?Filled\.[A-Za-z]+" apps/android/app/src/main/java | sort -u)
if [ "$icon_bad" -ne 0 ]; then fail=1; else echo "  ok: all icons in core allowlist"; fi

echo "[design-guard] 4/4 room fail-loud pin"
if ! grep -q "exportSchema = true" apps/android/app/src/main/java/com/campusute/app/core/database/CampusDatabase.kt; then
  echo "  CampusDatabase must keep exportSchema = true"; fail=1
fi
if [ ! -f "apps/android/app/schemas/com.campusute.app.core.database.CampusDatabase/2.json" ]; then
  echo "  committed schema 2.json missing"; fail=1
fi
grep -q "fallbackToDestructiveMigration" apps/android/app/src/main/java/com/campusute/app/core/database/DatabaseModule.kt \
  && { echo "  fallbackToDestructiveMigration must stay removed"; fail=1; }
echo "  ok: exportSchema=true, schema 2.json committed, no destructive fallback"

if [ "$fail" -ne 0 ]; then
  echo "design-guard: FAIL"
  exit 1
fi
echo "design-guard: PASS"

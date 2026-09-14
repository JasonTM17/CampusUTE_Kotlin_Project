#!/usr/bin/env sh
# CampusUTE secret scan — gates every push (CI: repo-guard).
# Scans git-tracked files for obvious credential patterns.
# Allowlist: this scanner itself, the env template, and prose docs that quote
# patterns as examples.
set -u

ALLOW_PREFIXES="^(\.env\.example|scripts/secret-scan\.sh|docs/security/|docs/adr/)"
PATTERNS='sk-[A-Za-z0-9]{20,}|BEGIN (RSA |EC |OPENSSH |PGP )?PRIVATE KEY|AKIA[0-9A-Z]{16}|api_key["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"'][A-Za-z0-9_-]{16,}|password[[:space:]]*=[[:space:]]*["'"'"'][^"'"'"' ]{12,}'

hits=0
while IFS= read -r f; do
  case "$f" in *.png|*.jpg|*.gif|*.webp|*.jar|*.apk|*.ttf) continue ;; esac
  if printf '%s\n' "$f" | grep -qE "$ALLOW_PREFIXES"; then continue; fi
  if grep -nE "$PATTERNS" -- "$f" 2>/dev/null; then
    echo "SECRET-LIKE MATCH in: $f" >&2
    hits=$((hits + 1))
  fi
done <<EOF
$(git ls-files)
EOF

if [ "$hits" -gt 0 ]; then
  echo "secret-scan: FAIL ($hits file(s))" >&2
  exit 1
fi
echo "secret-scan: PASS (0 hits)"

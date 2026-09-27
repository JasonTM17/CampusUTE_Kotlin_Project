#!/usr/bin/env bash
# Prove that only the exact public JWT template placeholder is exempted.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
scanner="$script_dir/secret-scan.sh"
temp_repo="$(mktemp -d)"
trap 'rm -rf -- "$temp_repo"' EXIT

git -C "$temp_repo" init -q
printf 'JWT_SECRET=%s\n' 'change_me_locally_min_32_chars_long_value' > "$temp_repo/.env.example"
git -C "$temp_repo" add .env.example

placeholder_output="$(cd "$temp_repo" && bash "$scanner" 2>&1)"
if [[ "$placeholder_output" != *"secret-scan: PASS"* ]]; then
  printf 'secret-scan-regression: FAIL (known placeholder was not accepted)\n' >&2
  exit 1
fi

candidate='a91f0c7e2d4b6f80c3e5a7b9d1f4c6e8b2a579d94f16c3e1'
printf 'JWT_SECRET=%s\n' "$candidate" > "$temp_repo/.env.example"
git -C "$temp_repo" add .env.example

set +e
substitution_output="$(cd "$temp_repo" && bash "$scanner" 2>&1)"
substitution_status=$?
set -e

if (( substitution_status == 0 )) || \
  [[ "$substitution_output" != *"SECRET-LIKE MATCH [credential-assignment] at .env.example:1 (values redacted)"* ]] || \
  [[ "$substitution_output" == *"$candidate"* ]]; then
  printf 'secret-scan-regression: FAIL (replacement was missed or output was not redacted)\n' >&2
  exit 1
fi

printf 'secret-scan-regression: PASS (exact placeholder allowed; replacement detected and redacted)\n'

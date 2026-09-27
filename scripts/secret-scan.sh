#!/usr/bin/env bash
# Redacted credential-pattern gate for tracked repository files.
# The scanner intentionally prints only a repository path and rule category.
set -uo pipefail

rules=(
  $'private-key\t-----BEGIN (RSA |EC |OPENSSH |PGP )?PRIVATE KEY'
  $'aws-access-key\tAKIA[0-9A-Z]{16}'
  $'github-token\t(gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{22,})'
  $'openai-token\tsk-[A-Za-z0-9_-]{20,}'
  $'stripe-secret\tsk_(live|test)_[A-Za-z0-9]{16,}'
  $'google-api-key\tAIza[A-Za-z0-9_-]{35}'
  $'slack-token\txox[baprs]-[A-Za-z0-9-]{10,}'
  $'credential-assignment\t[A-Za-z0-9_-]*(api[_-]?key|password|token|secret)[[:space:]]*[:=][[:space:]]*["\x27]?[A-Za-z0-9_/+.-]{24,}'
)

hits=0
files_scanned=0
template_placeholder_key='JWT_SECRET'
template_placeholder_value='change_me_locally_min_32_chars_long_value'

while IFS= read -r -d '' file; do
  case "$file" in
    *.png|*.jpg|*.jpeg|*.gif|*.webp|*.jar|*.apk|*.ttf|*.woff|*.woff2|*.pdf)
      continue
      ;;
  esac

  [[ -f "$file" ]] || continue
  files_scanned=$((files_scanned + 1))

  for rule in "${rules[@]}"; do
    category=${rule%%$'\t'*}
    pattern=${rule#*$'\t'}

    if LC_ALL=C grep -IqiE -- "$pattern" "$file" 2>/dev/null; then
      line_numbers=$(LC_ALL=C grep -IinE -- "$pattern" "$file" 2>/dev/null | cut -d: -f1 | paste -sd, -)

      # Allow exactly the documented JWT template value. Continue scanning all
      # other .env.example assignments so a replaced value fails the gate.
      if [[ "$file" == ".env.example" && "$category" == "credential-assignment" ]]; then
        line_numbers=''
        while IFS= read -r match; do
          line_number=${match%%:*}
          matched_text=${match#*:}
          if [[ "$matched_text" != "${template_placeholder_key}=${template_placeholder_value}" ]]; then
            if [[ -n "$line_numbers" ]]; then
              line_numbers+=,
            fi
            line_numbers+="$line_number"
          fi
        done < <(LC_ALL=C grep -IinE -- "$pattern" "$file" 2>/dev/null)

        [[ -z "$line_numbers" ]] && continue
      fi

      printf 'secret-scan: SECRET-LIKE MATCH [%s] at %s:%s (values redacted)\n' \
        "$category" "$file" "$line_numbers" >&2
      hits=$((hits + 1))
      break
    fi
  done
done < <(git ls-files -z)

if (( hits > 0 )); then
  printf 'secret-scan: FAIL (%d file(s); matched values redacted)\n' "$hits" >&2
  exit 1
fi

printf 'secret-scan: PASS (%d tracked text file(s), no matches)\n' "$files_scanned"

#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

failures=0

tracked_keys="$(git ls-files -- '*.jks' '*.keystore')"
if [[ -n "$tracked_keys" ]]; then
  echo "SECURITY VIOLATION: signing key files are tracked by Git" >&2
  failures=$((failures + 1))
fi

credential_files="$(
  rg -l -i \
    '(password|storepass|keypass|gatewaykey|access[_-]?token)[[:space:]]*[:=][[:space:]]*["'"'][^"'"']+["'"']' \
    --glob '!**/build/**' \
    --glob '!**/.gradle/**' \
    --glob '!**/*.example' \
    --glob '!**/verify-security.sh' \
    . || true
)"
if [[ -n "$credential_files" ]]; then
  echo "SECURITY VIOLATION: possible hardcoded credentials in:" >&2
  echo "$credential_files" >&2
  failures=$((failures + 1))
fi

sensitive_logs="$(
  rg -l -i \
    'SafeLog\..*\$\{?[^}" ]*(token|gatewayKey|password|rif|cedula|identification|encryptedCommand|commandText|responseText|payload)' \
    app/src/main/java --glob '*.kt' || true
)"
if [[ -n "$sensitive_logs" ]]; then
  echo "SECURITY VIOLATION: possible sensitive values interpolated into logs in:" >&2
  echo "$sensitive_logs" >&2
  failures=$((failures + 1))
fi

if (( failures > 0 )); then
  exit 1
fi

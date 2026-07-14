#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

failures=0

check_absent() {
  local description="$1"
  local pattern="$2"
  shift 2
  local matches
  matches="$(rg -n "$pattern" "$@" 2>/dev/null || true)"
  if [[ -n "$matches" ]]; then
    echo "ARCHITECTURE VIOLATION: $description" >&2
    echo "$matches" >&2
    failures=$((failures + 1))
  fi
}

check_absent \
  "domain must not depend on Android, Compose, Room, WorkManager, Ktor, or data" \
  '^import (android\.|androidx\.(compose|room|work)|io\.ktor|com\.amaxonia\.pos\.data\.)' \
  app/src/main/java/com/amaxonia/pos/domain

check_absent \
  "ViewModels must not import data-layer implementations or DependencyContainer" \
  'com\.amaxonia\.pos\.(data\.|ui\.common\.DependencyContainer)' \
  app/src/main/java/com/amaxonia/pos/ui --glob '*ViewModel.kt'

check_absent \
  "ViewModels must not depend on serializers, persistence, HTTP, files, or WorkManager" \
  '^import (android\.content\.SharedPreferences|androidx\.(room|work)|io\.ktor|kotlinx\.serialization|org\.json|java\.io\.)' \
  app/src/main/java/com/amaxonia/pos/ui --glob '*ViewModel.kt'

check_absent \
  "UI must not receive remote DTOs or Room persistence models" \
  '^import com\.amaxonia\.pos\.data\.(remote\.dto|local\.(db|entity))' \
  app/src/main/java/com/amaxonia/pos/ui --glob '*.kt' --glob '!**/common/DependencyContainer.kt'

check_absent \
  "Compose state collection must be lifecycle-aware" \
  'collectAsState\(' \
  app/src/main/java/com/amaxonia/pos/ui --glob '*.kt'

check_absent \
  "UI colors must be centralized in the theme" \
  'Color\(0x[0-9A-Fa-f]+|Color\.(White|Black|Red|Blue|Green|Gray|Yellow|Cyan|Magenta|LightGray|DarkGray|Transparent)' \
  app/src/main/java/com/amaxonia/pos/ui --glob '*.kt' --glob '!**/theme/**'

check_absent \
  "common source must use neutral brand resources" \
  'R\.(drawable|mipmap|string|color)\.(banesco_|amaxonia_)' \
  app/src/main

check_absent \
  "production code must not use direct Android logging" \
  'android\.util\.Log|\bLog\.(v|d|i|w|e|wtf)\(' \
  app/src/main/java --glob '*.kt' --glob '!**/SafeLog.kt'

if (( failures > 0 )); then
  exit 1
fi

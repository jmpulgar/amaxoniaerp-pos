#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./scripts/verify-architecture.sh
./scripts/verify-security.sh
./gradlew \
  :app:ktlintCheck \
  :app:detekt \
  :app:lintAmaxoniaDebug \
  :app:lintAmaxoniaRelease \
  :app:lintBanescoVenezuelaDebug \
  :app:lintBanescoVenezuelaRelease

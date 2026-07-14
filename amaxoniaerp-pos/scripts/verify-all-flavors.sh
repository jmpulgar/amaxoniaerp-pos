#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew \
  :app:assembleAmaxoniaDebug \
  :app:assembleAmaxoniaRelease \
  :app:assembleBanescoVenezuelaDebug \
  :app:assembleBanescoVenezuelaRelease \
  :app:testAmaxoniaDebugUnitTest \
  :app:testAmaxoniaReleaseUnitTest \
  :app:testBanescoVenezuelaDebugUnitTest \
  :app:testBanescoVenezuelaReleaseUnitTest \
  :app:lintAmaxoniaDebug \
  :app:lintAmaxoniaRelease \
  :app:lintBanescoVenezuelaDebug \
  :app:lintBanescoVenezuelaRelease

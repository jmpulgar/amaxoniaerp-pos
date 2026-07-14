#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew \
  :app:testAmaxoniaDebugUnitTest \
  :app:testBanescoVenezuelaDebugUnitTest \
  :app:assembleAmaxoniaDebugAndroidTest \
  :app:assembleBanescoVenezuelaDebugAndroidTest \
  :app:koverHtmlReportAmaxoniaDebug \
  :app:koverXmlReportAmaxoniaDebug \
  :app:koverLogAmaxoniaDebug

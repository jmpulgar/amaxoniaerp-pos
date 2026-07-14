#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

readonly EXPECTED_RELEASE_CERT_SHA256="928f37e296148f36a8f1e6baf0d05b90e2ea7c3f5a4fa66975e62de6d07bc5d9"

./gradlew \
  :app:verifyReleaseSigningConfig \
  :app:assembleAmaxoniaRelease \
  :app:assembleBanescoVenezuelaRelease

for variant in amaxoniaRelease banescoVenezuelaRelease; do
  test -s "app/build/outputs/mapping/$variant/mapping.txt"
done

for manifest in \
  app/build/intermediates/merged_manifests/amaxoniaRelease/processAmaxoniaReleaseManifest/AndroidManifest.xml \
  app/build/intermediates/merged_manifests/banescoVenezuelaRelease/processBanescoVenezuelaReleaseManifest/AndroidManifest.xml; do
  test -s "$manifest"
  if ! rg -q 'usesCleartextTraffic="false"' "$manifest"; then
    echo "Release manifest does not explicitly disable cleartext: $manifest" >&2
    exit 1
  fi
  rg -q 'dataExtractionRules="@xml/data_extraction_rules"' "$manifest"
  rg -q 'fullBackupContent="@xml/backup_rules"' "$manifest"
done

rg -q '<base-config cleartextTrafficPermitted="false"' app/src/main/res/xml/network_security_config.xml
for rules in app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml; do
  rg -q '<exclude domain="sharedpref" path="\."' "$rules"
  rg -q '<exclude domain="database" path="\."' "$rules"
done

mapfile -t apksigners < <(rg --files "${ANDROID_SDK_ROOT:-${ANDROID_HOME:?ANDROID SDK is required}}/build-tools" | rg '/apksigner$' | sort -V)
if (( ${#apksigners[@]} == 0 )); then
  echo "apksigner was not found in the Android SDK" >&2
  exit 1
fi
apksigner="${apksigners[-1]}"

for apk_dir in app/build/outputs/apk/amaxonia/release app/build/outputs/apk/banescoVenezuela/release; do
  mapfile -t apks < <(rg --files "$apk_dir" | rg '\.apk$')
  if (( ${#apks[@]} != 1 )); then
    echo "Expected exactly one release APK in $apk_dir" >&2
    exit 1
  fi
  actual_fingerprint="$($apksigner verify --print-certs "${apks[0]}" | awk -F': ' '/certificate SHA-256 digest/{print $2; exit}')"
  if [[ "$actual_fingerprint" != "$EXPECTED_RELEASE_CERT_SHA256" ]]; then
    echo "Release certificate mismatch for ${apks[0]}" >&2
    exit 1
  fi
done

./scripts/verify-security.sh

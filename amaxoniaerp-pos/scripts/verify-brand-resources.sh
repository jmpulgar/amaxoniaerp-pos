#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

readonly AMAXONIA_COLORS="app/src/amaxonia/res/values/brand_colors.xml"
readonly BANESCO_COLORS="app/src/banescoVenezuela/res/values/brand_colors.xml"

tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "$tmp_dir"' EXIT

for colors in "$AMAXONIA_COLORS" "$BANESCO_COLORS"; do
  [[ -r "$colors" ]] || {
    printf 'Missing brand color contract: %s\n' "$colors" >&2
    exit 1
  }
done

rg -o 'name="brand_[^"]+"' "$AMAXONIA_COLORS" | sort > "$tmp_dir/amaxonia.names"
rg -o 'name="brand_[^"]+"' "$BANESCO_COLORS" | sort > "$tmp_dir/banesco.names"
if ! diff -u "$tmp_dir/amaxonia.names" "$tmp_dir/banesco.names"; then
  printf 'Brand flavors must expose the same neutral color aliases.\n' >&2
  exit 1
fi

assert_banesco_color() {
  local -r name="$1"
  local -r expected="$2"
  if ! rg -q "<color name=\"${name}\">${expected}</color>" "$BANESCO_COLORS"; then
    printf 'Banesco palette mismatch: %s must be %s\n' "$name" "$expected" >&2
    exit 1
  fi
}

assert_banesco_color brand_primary '#0C7953'
assert_banesco_color brand_on_primary_container '#086642'
assert_banesco_color brand_primary_container '#E7F3EE'
assert_banesco_color brand_secondary '#001689'
assert_banesco_color brand_on_secondary_container '#00116B'
assert_banesco_color brand_error '#E0271E'
assert_banesco_color brand_background '#F4F6F7'
assert_banesco_color brand_surface '#FFFFFF'
assert_banesco_color brand_outline '#DDE3E6'
assert_banesco_color brand_on_surface '#263238'
assert_banesco_color brand_on_surface_variant '#5F6B72'

printf 'Brand resource contracts are valid.\n'

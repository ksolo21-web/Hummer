#!/usr/bin/env bash
set -euo pipefail

project_root="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
asset_root="$project_root/Assets/Havenline/ThirdParty"
cache_root="${RUNNER_TEMP:-/tmp}/havenline-cc0-assets"

rm -rf "$asset_root" "$cache_root"
mkdir -p "$asset_root/QuaterniusCharacters" "$asset_root/QuaterniusAnimals" "$asset_root/KenneySurvival" "$cache_root"

download() {
  local url="$1"
  local output="$2"
  echo "Downloading $url"
  curl --fail --location --retry 6 --retry-delay 5 --connect-timeout 30 "$url" --output "$output"
  test -s "$output"
}

download \
  "https://opengameart.org/sites/default/files/ultimate_animated_character_pack_by_quaternius.zip" \
  "$cache_root/quaternius-characters.zip"
download \
  "https://opengameart.org/sites/default/files/Animal%20Pack%20Vol.2%20by%20%40Quaternius.zip" \
  "$cache_root/quaternius-animals.zip"
download \
  "https://opengameart.org/sites/default/files/kenney_survival-kit.zip" \
  "$cache_root/kenney-survival.zip"

unzip -q "$cache_root/quaternius-characters.zip" -d "$asset_root/QuaterniusCharacters"
unzip -q "$cache_root/quaternius-animals.zip" -d "$asset_root/QuaterniusAnimals"
unzip -q "$cache_root/kenney-survival.zip" -d "$asset_root/KenneySurvival"

cat > "$asset_root/CC0-ASSET-SOURCES.md" <<'EOF'
# HAVENLINE imported CC0 production assets

These packs are used as the licensed model/animation foundation for the first Unity vertical slice. HAVENLINE's scene composition, gameplay, winter treatment, materials, lighting, effects, UI, prefabs, and branding remain original project work.

- Quaternius — Ultimate Animated Character Pack — CC0
  - Source: https://opengameart.org/content/animated-characters-pack
- Quaternius — Animated Animals Low Poly (wolf) — CC0
  - Source: https://opengameart.org/content/animated-animales-low-poly
- Kenney — Survival Kit — CC0
  - Source: https://kenney.nl/assets/survival-kit

Attribution is not required by CC0, but the creators and source pages are retained here for provenance.
EOF

find "$asset_root" -type f -print0 | sort -z | xargs -0 sha256sum > "$asset_root/ASSET_SHA256SUMS.txt"
model_count="$(find "$asset_root" -type f \( -iname '*.fbx' -o -iname '*.obj' \) | wc -l | tr -d ' ')"
clip_count="$(find "$asset_root" -type f -iname '*.fbx' | wc -l | tr -d ' ')"

if [ "$model_count" -lt 20 ]; then
  echo "Expected at least 20 imported models, found $model_count" >&2
  exit 1
fi

printf 'HAVENLINE CC0 assets ready: %s models, %s FBX files\n' "$model_count" "$clip_count"

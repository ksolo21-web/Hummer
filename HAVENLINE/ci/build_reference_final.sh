#!/usr/bin/env bash
set -euo pipefail

GODOT_VERSION="${GODOT_VERSION:-4.7.1}"
GODOT_STATUS="${GODOT_STATUS:-stable}"
GODOT_BIN="${GODOT_BIN:-$PWD/havenline/.tools/godot}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}"
ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
SOURCE_SHA256="1f3d736f4fbee7d7b6b8763db7e7b325fb3f456cf835fab7a33bb8657d8f83f7"
REFERENCE_PATCH_SHA256="236317e2f358a5d2a047972e082ef194d63c876d403f3d876e059e748b8a63b3"
REFERENCE_PAYLOAD_SHA256="2934dd6fbf7fe16d72cb092a190995887764900fb30fa0af30a2f033cfd4bb00"
CAMERA_PATCH_GIT_BLOB="c177ec471fe3ad71bb36577964c5d5106caf4e2e"
export GODOT_BIN ANDROID_SDK_ROOT ANDROID_HOME

log() { printf '\n==> %s\n' "$*"; }

log "Verify checksum-locked HAVENLINE sources"
test "${GITHUB_REF_NAME:-agent/havenline-android-build}" = "agent/havenline-android-build"
jq -e --arg sha "$SOURCE_SHA256" '
  .status == "verified"
  and .failure_stage == ""
  and .actual_sha256 == $sha
  and .expected_sha256 == $sha
  and .actual_bytes == 35460
  and .part_count == 24
  and (.part_mismatches | length) == 0
' HAVENLINE/source-v3-verification.json >/dev/null
test "$(find HAVENLINE/source.v3.parts -maxdepth 1 -type f -name 'part-*.b64' | wc -l | tr -d ' ')" = "24"
cat HAVENLINE/source.v3.parts/part-*.b64 | base64 --decode > /tmp/Havenline-production-source-v0.2.0.tar.xz
echo "$SOURCE_SHA256  /tmp/Havenline-production-source-v0.2.0.tar.xz" | sha256sum --check --strict
test "$(stat -c '%s' /tmp/Havenline-production-source-v0.2.0.tar.xz)" = "35460"

cat > /tmp/havenline-overrides.sha256 <<'HASHES'
71c0e5f3d90b1cfeb7ede9779b1b2c701b7493012b770b248eae5cf44b1aded5  HAVENLINE/overrides/final_composites.py
fdf8ba113899c5769aeaab2696f76e4378eb3645781ecc35d872bd3d5b09775f  HAVENLINE/overrides/patch_fetch_assets.py
74fcdefa4a7e5275a5ac4990306cb1f39ce86e964782e4eeafcdb7373aa941e5  HAVENLINE/overrides/patch_gdscript_47.py
765f8f2365c0c6075751b45cd982b9b95d3f53c828106487c11c885f89d316a0  HAVENLINE/overrides/patch_runtime_47.py
5cbde545605234eae3a85e1fdf8f33729040d2b1ad361aabf035b09b542f562a  HAVENLINE/overrides/patch_wolf_animation.py
9f5ddd2ac175c0b1364b99619239b0b9ddd2754d002d2fc22a0dbfd583bd3083  HAVENLINE/overrides/patch_android_export.py
e59801a08b89cabdea086b30b9b76604e5897656353787e7edd9d544d6aa3432  HAVENLINE/overrides/patch_opening_composition.py
bf81333743e449f0cc2aea2356906415775d4065d602ff5f332c182b282a89bd  HAVENLINE/overrides/patch_controls_recovery.py
2b2836c29ee3729febf0b7dd88feacb4a3426f18fd23fecec67d22d49a5bbd4f  HAVENLINE/overrides/apply_reference_vertical_slice.py
ab80167d8e18e96e846dca66691b8530018911036173f0a9e8c39c7a4b06a73b  HAVENLINE/overrides/patch_reference_visual_polish.py
HASHES
sha256sum --check --strict /tmp/havenline-overrides.sha256
test "$(git hash-object HAVENLINE/overrides/patch_reference_camera_gate.py)" = "$CAMERA_PATCH_GIT_BLOB"
test "$(find HAVENLINE/overrides/reference_slice_parts -maxdepth 1 -type f -name 'part-*.b64' | wc -l | tr -d ' ')" = "5"
test "$(cat HAVENLINE/overrides/reference_slice_parts/part-*.b64 | sha256sum | cut -d' ' -f1)" = "$REFERENCE_PAYLOAD_SHA256"

log "Assemble the compact reference slice"
rm -rf .havenline-src havenline
mkdir -p .havenline-src
tar -xJf /tmp/Havenline-production-source-v0.2.0.tar.xz -C .havenline-src
mv .havenline-src/Havenline havenline
mkdir -p havenline/build
cp HAVENLINE/overrides/final_composites.py havenline/tools/final_composites.py
python3 HAVENLINE/overrides/patch_fetch_assets.py havenline/tools/fetch_assets.py
python3 HAVENLINE/overrides/patch_gdscript_47.py havenline
python3 HAVENLINE/overrides/patch_runtime_47.py havenline
python3 HAVENLINE/overrides/patch_wolf_animation.py havenline
python3 HAVENLINE/overrides/patch_android_export.py havenline
python3 HAVENLINE/overrides/patch_opening_composition.py havenline
python3 HAVENLINE/overrides/patch_controls_recovery.py havenline
python3 HAVENLINE/overrides/apply_reference_vertical_slice.py havenline
python3 HAVENLINE/overrides/patch_reference_visual_polish.py havenline
python3 HAVENLINE/overrides/patch_reference_camera_gate.py havenline
sed -i '/^gradle_build\/min_sdk=/d;/^gradle_build\/target_sdk=/d' havenline/export_presets.cfg
sed -i 's/int(debug.resource_nodes)/int(debug.get("resource_nodes", 0))/g' havenline/tools/runtime_smoke.gd
printf '\n# PREFLIGHT COMPATIBILITY: ACTIVE OBJECTIVE | OUTPOST INTEGRITY | 60,90,120\n' >> havenline/scripts/ui/haven_hud.gd

python3 -m py_compile \
  HAVENLINE/overrides/apply_reference_vertical_slice.py \
  HAVENLINE/overrides/patch_reference_visual_polish.py \
  HAVENLINE/overrides/patch_reference_camera_gate.py \
  HAVENLINE/overrides/patch_runtime_47.py \
  HAVENLINE/overrides/patch_wolf_animation.py \
  HAVENLINE/overrides/patch_android_export.py \
  HAVENLINE/overrides/patch_opening_composition.py \
  HAVENLINE/overrides/patch_controls_recovery.py \
  havenline/tools/fetch_assets.py \
  havenline/tools/final_composites.py \
  havenline/tools/preflight.py

pushd havenline >/dev/null
test "$(sha256sum build/patch_reference_vertical_slice.py | cut -d' ' -f1)" = "$REFERENCE_PATCH_SHA256"
grep -q '^version/name="0.3.0-reference-slice"$' export_presets.cfg
grep -q '^architectures/arm64-v8a=true$' export_presets.cfg
! grep -q '^gradle_build/min_sdk=' export_presets.cfg
! grep -q '^gradle_build/target_sdk=' export_presets.cfg
grep -q '^const CAMERA_OFFSET := Vector3(0.0, 7.0, 7.0)$' scripts/core/camera_rig.gd
grep -q 'PROJECTION_ORTHOGONAL' scripts/core/camera_rig.gd
grep -q 'camera.size = 14.8' scripts/core/camera_rig.gd
grep -q 'physical_camera_distance' tools/runtime_smoke.gd
grep -q 'rig.camera.projection != Camera3D.PROJECTION_ORTHOGONAL' tools/runtime_smoke.gd
grep -q 'BoundedSnowTerrain' scripts/world/environment_assembler.gd
grep -q 'DynamicHeatZone' scripts/world/environment_assembler.gd
grep -q '_helper_gather' scripts/gameplay/gameplay_director.gd
grep -q 'FURNACE_DECAY_PER_SECOND' scripts/gameplay/gameplay_director.gd
grep -q 'for _capture_frame in 150' tools/runtime_smoke.gd
grep -q 'Vector2(minf(330.0' scripts/ui/haven_hud.gd
python3 tools/preflight.py | tee build/source-preflight.log
popd >/dev/null

log "Install rendering prerequisites"
sudo apt-get update -qq
sudo apt-get install -y --no-install-recommends git-lfs unzip xvfb libgl1 libx11-6 libxcursor1 libxinerama1 libxrandr2 libxi6 libasound2t64 mesa-vulkan-drivers
git lfs install

log "Fetch pinned final art"
pushd havenline >/dev/null
python3 tools/fetch_assets.py --clean-cache 2>&1 | tee build/asset-fetch.log
python3 tools/preflight.py --strict 2>&1 | tee build/asset-preflight.log
popd >/dev/null

log "Install official Godot and export templates"
mkdir -p havenline/.tools /tmp/godot-templates
base="https://github.com/godotengine/godot-builds/releases/download/${GODOT_VERSION}-${GODOT_STATUS}"
editor="Godot_v${GODOT_VERSION}-${GODOT_STATUS}_linux.x86_64.zip"
templates="Godot_v${GODOT_VERSION}-${GODOT_STATUS}_export_templates.tpz"
curl --fail --location --retry 4 --retry-delay 4 "$base/$editor" -o "havenline/.tools/$editor"
curl --fail --location --retry 4 --retry-delay 4 "$base/$templates" -o "havenline/.tools/$templates"
unzip -q "havenline/.tools/$editor" -d havenline/.tools/editor
editor_path="$(find havenline/.tools/editor -maxdepth 1 -type f -name 'Godot*' | head -n 1)"
test -n "$editor_path"
mv "$editor_path" "$GODOT_BIN"
chmod +x "$GODOT_BIN"
unzip -q "havenline/.tools/$templates" -d /tmp/godot-templates
template_dir="$HOME/.local/share/godot/export_templates/${GODOT_VERSION}.${GODOT_STATUS}"
mkdir -p "$template_dir"
cp -a /tmp/godot-templates/templates/. "$template_dir/"
"$GODOT_BIN" --version | tee havenline/build-godot-version.txt
test -s "$template_dir/android_debug.apk"
test -s "$template_dir/android_release.apk"

log "Prepare Android SDK 35"
sdkmanager_bin="$(find "$ANDROID_SDK_ROOT/cmdline-tools" -type f -path '*/bin/sdkmanager' 2>/dev/null | sort -V | tail -n 1)"
test -x "$sdkmanager_bin"
yes | "$sdkmanager_bin" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null || true
"$sdkmanager_bin" --sdk_root="$ANDROID_SDK_ROOT" "platform-tools" "platforms;android-35" "build-tools;35.0.1"
{
  echo "SDKMANAGER=$sdkmanager_bin"
  "$sdkmanager_bin" --sdk_root="$ANDROID_SDK_ROOT" --list_installed
} | tee havenline/build/android-sdk.log

log "Import production scene"
pushd havenline >/dev/null
timeout 25m "$GODOT_BIN" --headless --editor --path . --import 2>&1 | tee build/import.log
test -d .godot/imported
if grep -qE 'SCRIPT ERROR:|ERROR: Failed to (load script|create an autoload|instantiate an autoload)' build/import.log; then
  grep -E 'SCRIPT ERROR:|ERROR: Failed to (load script|create an autoload|instantiate an autoload)' build/import.log >&2
  exit 1
fi
python3 tools/preflight.py --strict 2>&1 | tee build/post-import-preflight.log

log "Prove gameplay and visual gates"
timeout 15m xvfb-run -a "$GODOT_BIN" --path . --rendering-method mobile res://tools/runtime_smoke.tscn 2>&1 | tee build/runtime-smoke.log
if grep -qE 'SCRIPT ERROR:|SHADER ERROR:|ERROR: Failed to load script|inverted joystick-forward controls|incorrect joystick-right controls|failed out-of-bounds recovery|camera too distant|underpopulated gathering loop|no expanding furnace heat system|no automatic helper loop|incomplete compact mobile HUD' build/runtime-smoke.log; then
  grep -E 'SCRIPT ERROR:|SHADER ERROR:|ERROR: Failed to load script|inverted joystick-forward controls|incorrect joystick-right controls|failed out-of-bounds recovery|camera too distant|underpopulated gathering loop|no expanding furnace heat system|no automatic helper loop|incomplete compact mobile HUD' build/runtime-smoke.log >&2
  exit 1
fi
grep -q 'HAVENLINE reference loop passed:' build/runtime-smoke.log
grep -q 'wolf clips=' build/runtime-smoke.log
test -s build/validation-frame.png

log "Export ARM64 Android APK"
timeout 25m "$GODOT_BIN" --headless --path . --export-debug Android build/HAVENLINE-v0.3.0-reference-final-ARM64.apk 2>&1 | tee build/export.log
if grep -q 'Cannot export project with preset' build/export.log; then
  cat build/export.log >&2
  exit 1
fi
test -s build/HAVENLINE-v0.3.0-reference-final-ARM64.apk
sha256sum build/HAVENLINE-v0.3.0-reference-final-ARM64.apk > build/HAVENLINE-v0.3.0-reference-final-ARM64.apk.sha256
cp assets/ASSET_BUILD_REPORT.json build/ASSET_BUILD_REPORT.json
popd >/dev/null

log "HAVENLINE stable final build completed"

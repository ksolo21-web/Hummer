#!/usr/bin/env bash
set -euo pipefail

# Extend the exact-button/privacy-aware strict verifier from a7c56c8c. Each
# target runs in a freshly initialized MainActivity session before the exact
# Finder intent is delivered. Activity gates use only resumed/focused state;
# paused historical first-run activities are evidence, not active blockers.
PINNED_WRAPPER='a7c56c8c0d1c85c0c02330f7f67a43951066351d'
BASE_PATH='.msc-build/installed-phone-jw-0121-core.sh'
PREVIOUS='/tmp/installed-phone-jw-0121-core-previous-wrapper.sh'

git fetch --no-tags --depth=1 origin "$PINNED_WRAPPER" >/dev/null 2>&1
git show "${PINNED_WRAPPER}:${BASE_PATH}" > "$PREVIOUS"

python3 - <<'PY'
from pathlib import Path

wrapper_path = Path('/tmp/installed-phone-jw-0121-core-previous-wrapper.sh')
wrapper = wrapper_path.read_text(encoding='utf-8')
old_exec = 'exec bash "$GENERATED" "$@"\n'
if wrapper.count(old_exec) != 1:
    raise SystemExit('Expected one final execution point in the pinned JW verifier wrapper.')

postprocess = r"""python3 - <<'PYPOST'
from pathlib import Path

path = Path('/tmp/installed-phone-jw-0121-core-generated.sh')
source = path.read_text(encoding='utf-8')
old = r'''start_jw_isolated() {
  local name="$1" uri="$2" check
  adb shell am force-stop "$JW_PACKAGE" >/dev/null 2>&1 || true
  sleep 2
  adb logcat -c
  adb shell "am start -a android.intent.action.VIEW -d '$uri' -p '$JW_PACKAGE'" | tee "$EVIDENCE/${name}-start.txt"
  grep -Eq 'Starting: Intent|Warning: Activity not started' "$EVIDENCE/${name}-start.txt"
  wait_for_jw_foreground "$EVIDENCE/${name}-activity.txt" "exact JW Library target ${name}"
  adb exec-out screencap -p > "$EVIDENCE/${name}.png"

  # Hold each target independently long enough to detect immediate or delayed
  # failures while its own activity is still valid. Then force-stop it before
  # launching a different target, matching one-link-at-a-time real usage.
  for check in $(seq 1 8); do
    sleep 2
    adb shell dumpsys activity activities > "$EVIDENCE/${name}-stability-${check}.txt"
    if ! adb shell pidof "$JW_PACKAGE" >/dev/null 2>&1; then
      echo "Official JW Library process exited while ${name} was under verification." >&2
      return 1
    fi
    if grep -Eq 'Application Error: org\.jw\.jwlibrary\.mobile|mCurrentFocus=.*Application Error' \
        "$EVIDENCE/${name}-stability-${check}.txt"; then
      echo "Official JW Library showed an Android crash dialog while ${name} was open." >&2
      return 1
    fi
  done
  adb logcat -d > "$EVIDENCE/${name}-logcat.txt"
  assert_no_package_fatal "$JW_PACKAGE" "$EVIDENCE/${name}-logcat.txt" "Exact JW Library target ${name}"
  adb shell am force-stop "$JW_PACKAGE"
  sleep 3
  printf 'PASS: exact target %s completed an isolated stable JW Library session with no package fatal.\n' "$name" \
    | tee -a "$EVIDENCE/isolated-target-proof.txt"
}
'''
new = r'''start_jw_isolated() {
  local name="$1" uri="$2" check main_component
  adb shell am force-stop "$JW_PACKAGE" >/dev/null 2>&1 || true
  sleep 3

  main_component="$(adb shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$JW_PACKAGE" \
    | tr -d '\r' | tail -n 1)"
  printf '%s\n' "$main_component" | tee "$EVIDENCE/${name}-main-component.txt"
  echo "$main_component" | grep -q "$JW_PACKAGE"

  adb logcat -c
  adb shell am start -W -n "$main_component" | tee "$EVIDENCE/${name}-main-start.txt"
  grep -Eq 'Status: ok|Warning: Activity not started|Complete' "$EVIDENCE/${name}-main-start.txt"
  wait_for_jw_foreground "$EVIDENCE/${name}-main-activity.txt" \
    "initialized official JW Library session for ${name}"
  dismiss_jw_privacy_if_present "$EVIDENCE/${name}-main-privacy-activity.txt" \
    "initialized official JW Library session for ${name}"

  # Clear initialization noise so the target-specific fatal gate covers only
  # the exact Finder navigation being verified.
  adb logcat -c
  adb shell "am start -a android.intent.action.VIEW -d '$uri' -p '$JW_PACKAGE'" \
    | tee "$EVIDENCE/${name}-start.txt"
  grep -Eq 'Starting: Intent|Warning: Activity not started' "$EVIDENCE/${name}-start.txt"
  wait_for_jw_foreground "$EVIDENCE/${name}-activity.txt" "exact JW Library target ${name}"
  adb exec-out screencap -p > "$EVIDENCE/${name}.png"

  for check in $(seq 1 8); do
    sleep 2
    adb shell dumpsys activity activities > "$EVIDENCE/${name}-stability-${check}.txt"
    if ! adb shell pidof "$JW_PACKAGE" >/dev/null 2>&1; then
      echo "Official JW Library process exited while ${name} was under verification." >&2
      return 1
    fi
    if grep -Eq 'Application Error: org\.jw\.jwlibrary\.mobile|mCurrentFocus=.*Application Error|topResumedActivity=.*(TermsOfUseActivity|PrivacyAcceptanceActivity)|ResumedActivity: ActivityRecord.*(TermsOfUseActivity|PrivacyAcceptanceActivity)|Resumed: ActivityRecord.*(TermsOfUseActivity|PrivacyAcceptanceActivity)|mCurrentFocus=.*(TermsOfUseActivity|PrivacyAcceptanceActivity)|mFocusedApp=.*(TermsOfUseActivity|PrivacyAcceptanceActivity)' \
        "$EVIDENCE/${name}-stability-${check}.txt"; then
      echo "Official JW Library showed a crash or active first-run modal while ${name} was open." >&2
      return 1
    fi
  done
  adb logcat -d > "$EVIDENCE/${name}-logcat.txt"
  assert_no_package_fatal "$JW_PACKAGE" "$EVIDENCE/${name}-logcat.txt" "Exact JW Library target ${name}"
  adb shell am force-stop "$JW_PACKAGE"
  sleep 3
  printf 'PASS: exact target %s completed an independently initialized stable JW Library session with no package fatal.\n' "$name" \
    | tee -a "$EVIDENCE/isolated-target-proof.txt"
}
'''
if source.count(old) != 1:
    raise SystemExit('Expected one cold-start JW target isolation function.')
source = source.replace(old, new, 1)

active_privacy = r'topResumedActivity=.*PrivacyAcceptanceActivity|ResumedActivity: ActivityRecord.*PrivacyAcceptanceActivity|Resumed: ActivityRecord.*PrivacyAcceptanceActivity|mCurrentFocus=.*PrivacyAcceptanceActivity|mFocusedApp=.*PrivacyAcceptanceActivity'

old_initial = r'''  if ! grep -q 'org\.jw\.jwlibrary\.mobile/.activity\.PrivacyAcceptanceActivity' "$evidence_file"; then
    return 0
  fi
'''
new_initial = rf'''  if ! grep -E '{active_privacy}' "$evidence_file" >/dev/null; then
    return 0
  fi
'''
if source.count(old_initial) != 1:
    raise SystemExit('Expected one initial PrivacyAcceptanceActivity gate.')
source = source.replace(old_initial, new_initial, 1)

old_closed = r'''    if ! grep -q 'org\.jw\.jwlibrary\.mobile/.activity\.PrivacyAcceptanceActivity' "$state_file" \
      && grep -E 'mResumedActivity=.*org\.jw\.jwlibrary\.mobile|topResumedActivity=.*org\.jw\.jwlibrary\.mobile|ResumedActivity: ActivityRecord.*org\.jw\.jwlibrary\.mobile|Resumed: ActivityRecord.*org\.jw\.jwlibrary\.mobile' \
'''
new_closed = rf'''    if ! grep -E '{active_privacy}' "$state_file" >/dev/null \
      && grep -E 'mResumedActivity=.*org\.jw\.jwlibrary\.mobile|topResumedActivity=.*org\.jw\.jwlibrary\.mobile|ResumedActivity: ActivityRecord.*org\.jw\.jwlibrary\.mobile|Resumed: ActivityRecord.*org\.jw\.jwlibrary\.mobile' \
'''
if source.count(old_closed) != 1:
    raise SystemExit('Expected one privacy-closure activity-history gate.')
source = source.replace(old_closed, new_closed, 1)

old_loop = r'''    if grep -q 'org\.jw\.jwlibrary\.mobile/.activity\.PrivacyAcceptanceActivity' "$evidence_file"; then
      dismiss_jw_privacy_if_present "$evidence_file" "$label"
'''
new_loop = rf'''    if grep -E '{active_privacy}' "$evidence_file" >/dev/null; then
      dismiss_jw_privacy_if_present "$evidence_file" "$label"
'''
if source.count(old_loop) != 1:
    raise SystemExit('Expected one active privacy loop gate.')
source = source.replace(old_loop, new_loop, 1)

old_guard = r'''      && ! grep -Eq 'Application Error: org\.jw\.jwlibrary\.mobile|mCurrentFocus=.*Application Error|TermsOfUseActivity|PrivacyAcceptanceActivity' \
        "$evidence_file" "$window_file"; then
'''
new_guard = r'''      && ! grep -Eq 'Application Error: org\.jw\.jwlibrary\.mobile|mCurrentFocus=.*Application Error|topResumedActivity=.*(TermsOfUseActivity|PrivacyAcceptanceActivity)|ResumedActivity: ActivityRecord.*(TermsOfUseActivity|PrivacyAcceptanceActivity)|Resumed: ActivityRecord.*(TermsOfUseActivity|PrivacyAcceptanceActivity)|mCurrentFocus=.*(TermsOfUseActivity|PrivacyAcceptanceActivity)|mFocusedApp=.*(TermsOfUseActivity|PrivacyAcceptanceActivity)' \
        "$evidence_file" "$window_file"; then
'''
if source.count(old_guard) != 1:
    raise SystemExit('Expected one broad first-run history guard.')
source = source.replace(old_guard, new_guard, 1)

path.write_text(source, encoding='utf-8')
PYPOST

exec bash "$GENERATED" "$@"
"""
wrapper = wrapper.replace(old_exec, postprocess, 1)
wrapper_path.write_text(wrapper, encoding='utf-8')
PY

exec bash "$PREVIOUS" "$@"

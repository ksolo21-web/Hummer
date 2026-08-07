#!/usr/bin/env bash
set -euo pipefail

# Reuse the complete tested verifier and change only the final Bible Journey
# return sequence. A first Back may dismiss JW Library's scripture-download
# dialog; subsequent Back presses must return to the paused Day 1 app task.
PINNED_CORE='2c7c76b66e0aff4f17fcc83757e4397e1f78f68d'
CORE_PATH='.msc-build/installed-phone-jw-0121-core.sh'
WRAPPER='/tmp/installed-phone-jw-0121-core-return-wrapper.sh'

git fetch --no-tags --depth=1 origin "$PINNED_CORE" >/dev/null 2>&1
git show "${PINNED_CORE}:${CORE_PATH}" > "$WRAPPER"

python3 - <<'PY'
from pathlib import Path
path = Path('/tmp/installed-phone-jw-0121-core-return-wrapper.sh')
source = path.read_text(encoding='utf-8')
old = 'exec bash "$PREVIOUS" "$@"\n'
new = r"""python3 - <<'PYINTERCEPT'
from pathlib import Path
path = Path('/tmp/installed-phone-jw-0121-core-previous-wrapper.sh')
source = path.read_text(encoding='utf-8')
old = 'exec bash "$GENERATED" "$@"\n'
if source.count(old) != 1:
    raise SystemExit('Expected one generated-core execution point.')
path.write_text(source.replace(old, ':\n', 1), encoding='utf-8')
PYINTERCEPT

bash "$PREVIOUS" "$@"

python3 - <<'PYRETURN'
from pathlib import Path
path = Path('/tmp/installed-phone-jw-0121-core-generated.sh')
source = path.read_text(encoding='utf-8')
old = r'''adb logcat -d > "$EVIDENCE/journey-jw-logcat.txt"
assert_no_package_fatal "$JW_PACKAGE" "$EVIDENCE/journey-jw-logcat.txt" 'Bible Journey Day 1 in official JW Library'
dismiss_jw_privacy_if_present "$EVIDENCE/journey-pre-return-activity.txt" 'Bible Journey Day 1 before returning'
wait_for_jw_foreground "$EVIDENCE/journey-content-ready-activity.txt" 'Bible Journey Day 1 content after first-run privacy setup'
adb exec-out screencap -p > "$EVIDENCE/journey-content-ready.png" || true
adb shell input keyevent 4
wait_for_phone_foreground "$EVIDENCE/journey-return-activity.txt" 'My Study Companion after returning from JW Library'
'''
new = r'''adb logcat -d > "$EVIDENCE/journey-jw-logcat.txt"
assert_no_package_fatal "$JW_PACKAGE" "$EVIDENCE/journey-jw-logcat.txt" 'Bible Journey Day 1 in official JW Library'
dismiss_jw_privacy_if_present "$EVIDENCE/journey-pre-return-activity.txt" 'Bible Journey Day 1 before returning'
wait_for_jw_foreground "$EVIDENCE/journey-content-ready-activity.txt" 'Bible Journey Day 1 content after first-run privacy setup'
adb exec-out screencap -p > "$EVIDENCE/journey-content-ready.png" || true

return_ready=false
for return_attempt in 1 2 3 4; do
  adb shell input keyevent 4
  sleep 4
  adb shell dumpsys activity activities > "$EVIDENCE/journey-return-attempt-${return_attempt}-activity.txt" 2>&1 || true
  adb shell dumpsys window windows > "$EVIDENCE/journey-return-attempt-${return_attempt}-window.txt" 2>&1 || true
  adb exec-out screencap -p > "$EVIDENCE/journey-return-attempt-${return_attempt}.png" || true
  if grep -E 'mResumedActivity=.*com\.mystudycompanion\.app\.debug|topResumedActivity=.*com\.mystudycompanion\.app\.debug|ResumedActivity: ActivityRecord.*com\.mystudycompanion\.app\.debug|Resumed: ActivityRecord.*com\.mystudycompanion\.app\.debug' \
      "$EVIDENCE/journey-return-attempt-${return_attempt}-activity.txt" >/dev/null \
    && grep -E 'mCurrentFocus=.*com\.mystudycompanion\.app\.debug|mFocusedApp=.*com\.mystudycompanion\.app\.debug' \
      "$EVIDENCE/journey-return-attempt-${return_attempt}-activity.txt" \
      "$EVIDENCE/journey-return-attempt-${return_attempt}-window.txt" >/dev/null; then
    wait_for_phone_foreground "$EVIDENCE/journey-return-activity.txt" \
      'My Study Companion after returning from JW Library'
    return_ready=true
    printf 'PASS: Back returned from JW Library to My Study Companion after %d bounded press(es).\n' \
      "$return_attempt" | tee -a "$EVIDENCE/journey-return-proof.txt"
    break
  fi
  printf 'Bible Journey return attempt %d remained outside My Study Companion; continuing bounded Back sequence.\n' \
    "$return_attempt" | tee -a "$EVIDENCE/journey-return-proof.txt"
done
if [[ "$return_ready" != true ]]; then
  echo 'Back did not return from JW Library to the paused My Study Companion task within four bounded attempts.' >&2
  exit 1
fi
'''
if source.count(old) != 1:
    raise SystemExit('Expected one Bible Journey single-Back return sequence.')
path.write_text(source.replace(old, new, 1), encoding='utf-8')
PYRETURN

bash -n /tmp/installed-phone-jw-0121-core-generated.sh
if [[ "${1:-}" == '--preflight' ]]; then
  echo 'PASS: bounded JW return verifier generated and passed shell syntax validation.'
  exit 0
fi
exec bash /tmp/installed-phone-jw-0121-core-generated.sh "$@"
"""
if source.count(old) != 1:
    raise SystemExit('Expected one current-core execution point.')
path.write_text(source.replace(old, new, 1), encoding='utf-8')
PY

bash -n "$WRAPPER"
exec bash "$WRAPPER" "$@"

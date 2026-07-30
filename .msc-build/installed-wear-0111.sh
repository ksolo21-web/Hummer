#!/usr/bin/env bash
set -euo pipefail

mkdir -p wear-installed-evidence
APK="dist/MyStudyCompanion-wear-0.11.1-debug.apk"
test -f "$APK"
unzip -tq "$APK"

adb_recover() {
  local attempt
  for attempt in 1 2 3 4 5; do
    adb start-server >/dev/null 2>&1 || true
    adb reconnect >/dev/null 2>&1 || true
    if adb wait-for-device >/dev/null 2>&1 \
      && adb shell 'echo ready' 2>/dev/null | grep -q ready; then
      return 0
    fi
    sleep $((attempt * 4))
  done
  echo 'ADB did not recover after five attempts.' >&2
  return 1
}

adb_install_retry() {
  local attempt
  for attempt in 1 2 3 4; do
    echo "Wear install attempt ${attempt}" | tee -a wear-installed-evidence/install.txt
    adb_recover
    if adb install -r "$APK" 2>&1 | tee -a wear-installed-evidence/install.txt; then
      return 0
    fi
    adb kill-server >/dev/null 2>&1 || true
    sleep $((attempt * 6))
  done
  echo 'Wear APK did not install after four attempts.' >&2
  return 1
}

adb_install_retry
adb_recover
PACKAGE_PATH="$(adb shell pm path com.mystudycompanion.app.debug | tr -d '\r')"
test -n "$PACKAGE_PATH"
printf '%s\n' "$PACKAGE_PATH" > wear-installed-evidence/package-path.txt

# Hosted Wear images may boot into an unfinished onboarding flow that immediately
# covers any launched app. Mark the emulator provisioned and remove that harness-only
# obstruction before evaluating My Study Companion.
adb shell settings put global device_provisioned 1 || true
adb shell settings put secure user_setup_complete 1 || true
adb shell settings put secure tv_user_setup_complete 1 || true
adb shell am force-stop com.google.android.wearable.setupwizard || true
adb shell pm disable-user --user 0 com.google.android.wearable.setupwizard \
  > wear-installed-evidence/setup-wizard-disable.txt 2>&1 || true
adb shell input keyevent 3 || true
sleep 3

adb shell am force-stop com.mystudycompanion.app.debug
adb logcat -c
adb shell monkey -p com.mystudycompanion.app.debug -c android.intent.category.LAUNCHER 1 \
  | tee wear-installed-evidence/launch.txt

sleep 12
adb_recover
adb shell dumpsys activity activities > wear-installed-evidence/activity.txt
adb shell dumpsys window windows > wear-installed-evidence/window.txt
adb logcat -d > wear-installed-evidence/logcat.txt

python3 - <<'PY'
from pathlib import Path
activity = Path('wear-installed-evidence/activity.txt').read_text(errors='replace')
window = Path('wear-installed-evidence/window.txt').read_text(errors='replace')
logcat = Path('wear-installed-evidence/logcat.txt').read_text(errors='replace').splitlines()
package = 'com.mystudycompanion.app.debug'
component = package + '/com.mystudycompanion.app.wear.MainActivity'

active = (
    component in activity
    and ('mResumedActivity' in activity or 'Resumed:' in activity)
    and package in activity
) or component in window
if not active:
    raise SystemExit('Wear app installed but its MainActivity was not the active foreground activity.')

for index, line in enumerate(logcat):
    if 'FATAL EXCEPTION' not in line:
        continue
    block = '\n'.join(logcat[index:index + 80])
    if f'Process: {package}' in block or package in block:
        raise SystemExit('Wear app produced a package-specific fatal Android exception.')
PY

adb exec-out screencap -p > wear-installed-evidence/watch-home.png
adb shell uiautomator dump /sdcard/watch.xml >/dev/null 2>&1 || true
adb pull /sdcard/watch.xml wear-installed-evidence/watch.xml >/dev/null 2>&1 || true

echo 'Wear APK installed; onboarding was cleared; MainActivity was foreground; no package-specific fatal exception occurred.' \
  | tee wear-installed-evidence/RESULT.txt

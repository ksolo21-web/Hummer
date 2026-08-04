#!/usr/bin/env bash
set -euo pipefail

mkdir -p wear-installed-evidence
APK="dist/MyStudyCompanion-wear-0.11.1-debug.apk"
PACKAGE="com.mystudycompanion.app.debug"
COMPONENT="${PACKAGE}/com.mystudycompanion.app.wear.MainActivity"
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
PACKAGE_PATH="$(adb shell pm path "$PACKAGE" | tr -d '\r')"
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
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell input keyevent 3 || true
sleep 3

# Do not use Android monkey here. On hosted Wear images it can abort because an
# unrelated system service is ANR-ing, injecting zero launch events. Start the exact
# exported launcher activity directly and verify Android reports a successful start.
adb shell am force-stop "$PACKAGE"
adb logcat -c
: > wear-installed-evidence/launch.txt
launched=false
for attempt in 1 2 3; do
  adb_recover
  echo "Explicit MainActivity launch attempt ${attempt}" | tee -a wear-installed-evidence/launch.txt
  if adb shell am start -W -n "$COMPONENT" 2>&1 | tee -a wear-installed-evidence/launch.txt \
      | grep -q 'Status: ok'; then
    launched=true
    break
  fi
  sleep $((attempt * 4))
done
if [[ "$launched" != true ]]; then
  echo 'Android did not report a successful explicit Wear MainActivity launch.' >&2
  exit 1
fi

foreground=false
for attempt in 1 2 3 4 5 6; do
  sleep 3
  adb_recover
  adb shell dumpsys activity activities > wear-installed-evidence/activity.txt
  adb shell dumpsys window windows > wear-installed-evidence/window.txt
  if grep -E "mResumedActivity=.*${PACKAGE}/com\.mystudycompanion\.app\.wear\.MainActivity|Resumed: ActivityRecord.*${PACKAGE}/com\.mystudycompanion\.app\.wear\.MainActivity" \
      wear-installed-evidence/activity.txt >/dev/null \
      || grep -E "mCurrentFocus=.*${PACKAGE}/com\.mystudycompanion\.app\.wear\.MainActivity|mFocusedApp=.*${PACKAGE}/com\.mystudycompanion\.app\.wear\.MainActivity" \
      wear-installed-evidence/window.txt >/dev/null; then
    foreground=true
    break
  fi
  # A system overlay may briefly win focus during Wear boot. Relaunch the exact
  # component instead of treating that transient overlay as an app failure.
  adb shell am start -W -n "$COMPONENT" >> wear-installed-evidence/launch.txt 2>&1 || true
done

adb logcat -d > wear-installed-evidence/logcat.txt
if [[ "$foreground" != true ]]; then
  echo 'Wear app installed and explicitly launched, but MainActivity did not become foreground.' >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path
logcat = Path('wear-installed-evidence/logcat.txt').read_text(errors='replace').splitlines()
package = 'com.mystudycompanion.app.debug'
for index, line in enumerate(logcat):
    if 'FATAL EXCEPTION' not in line:
        continue
    block = '\n'.join(logcat[index:index + 80])
    if f'Process: {package}' in block:
        raise SystemExit('Wear app produced a package-specific fatal Android exception.')
PY

adb exec-out screencap -p > wear-installed-evidence/watch-home.png
adb shell uiautomator dump /sdcard/watch.xml >/dev/null 2>&1 || true
adb pull /sdcard/watch.xml wear-installed-evidence/watch.xml >/dev/null 2>&1 || true

echo 'Wear APK installed; onboarding was cleared; explicit MainActivity launch succeeded; MainActivity was foreground; no package-specific fatal exception occurred.' \
  | tee wear-installed-evidence/RESULT.txt

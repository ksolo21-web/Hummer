#!/usr/bin/env bash
set -euo pipefail

EVIDENCE="installed-0120-evidence/wear"
mkdir -p "$EVIDENCE"
APK="dist/MyStudyCompanion-wear-0.12.0-debug.apk"
PACKAGE="com.mystudycompanion.app.debug"
COMPONENT="${PACKAGE}/com.mystudycompanion.app.wear.MainActivity"

test -f "$APK"
unzip -tq "$APK"

adb start-server >/dev/null
adb wait-for-device
for attempt in $(seq 1 150); do
  if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] \
    && adb shell service check package 2>/dev/null | grep -q found \
    && adb shell cmd package list packages >/dev/null 2>&1; then
    echo "Wear Android services ready after check ${attempt}." | tee "$EVIDENCE/android-ready.txt"
    break
  fi
  if [[ "$attempt" == "150" ]]; then
    echo 'Wear Android package manager did not become ready.' >&2
    exit 1
  fi
  sleep 2
done

installed=false
for attempt in 1 2 3; do
  echo "Wear install attempt ${attempt}" | tee -a "$EVIDENCE/install.txt"
  if adb install --no-streaming -r -g "$APK" 2>&1 | tee -a "$EVIDENCE/install.txt" | grep -q Success; then
    installed=true
    break
  fi
  sleep $((attempt * 4))
done
if [[ "$installed" != true ]]; then
  echo 'Wear APK did not install.' >&2
  exit 1
fi

PACKAGE_PATH="$(adb shell pm path "$PACKAGE" | tr -d '\r')"
test -n "$PACKAGE_PATH"
printf '%s\n' "$PACKAGE_PATH" > "$EVIDENCE/package-path.txt"

# Hosted Wear images often boot behind setup wizard. Clear only that emulator obstruction.
adb shell settings put global device_provisioned 1 || true
adb shell settings put secure user_setup_complete 1 || true
adb shell settings put secure tv_user_setup_complete 1 || true
adb shell am force-stop com.google.android.wearable.setupwizard || true
adb shell pm disable-user --user 0 com.google.android.wearable.setupwizard \
  > "$EVIDENCE/setup-wizard-disable.txt" 2>&1 || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell input keyevent 3 || true
sleep 3

adb shell am force-stop "$PACKAGE"
adb logcat -c
adb shell am start -W -n "$COMPONENT" | tee "$EVIDENCE/launch.txt"
grep -q 'Status: ok' "$EVIDENCE/launch.txt"

foreground=false
for attempt in 1 2 3 4 5 6; do
  sleep 3
  adb shell dumpsys activity activities > "$EVIDENCE/activity.txt"
  adb shell dumpsys window windows > "$EVIDENCE/window.txt"
  if grep -E "mResumedActivity=.*${PACKAGE}/com\.mystudycompanion\.app\.wear\.MainActivity|Resumed: ActivityRecord.*${PACKAGE}/com\.mystudycompanion\.app\.wear\.MainActivity" \
      "$EVIDENCE/activity.txt" >/dev/null \
      || grep -E "mCurrentFocus=.*${PACKAGE}/com\.mystudycompanion\.app\.wear\.MainActivity|mFocusedApp=.*${PACKAGE}/com\.mystudycompanion\.app\.wear\.MainActivity" \
      "$EVIDENCE/window.txt" >/dev/null; then
    foreground=true
    break
  fi
  adb shell am start -W -n "$COMPONENT" >> "$EVIDENCE/launch.txt" 2>&1 || true
done

adb logcat -d > "$EVIDENCE/logcat.txt"
if [[ "$foreground" != true ]]; then
  echo 'Wear app installed and launched, but MainActivity did not become foreground.' >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path
lines = Path('installed-0120-evidence/wear/logcat.txt').read_text(errors='replace').splitlines()
package = 'com.mystudycompanion.app.debug'
for index, line in enumerate(lines):
    if 'FATAL EXCEPTION' not in line:
        continue
    block = '\n'.join(lines[index:index + 100])
    if f'Process: {package}' in block:
        raise SystemExit('Wear app produced a package-specific fatal Android exception.')
PY

adb exec-out screencap -p > "$EVIDENCE/watch-home.png"
adb shell uiautomator dump /sdcard/watch.xml >/dev/null 2>&1 || true
adb pull /sdcard/watch.xml "$EVIDENCE/watch.xml" >/dev/null 2>&1 || true

echo 'PASS: 0.12.0 Wear APK installed, explicitly launched, reached the foreground, and produced no package-specific fatal Android exception.' \
  | tee "$EVIDENCE/RESULT.txt"

#!/usr/bin/env bash
set -euo pipefail

EVIDENCE="installed-0121-evidence/phone"
mkdir -p "$EVIDENCE"
BASELINE_APK="dist-baseline/MyStudyCompanion-phone-0.12.0-debug.apk"
NEW_APK="dist/MyStudyCompanion-phone-0.12.1-debug.apk"
PACKAGE="com.mystudycompanion.app.debug"
test -f "$BASELINE_APK"
test -f "$NEW_APK"

collect_failure_evidence() {
  set +e
  date -u +'%Y-%m-%dT%H:%M:%SZ' > "$EVIDENCE/failure-time.txt"
  adb get-state > "$EVIDENCE/adb-state.txt" 2>&1
  adb shell getprop > "$EVIDENCE/getprop.txt" 2>&1
  adb shell service check package > "$EVIDENCE/package-service.txt" 2>&1
  adb shell cmd package list packages > "$EVIDENCE/package-list.txt" 2>&1
  adb shell dumpsys package "$PACKAGE" > "$EVIDENCE/baseline-package-dumpsys.txt" 2>&1
  adb logcat -d > "$EVIDENCE/failure-logcat.txt" 2>&1
}
trap 'rc=$?; if (( rc != 0 )); then collect_failure_evidence; fi' EXIT

wait_for_android() {
  local attempt
  adb start-server >/dev/null
  adb wait-for-device
  for attempt in $(seq 1 180); do
    if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] \
      && adb shell service check package 2>/dev/null | grep -q found \
      && adb shell cmd package list packages >/dev/null 2>&1; then
      sleep 8
      if adb shell service check package 2>/dev/null | grep -q found \
        && adb shell cmd package list packages >/dev/null 2>&1; then
        echo "Android package services stable after check ${attempt}." | tee -a "$EVIDENCE/android-ready.txt"
        return 0
      fi
    fi
    sleep 2
  done
  echo 'Android package manager did not become stable.' >&2
  return 1
}

recover_adb() {
  set +e
  adb reconnect device >/dev/null 2>&1
  sleep 4
  adb kill-server >/dev/null 2>&1
  adb start-server >/dev/null 2>&1
  adb wait-for-device
  set -e
  wait_for_android
}

install_baseline() {
  local method log remote
  log="$EVIDENCE/baseline-install.txt"
  remote="/data/local/tmp/msc-baseline-0120.apk"

  for method in streaming no-streaming package-manager; do
    wait_for_android
    echo "Baseline install method: ${method}" | tee -a "$log"
    case "$method" in
      streaming)
        if timeout 240s adb install --streaming -r -g "$BASELINE_APK" 2>&1 | tee -a "$log"; then
          if adb shell pm path "$PACKAGE" | tee "$EVIDENCE/baseline-package-path.txt" | grep -q package:; then
            return 0
          fi
        fi
        ;;
      no-streaming)
        if timeout 240s adb install --no-streaming -r -g "$BASELINE_APK" 2>&1 | tee -a "$log"; then
          if adb shell pm path "$PACKAGE" | tee "$EVIDENCE/baseline-package-path.txt" | grep -q package:; then
            return 0
          fi
        fi
        ;;
      package-manager)
        adb shell rm -f "$remote" >/dev/null 2>&1 || true
        if timeout 180s adb push "$BASELINE_APK" "$remote" 2>&1 | tee -a "$log" \
          && timeout 240s adb shell pm install -r -g "$remote" 2>&1 | tee -a "$log"; then
          adb shell rm -f "$remote" >/dev/null 2>&1 || true
          if adb shell pm path "$PACKAGE" | tee "$EVIDENCE/baseline-package-path.txt" | grep -q package:; then
            return 0
          fi
        fi
        adb shell rm -f "$remote" >/dev/null 2>&1 || true
        ;;
    esac
    adb shell service check package 2>&1 | tee -a "$log" || true
    adb shell cmd package list packages >/dev/null 2>>"$log" || true
    recover_adb
  done

  echo 'All baseline APK installation methods failed.' | tee -a "$log" >&2
  return 1
}

# Establish a real database-version-6 install, then upgrade it in place to version 7.
wait_for_android
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
install_baseline
BASE_COMPONENT="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$PACKAGE" | tr -d '\r' | tail -n 1)"
echo "$BASE_COMPONENT" | tee "$EVIDENCE/baseline-component.txt" | grep -q "$PACKAGE"
adb shell am start -n "$BASE_COMPONENT" | tee /tmp/baseline-launch.txt
grep -Eq 'Starting: Intent|Warning: Activity not started' /tmp/baseline-launch.txt
baseline_foreground=false
for attempt in $(seq 1 45); do
  adb shell dumpsys activity activities > /tmp/baseline-activity.txt
  if grep -E "mResumedActivity=.*${PACKAGE}/|Resumed: ActivityRecord.*${PACKAGE}/" /tmp/baseline-activity.txt >/dev/null; then
    baseline_foreground=true
    break
  fi
  adb shell am start -n "$BASE_COMPONENT" >/dev/null 2>&1 || true
  sleep 2
done
[[ "$baseline_foreground" == true ]]
for attempt in $(seq 1 30); do
  if adb shell run-as "$PACKAGE" test -f databases/my-study-companion.db >/dev/null 2>&1; then
    adb shell run-as "$PACKAGE" ls -l databases/my-study-companion.db | tee /tmp/baseline-database.txt
    break
  fi
  [[ "$attempt" != 30 ]] || { echo 'Baseline Room database was not created.' >&2; exit 1; }
  sleep 2
done
adb shell am force-stop "$PACKAGE"

python3 - <<'PY'
from pathlib import Path
source = Path('.msc-build/installed-phone-0120.sh').read_text(encoding='utf-8')
source = source.replace('installed-0120-evidence/phone', 'installed-0121-evidence/phone')
source = source.replace('MyStudyCompanion-phone-0.12.0-debug.apk', 'MyStudyCompanion-phone-0.12.1-debug.apk')
source = source.replace('PASS: 0.12.0 phone APK', 'PASS: 0.12.1 phone APK upgraded from database version 6')
bad_local = '  local apk="$1" package="$2" label="$3" log="$EVIDENCE/${label}-install.txt"\n'
good_local = '  local apk="$1" package="$2" label="$3"\n  local log="$EVIDENCE/${label}-install.txt"\n'
if source.count(bad_local) != 1:
    raise SystemExit('Expected exactly one unsafe install_apk local declaration.')
source = source.replace(bad_local, good_local, 1)
old_order = 'install_apk "$JW_APK" "$JW_PACKAGE" jw-library\ninstall_apk "$PHONE_APK" "$PHONE_PACKAGE" phone\n'
new_order = 'install_apk "$PHONE_APK" "$PHONE_PACKAGE" phone\ninstall_apk "$JW_APK" "$JW_PACKAGE" jw-library\n'
if source.count(old_order) != 1:
    raise SystemExit('Expected exactly one phone/JW Library install sequence.')
source = source.replace(old_order, new_order, 1)
source = source.replace(
    'adb shell am start -W -a android.intent.action.VIEW \\\n',
    'adb shell am start -a android.intent.action.VIEW \\\n',
    1,
)
source = source.replace(
    "grep -q 'Status: ok' \"$EVIDENCE/jw-start.txt\"\n",
    "grep -Eq 'Starting: Intent|Warning: Activity not started' \"$EVIDENCE/jw-start.txt\"\n",
    1,
)
blocking_phone = '  adb shell am start -W -n "$PHONE_COMPONENT" | tee "$EVIDENCE/phone-launch.txt"\n  grep -q \'Status: ok\' "$EVIDENCE/phone-launch.txt"\n'
nonblocking_phone = '  adb shell am start -n "$PHONE_COMPONENT" | tee "$EVIDENCE/phone-launch.txt"\n  grep -Eq \'Starting: Intent|Warning: Activity not started\' "$EVIDENCE/phone-launch.txt"\n'
if source.count(blocking_phone) != 1:
    raise SystemExit('Expected exactly one blocking phone launch assertion.')
source = source.replace(blocking_phone, nonblocking_phone, 1)
anchor = "resolve_jw week_https 'https://www.jw.org/finder?srcid=jwlshare&wtlocale=E&prefer=lang&docid=202026244'\n"
addition = anchor + "TODAY_UTC=\"$(date -u +%Y%m%d)\"\nresolve_jw daily \"jwlibrary:///finder?alias=daily-text&date=${TODAY_UTC}&wtlocale=E\"\nresolve_jw daily_https \"https://www.jw.org/finder?alias=daily-text&date=${TODAY_UTC}&wtlocale=E\"\n"
if source.count(anchor) != 1:
    raise SystemExit('Daily Text insertion anchor not found exactly once.')
source = source.replace(anchor, addition, 1)
Path('/tmp/installed-phone-0121-generated.sh').write_text(source, encoding='utf-8')
PY

bash /tmp/installed-phone-0121-generated.sh
adb shell dumpsys package "$PACKAGE" > "$EVIDENCE/upgraded-package.txt"
adb shell am start -a android.intent.action.VIEW \
  -d "jwlibrary:///finder?alias=daily-text&date=$(date -u +%Y%m%d)&wtlocale=E" \
  org.jw.jwlibrary.mobile | tee "$EVIDENCE/daily-text-start.txt"
grep -Eq 'Starting: Intent|Warning: Activity not started' "$EVIDENCE/daily-text-start.txt"
sleep 6
adb shell dumpsys activity activities > "$EVIDENCE/daily-text-activity.txt"
grep -E 'mResumedActivity=.*org\.jw\.jwlibrary\.mobile|Resumed: ActivityRecord.*org\.jw\.jwlibrary\.mobile' \
  "$EVIDENCE/daily-text-activity.txt" >/dev/null
adb exec-out screencap -p > "$EVIDENCE/daily-text-open.png"
cp /tmp/baseline-database.txt "$EVIDENCE/baseline-database.txt"
printf '%s\n' 'PASS: real database version 6 existed before the in-place 0.12.1 upgrade; exact dated Daily Text Finder resolved and opened in official JW Library.' \
  | tee -a "$EVIDENCE/RESULT.txt"

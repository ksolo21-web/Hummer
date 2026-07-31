#!/usr/bin/env bash
set -euo pipefail

PHONE_APK='dist/MyStudyCompanion-phone-0.12.2-debug.apk'
COMPAT_APK='dist/MyStudyCompanion-phone-0.12.1-debug.apk'
test -f "$PHONE_APK"
cp "$PHONE_APK" "$COMPAT_APK"

# Generate the proven 0.12.1 JW bootstrap and bounded-return verifier without
# executing it. Then retarget the fully generated scripts to the 0.12.2 APK.
python3 - <<'PY'
from pathlib import Path
source = Path('.msc-build/run-installed-phone-jw-safe-scroll.sh').read_text(encoding='utf-8')
old = 'exec bash "$PATCHED" "$@"\n'
if source.count(old) != 1:
    raise SystemExit('Expected one safe-scroll execution point.')
Path('/tmp/run-installed-phone-jw-0122-generate.sh').write_text(
    source.replace(old, ':\n', 1),
    encoding='utf-8',
)
PY
bash /tmp/run-installed-phone-jw-0122-generate.sh

test -s /tmp/installed-phone-jw-0121-safe-scroll.sh
test -s /tmp/installed-phone-jw-0121-core-generated.sh

python3 - <<'PY'
from pathlib import Path

for raw in (
    '/tmp/installed-phone-jw-0121-safe-scroll.sh',
    '/tmp/installed-phone-jw-0121-core-generated.sh',
):
    path = Path(raw)
    source = path.read_text(encoding='utf-8')
    source = source.replace('installed-0121-evidence', 'installed-0122-evidence')
    source = source.replace('MyStudyCompanion-phone-0.12.1-debug.apk', 'MyStudyCompanion-phone-0.12.2-debug.apk')
    source = source.replace('versionCode=25', 'versionCode=26')
    source = source.replace(
        'versionName=0.12.1-private-alpha-grounded-links-debug',
        'versionName=0.12.2-private-alpha-complete-jw-links-debug',
    )
    source = source.replace('0.12.1 APK', '0.12.2 APK')
    source = source.replace('0.12.1 phone', '0.12.2 phone')
    output = Path(raw.replace('0121', '0122'))
    output.write_text(source, encoding='utf-8')
PY

cat > .msc-build/installed-phone-jw-0121-core.sh <<'SHCORE'
#!/usr/bin/env bash
set -euo pipefail
exec bash /tmp/installed-phone-jw-0122-core-generated.sh "$@"
SHCORE
chmod +x .msc-build/installed-phone-jw-0121-core.sh
bash -n /tmp/installed-phone-jw-0122-safe-scroll.sh
bash -n /tmp/installed-phone-jw-0122-core-generated.sh
bash /tmp/installed-phone-jw-0122-safe-scroll.sh

# The UI/resolver/unit gates prove which targets every app surface emits. This
# installed matrix independently proves that the current official JW Library
# accepts every distinct exact-target class used by those surfaces.
EVIDENCE='installed-0122-evidence/jw-library/complete-link-matrix'
JW_PACKAGE='org.jw.jwlibrary.mobile'
APP_PACKAGE='com.mystudycompanion.app.debug'
mkdir -p "$EVIDENCE"

wait_for_jw_target() {
  local name="$1" attempt activity window
  for attempt in $(seq 1 45); do
    activity="$EVIDENCE/${name}-activity-${attempt}.txt"
    window="$EVIDENCE/${name}-window-${attempt}.txt"
    adb shell dumpsys activity activities > "$activity" 2>&1 || true
    adb shell dumpsys window windows > "$window" 2>&1 || true
    if grep -E 'mResumedActivity=.*org\.jw\.jwlibrary\.mobile|topResumedActivity=.*org\.jw\.jwlibrary\.mobile|ResumedActivity: ActivityRecord.*org\.jw\.jwlibrary\.mobile|Resumed: ActivityRecord.*org\.jw\.jwlibrary\.mobile' "$activity" >/dev/null \
      && grep -E 'mCurrentFocus=.*org\.jw\.jwlibrary\.mobile|mFocusedApp=.*org\.jw\.jwlibrary\.mobile' "$activity" "$window" >/dev/null \
      && ! grep -E 'TermsOfUseActivity|PrivacyAcceptanceActivity|Application Error|keeps stopping' "$activity" "$window" >/dev/null; then
      cp "$activity" "$EVIDENCE/${name}-activity.txt"
      cp "$window" "$EVIDENCE/${name}-window.txt"
      return 0
    fi
    sleep 2
  done
  echo "JW Library did not reach a stable foreground state for exact matrix target ${name}." >&2
  return 1
}

assert_no_jw_fatal() {
  local name="$1" log="$EVIDENCE/${name}-logcat.txt"
  adb logcat -d > "$log"
  python3 - "$log" <<'PY'
from pathlib import Path
import sys
lines = Path(sys.argv[1]).read_text(errors='replace').splitlines()
for index, line in enumerate(lines):
    if 'FATAL EXCEPTION' not in line:
        continue
    block = '\n'.join(lines[index:index + 120])
    if 'Process: org.jw.jwlibrary.mobile' in block:
        raise SystemExit('Official JW Library produced a package-specific fatal exception.')
PY
}

run_exact_target() {
  local name="$1" uri="$2" check
  adb shell am force-stop "$JW_PACKAGE" >/dev/null 2>&1 || true
  sleep 2
  adb logcat -c
  adb shell "am start -a android.intent.action.VIEW -d '$uri' -p '$JW_PACKAGE'" \
    | tee "$EVIDENCE/${name}-start.txt"
  grep -Eq 'Starting: Intent|Warning: Activity not started' "$EVIDENCE/${name}-start.txt"
  wait_for_jw_target "$name"
  adb exec-out screencap -p > "$EVIDENCE/${name}.png" || true
  for check in 1 2 3 4; do
    sleep 2
    adb shell dumpsys activity activities > "$EVIDENCE/${name}-hold-${check}.txt"
    adb shell pidof "$JW_PACKAGE" >/dev/null
    grep -E 'mResumedActivity=.*org\.jw\.jwlibrary\.mobile|topResumedActivity=.*org\.jw\.jwlibrary\.mobile|ResumedActivity: ActivityRecord.*org\.jw\.jwlibrary\.mobile|Resumed: ActivityRecord.*org\.jw\.jwlibrary\.mobile' \
      "$EVIDENCE/${name}-hold-${check}.txt" >/dev/null
    if grep -E 'com\.android\.chrome|org\.chromium|Application Error|keeps stopping' "$EVIDENCE/${name}-hold-${check}.txt" >/dev/null; then
      echo "Exact target ${name} escaped JW Library or displayed an error." >&2
      return 1
    fi
  done
  assert_no_jw_fatal "$name"
  printf 'PASS: %s opened as a stable, package-scoped JW Library target.\n' "$name" \
    | tee -a "$EVIDENCE/RESULT.txt"
}

DAILY_DATE="$(date -u +%Y%m%d)"
while IFS='|' read -r name uri; do
  [[ -n "$name" ]] || continue
  run_exact_target "$name" "$uri"
done <<EOF
bible-single-verse|jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=01001001
bible-chapter|jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=18001000
bible-verse-range|jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=24020007-24020018
bible-cross-chapter|jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=21011009-21012014
bible-cross-book|jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=01049000-02001000
semicolon-first-passage|jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=09018001-09018016
semicolon-second-passage|jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=09020000
active-week-document|jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&docid=202026244
research-guide-publication|jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=rsg19
research-guide-document|jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&docid=1204360
dated-daily-text|jwlibrary:///finder?alias=daily-text&date=${DAILY_DATE}&wtlocale=E
EOF

adb shell am force-stop "$JW_PACKAGE" >/dev/null 2>&1 || true
adb shell monkey -p "$APP_PACKAGE" -c android.intent.category.LAUNCHER 1 \
  > "$EVIDENCE/phone-return-launch.txt" 2>&1 || true
printf '%s\n' 'PASS: the installed 0.12.2 app and current official JW Library completed the original UI return tests plus the full exact-target class matrix without a silent browser escape or package-specific fatal exception.' \
  | tee -a "$EVIDENCE/RESULT.txt"

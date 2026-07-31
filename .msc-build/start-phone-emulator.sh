#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="${1:-installed-0121-evidence/emulator}"
AVD_NAME="${MSC_AVD_NAME:-msc-phone-api33}"
SYSTEM_IMAGE="system-images;android-33;google_apis;x86_64"
mkdir -p "$EVIDENCE_DIR"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$SDK_ROOT"
SDKMANAGER="$(find "$SDK_ROOT/cmdline-tools" -path '*/bin/sdkmanager' -type f 2>/dev/null | sort -V | tail -n 1)"
AVDMANAGER="$(find "$SDK_ROOT/cmdline-tools" -path '*/bin/avdmanager' -type f 2>/dev/null | sort -V | tail -n 1)"
EMULATOR="$SDK_ROOT/emulator/emulator"
test -x "$SDKMANAGER"
test -x "$AVDMANAGER"

ACCEL_MODE=off
if [[ -e /dev/kvm ]]; then
  sudo chown root:kvm /dev/kvm >/dev/null 2>&1 || true
  sudo chmod 0666 /dev/kvm >/dev/null 2>&1 || true
  if [[ -r /dev/kvm && -w /dev/kvm ]]; then
    ACCEL_MODE=on
  fi
fi
{
  printf 'accel_mode=%s\n' "$ACCEL_MODE"
  ls -l /dev/kvm 2>&1 || true
  id
} > "$EVIDENCE_DIR/kvm.txt"

# Software-only x86_64 emulation has repeatedly stalled or left ADB offline on
# hosted runners. The installed-runtime verification requires a usable emulator,
# so fail immediately rather than waiting twenty minutes on a dead device.
if [[ "$ACCEL_MODE" != on ]]; then
  echo 'KVM acceleration is unavailable on this runner.' | tee "$EVIDENCE_DIR/kvm-failure.txt" >&2
  exit 1
fi

yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "platform-tools" "emulator" "$SYSTEM_IMAGE"
export PATH="$SDK_ROOT/platform-tools:$SDK_ROOT/emulator:$PATH"
if [[ -n "${GITHUB_PATH:-}" ]]; then
  printf '%s\n%s\n' "$SDK_ROOT/platform-tools" "$SDK_ROOT/emulator" >> "$GITHUB_PATH"
fi
command -v adb

AVD_HOME="$HOME/.android/avd"
AVD_PATH="$AVD_HOME/${AVD_NAME}.avd"
AVD_INI="$AVD_HOME/${AVD_NAME}.ini"
export ANDROID_AVD_HOME="$AVD_HOME"
export ANDROID_SDK_HOME="$HOME"
mkdir -p "$AVD_HOME"
rm -rf "$AVD_PATH" "$AVD_INI"
env | sort > "$EVIDENCE_DIR/emulator-environment.txt"

# Use avdmanager's normal registration path. A previous custom --path flow
# created the AVD directory but produced an emulator process that could not see
# the hand-written registration file and exited with "Unknown AVD name".
printf 'no\n' > "$EVIDENCE_DIR/avd-answer.txt"
set +e
"$AVDMANAGER" create avd --force --name "$AVD_NAME" \
  --package "$SYSTEM_IMAGE" --device pixel_6 \
  < "$EVIDENCE_DIR/avd-answer.txt" > "$EVIDENCE_DIR/avdmanager-create.txt" 2>&1
AVD_CREATE_STATUS=$?
set -e
cat "$EVIDENCE_DIR/avdmanager-create.txt"
if (( AVD_CREATE_STATUS != 0 )); then
  printf 'avdmanager exited with status %d.\n' "$AVD_CREATE_STATUS" | tee "$EVIDENCE_DIR/avdmanager-failure.txt" >&2
  exit "$AVD_CREATE_STATUS"
fi

test -d "$AVD_PATH" || { echo "Missing AVD directory: $AVD_PATH" | tee "$EVIDENCE_DIR/avd-path-failure.txt" >&2; exit 1; }
test -f "$AVD_INI" || { echo "Missing AVD registration: $AVD_INI" | tee "$EVIDENCE_DIR/avd-ini-failure.txt" >&2; exit 1; }
"$EMULATOR" -list-avds > "$EVIDENCE_DIR/registered-avds.txt"
cat "$EVIDENCE_DIR/registered-avds.txt"
grep -Fx "$AVD_NAME" "$EVIDENCE_DIR/registered-avds.txt"

CONFIG="$AVD_PATH/config.ini"
test -f "$CONFIG"
cat >> "$CONFIG" <<'EOF_CONFIG'
hw.ramSize=2048
vm.heapSize=512
disk.dataPartition.size=6G
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.keyboard=yes
showDeviceFrame=no
EOF_CONFIG

adb kill-server >/dev/null 2>&1 || true
nohup env ANDROID_AVD_HOME="$AVD_HOME" ANDROID_SDK_HOME="$HOME" \
  "$EMULATOR" -avd "$AVD_NAME" \
  -no-window -noaudio -no-boot-anim -camera-back none \
  -gpu swiftshader_indirect -no-snapshot -no-snapshot-save -wipe-data -no-metrics \
  -accel "$ACCEL_MODE" -memory 2048 -cores 2 \
  > "$EVIDENCE_DIR/emulator.log" 2>&1 &
EMULATOR_PID=$!
echo "$EMULATOR_PID" > "$EVIDENCE_DIR/emulator.pid"
sleep 5
if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
  tail -n 300 "$EVIDENCE_DIR/emulator.log" > "$EVIDENCE_DIR/emulator-early-exit.txt" || true
  echo 'Emulator process exited before Android boot began.' >&2
  exit 1
fi
if grep -Eqi 'Unknown AVD name|PANIC:|cannot find AVD|ERROR +\|.*AVD' "$EVIDENCE_DIR/emulator.log"; then
  tail -n 300 "$EVIDENCE_DIR/emulator.log" > "$EVIDENCE_DIR/emulator-fatal-start.txt" || true
  echo 'Emulator reported a fatal AVD startup error.' >&2
  exit 1
fi

adb start-server >/dev/null
ready_streak=0
for attempt in $(seq 1 450); do
  if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
    tail -n 300 "$EVIDENCE_DIR/emulator.log" > "$EVIDENCE_DIR/emulator-unexpected-exit.txt" || true
    echo 'Emulator process exited before service readiness.' >&2
    exit 1
  fi
  if grep -Eqi 'Unknown AVD name|PANIC:|cannot find AVD|ERROR +\|.*AVD' "$EVIDENCE_DIR/emulator.log"; then
    tail -n 300 "$EVIDENCE_DIR/emulator.log" > "$EVIDENCE_DIR/emulator-fatal-runtime.txt" || true
    echo 'Emulator reported a fatal AVD error before Android became ready.' >&2
    exit 1
  fi

  state="$(adb get-state 2>/dev/null || true)"
  boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  package_ready=false
  input_ready=false
  settings_ready=false
  activity_ready=false
  [[ "$state" == device && "$boot" == 1 ]] && \
    adb shell service check package 2>/dev/null | grep -q found && \
    adb shell cmd package list packages >/dev/null 2>&1 && package_ready=true
  [[ "$state" == device && "$boot" == 1 ]] && \
    adb shell service check input 2>/dev/null | grep -q found && \
    adb shell input keyevent 82 >/dev/null 2>&1 && input_ready=true
  [[ "$state" == device && "$boot" == 1 ]] && \
    adb shell service check settings 2>/dev/null | grep -q found && \
    adb shell settings get global airplane_mode_on >/dev/null 2>&1 && settings_ready=true
  [[ "$state" == device && "$boot" == 1 ]] && \
    adb shell service check activity 2>/dev/null | grep -q found && \
    adb shell dumpsys activity activities >/dev/null 2>&1 && activity_ready=true

  if [[ "$package_ready" == true && "$input_ready" == true && "$settings_ready" == true && "$activity_ready" == true ]]; then
    ready_streak=$((ready_streak + 1))
    printf 'Stable Android service check %d/3 at attempt %d.\n' "$ready_streak" "$attempt" | tee -a "$EVIDENCE_DIR/readiness.txt"
    if [[ "$ready_streak" -ge 3 ]]; then
      adb shell settings put global window_animation_scale 0
      adb shell settings put global transition_animation_scale 0
      adb shell settings put global animator_duration_scale 0
      adb shell getprop > "$EVIDENCE_DIR/getprop-ready.txt"
      adb shell service list > "$EVIDENCE_DIR/services-ready.txt"
      adb shell cmd package list packages > "$EVIDENCE_DIR/packages-ready.txt"
      printf '%s\n' 'PASS: SDK-managed AVD registration was valid, KVM acceleration was active, boot completed, and input, settings, activity, and package services were responsive for three consecutive checks.' | tee "$EVIDENCE_DIR/RESULT.txt"
      exit 0
    fi
  else
    ready_streak=0
  fi

  if (( attempt % 30 == 0 )); then
    printf 'Waiting for stable Android services: attempt %d/450, state=%s boot=%s package=%s input=%s settings=%s activity=%s\n' \
      "$attempt" "$state" "$boot" "$package_ready" "$input_ready" "$settings_ready" "$activity_ready" \
      | tee -a "$EVIDENCE_DIR/readiness.txt"
    adb reconnect offline >/dev/null 2>&1 || true
  fi
  sleep 2
done

adb shell getprop > "$EVIDENCE_DIR/getprop-timeout.txt" 2>&1 || true
adb shell service list > "$EVIDENCE_DIR/services-timeout.txt" 2>&1 || true
tail -n 300 "$EVIDENCE_DIR/emulator.log" > "$EVIDENCE_DIR/emulator-tail-timeout.txt" || true
echo 'Emulator did not reach stable service readiness.' >&2
exit 1

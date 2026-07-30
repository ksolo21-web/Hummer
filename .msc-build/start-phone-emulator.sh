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

if [[ -e /dev/kvm ]]; then
  sudo chown root:kvm /dev/kvm || true
  sudo chmod 0666 /dev/kvm || true
fi

yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "platform-tools" "emulator" "$SYSTEM_IMAGE"

echo no | "$AVDMANAGER" create avd --force --name "$AVD_NAME" --package "$SYSTEM_IMAGE" --device pixel_6
CONFIG="$HOME/.android/avd/${AVD_NAME}.avd/config.ini"
cat >> "$CONFIG" <<'EOF'
hw.ramSize=4096
vm.heapSize=768
disk.dataPartition.size=8G
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.keyboard=yes
showDeviceFrame=no
EOF

adb kill-server >/dev/null 2>&1 || true
nohup "$EMULATOR" -avd "$AVD_NAME" \
  -no-window -noaudio -no-boot-anim -camera-back none \
  -gpu swiftshader_indirect -no-snapshot -no-snapshot-save -wipe-data \
  > "$EVIDENCE_DIR/emulator.log" 2>&1 &
echo $! > "$EVIDENCE_DIR/emulator.pid"

adb start-server >/dev/null
ready_streak=0
for attempt in $(seq 1 600); do
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
      printf '%s\n' 'PASS: emulator reported boot complete and input, settings, activity, and package services were responsive for three consecutive checks.' | tee "$EVIDENCE_DIR/RESULT.txt"
      exit 0
    fi
  else
    ready_streak=0
  fi

  if (( attempt % 30 == 0 )); then
    printf 'Waiting for stable Android services: attempt %d/600, state=%s boot=%s package=%s input=%s settings=%s activity=%s\n' \
      "$attempt" "$state" "$boot" "$package_ready" "$input_ready" "$settings_ready" "$activity_ready" \
      | tee -a "$EVIDENCE_DIR/readiness.txt"
    adb reconnect >/dev/null 2>&1 || true
  fi
  sleep 2
done

adb shell getprop > "$EVIDENCE_DIR/getprop-timeout.txt" 2>&1 || true
adb shell service list > "$EVIDENCE_DIR/services-timeout.txt" 2>&1 || true
tail -n 300 "$EVIDENCE_DIR/emulator.log" > "$EVIDENCE_DIR/emulator-tail-timeout.txt" || true
echo 'Emulator did not reach stable service readiness.' >&2
exit 1

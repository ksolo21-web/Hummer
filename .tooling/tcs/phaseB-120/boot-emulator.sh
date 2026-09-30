#!/usr/bin/env bash
set -euo pipefail
sudo chmod 666 /dev/kvm
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export ANDROID_AVD_HOME="$RUNNER_TEMP/territory-phase-b-avd"
mkdir -p "$ANDROID_AVD_HOME"
AVDMANAGER="$(command -v avdmanager || find "$ANDROID_HOME/cmdline-tools" -path '*/bin/avdmanager' -type f | sort -V | tail -n1)"
echo no | "$AVDMANAGER" create avd --force --name territory_phase_b --package 'system-images;android-35;google_apis;x86_64' --device 'pixel_6' > evidence/avd-create.log 2>&1
"$ANDROID_HOME/emulator/emulator" -avd territory_phase_b -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none -memory 4096 -accel on > evidence/emulator.log 2>&1 &
echo $! > evidence/emulator.pid
adb wait-for-device
booted=false
for i in $(seq 1 180); do
  if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then booted=true; break; fi
  sleep 2
done
[[ "$booted" == true ]]
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

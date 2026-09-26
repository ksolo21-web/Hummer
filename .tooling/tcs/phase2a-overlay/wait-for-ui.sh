#!/bin/sh
set -eu

REMOTE_NAME="$1"
LOCAL_PATH="$2"
shift 2

attempt=0
while [ "$attempt" -lt 20 ]; do
  attempt=$((attempt + 1))
  if adb shell uiautomator dump "/sdcard/$REMOTE_NAME" >/dev/null 2>&1; then
    if adb pull "/sdcard/$REMOTE_NAME" "$LOCAL_PATH" >/dev/null 2>&1; then
      ready=1
      for expected in "$@"; do
        if ! grep -F "$expected" "$LOCAL_PATH" >/dev/null 2>&1; then
          ready=0
          break
        fi
      done
      if [ "$ready" -eq 1 ]; then
        echo "UI_READY=$REMOTE_NAME attempts=$attempt"
        exit 0
      fi
    fi
  fi
  sleep 2
done

echo "UI_READY_TIMEOUT=$REMOTE_NAME" >&2
adb shell dumpsys activity top || true
exit 1

#!/bin/sh
set -eu

REMOTE_NAME="$1"
LOCAL_PATH="$2"
DIRECTION="$3"
shift 3

attempt=0
while [ "$attempt" -lt 14 ]; do
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
        echo "UI_SCROLL_READY=$REMOTE_NAME attempts=$attempt direction=$DIRECTION"
        exit 0
      fi
    fi
  fi

  if [ "$DIRECTION" = "UP" ]; then
    adb shell input swipe 540 1900 540 780 320 >/dev/null
  elif [ "$DIRECTION" = "DOWN" ]; then
    adb shell input swipe 540 780 540 1900 320 >/dev/null
  else
    echo "Unsupported scroll direction: $DIRECTION" >&2
    exit 2
  fi
  sleep 1.5
done

echo "UI_SCROLL_TIMEOUT=$REMOTE_NAME direction=$DIRECTION" >&2
exit 1

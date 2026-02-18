#!/usr/bin/env bash
set -euo pipefail

REQUIRE_DEVICE=false
for arg in "$@"; do
  case "$arg" in
    --require-device)
      REQUIRE_DEVICE=true
      ;;
    *)
      echo "Unknown argument: $arg"
      exit 1
      ;;
  esac
done

APP_ID="${APP_ID:-com.imhere.app}"
APP_ACTIVITY="${APP_ACTIVITY:-com.imhere.app.MainActivity}"
APK_PATH="${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}"
ADB_BIN="${ADB_BIN:-adb}"
LOG_FILE="${LOG_FILE:-/tmp/imhere-runtime-smoke.log}"

if ! command -v "$ADB_BIN" >/dev/null 2>&1; then
  echo "adb not found: $ADB_BIN"
  if [[ "$REQUIRE_DEVICE" == true ]]; then
    exit 1
  fi
  echo "Skipping runtime smoke (adb unavailable)."
  exit 0
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found: $APK_PATH"
  echo "Build debug APK first (./gradlew :app:assembleDebug)."
  exit 1
fi

DEVICE="$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
if [[ -z "$DEVICE" ]]; then
  echo "No connected Android device."
  if [[ "$REQUIRE_DEVICE" == true ]]; then
    exit 1
  fi
  echo "Skipping runtime smoke (no connected device)."
  exit 0
fi

echo "Using device: $DEVICE"

"$ADB_BIN" -s "$DEVICE" wait-for-device
"$ADB_BIN" -s "$DEVICE" logcat -c
"$ADB_BIN" -s "$DEVICE" install -r "$APK_PATH" >/dev/null
"$ADB_BIN" -s "$DEVICE" shell am force-stop "$APP_ID" || true
"$ADB_BIN" -s "$DEVICE" shell am start -n "$APP_ID/$APP_ACTIVITY" >/dev/null

sleep 12

"$ADB_BIN" -s "$DEVICE" logcat -d > "$LOG_FILE"

if rg -n "FATAL EXCEPTION|ANR in ${APP_ID}|Process: ${APP_ID}.*(Exception|Error)|SIGSEGV" "$LOG_FILE"; then
  echo "Runtime smoke failed: crash/ANR pattern detected."
  exit 1
fi

echo "Runtime smoke passed: no crash/ANR patterns detected."

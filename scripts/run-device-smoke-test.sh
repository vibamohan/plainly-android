#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"

./gradlew assembleDebug

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not installed or is not on PATH. Install Android platform-tools." >&2
  exit 1
fi

device_count="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [ "$device_count" -eq 0 ]; then
  echo "No authorized Android emulator or device found. Start one and enable USB debugging." >&2
  adb devices || true
  exit 1
fi

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p org.plainly.app 1

echo "Plainly launched. Smoke-test: choose a PDF/image, capture with camera, expand evidence, try TTS, translation, history export/delete, and calendar confirmation."

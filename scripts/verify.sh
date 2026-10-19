#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"

./gradlew testDebugUnitTest assembleDebug lintDebug

if command -v adb >/dev/null 2>&1 && adb devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit found ? 0 : 1 }'; then
  ./gradlew connectedDebugAndroidTest
  echo "Automated and connected Android verification passed."
else
  echo "Automated verification passed. No authorized Android target found; connected tests skipped."
fi

echo "Manual feature smoke testing remains available: ./scripts/run-device-smoke-test.sh"

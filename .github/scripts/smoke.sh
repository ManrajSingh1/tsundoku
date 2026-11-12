#!/usr/bin/env bash
# Installs the spike APK on the CI emulator, drives it, and saves screenshots.
# This proves the app launches and the carousel flow works. It says nothing about
# frame rate: the emulator renders in software, so its timings are meaningless.
set -euo pipefail

PKG=io.github.manrajsingh1.tsundoku.spike
OUT=shots
mkdir -p "$OUT"

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

# Taps the centre of the first element whose text matches $1, found through the
# accessibility tree, so the script does not depend on screen coordinates.
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null
  python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, subprocess, sys
text, path = sys.argv[1], sys.argv[2]
xml = open(path, encoding="utf-8").read()
m = re.search(r'text="%s"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(text), xml)
if not m:
    sys.exit(f"No element with text {text!r} on screen")
x1, y1, x2, y2 = map(int, m.groups())
subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)], check=True)
PY
}

adb install -r apk/spike-release.apk
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
shot 01-opening
sleep 5
shot 02-open

adb shell input swipe 800 1000 250 1000 300
sleep 3
shot 03-after-swipe-left

adb shell input swipe 300 1000 560 1000 300
sleep 3
shot 04-after-swipe-right

tap_text "Tuning"
sleep 2
shot 05-tuning
tap_text "Flat"
sleep 2
shot 06-flat
tap_text "3D"
sleep 1
tap_text "Hide tuning"
sleep 1

tap_text "Run 20 s test"
sleep 6
shot 07-test-running
sleep 22
shot 08-test-result

adb logcat -d > "$OUT/logcat.txt"
if ! adb shell pidof "$PKG" > /dev/null; then
  echo "The app is no longer running."
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" || true
  exit 1
fi
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt"
  exit 1
fi
echo "Smoke test passed: the app launched, swiped, switched modes and finished a test run."

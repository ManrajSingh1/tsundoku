#!/usr/bin/env bash
# Installs the spike APK on the CI emulator, drives it, and saves screenshots.
# This proves the app launches and the carousel flow works. It says nothing about
# frame rate: the emulator renders in software, so its timings are meaningless.
set -euo pipefail

PKG=io.github.manrajsingh1.tsundoku.spike
SHOTS=shots
LOGS=smoke-logs
mkdir -p "$SHOTS" "$LOGS"

shot() { adb exec-out screencap -p > "$SHOTS/$1.png"; }

# Reads the accessibility tree into $LOGS/ui.xml. The emulator renders in software and is
# slow enough that system apps sometimes raise "isn't responding" dialogs over ours;
# those are dismissed here so they cannot swallow the next tap.
dump() {
  for _ in 1 2 3 4; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null
    adb pull /sdcard/ui.xml "$LOGS/ui.xml" > /dev/null
    if grep -q "isn&apos;t responding\|isn't responding" "$LOGS/ui.xml"; then
      echo "Dismissing a system 'not responding' dialog."
      tap_in_dump "Wait"
      sleep 3
    else
      return 0
    fi
  done
}

tap_in_dump() {
  python3 - "$1" "$LOGS/ui.xml" <<'PY'
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

# Prints every piece of text on screen with its bounds, so the job log shows what the
# app displayed at each step.
describe() {
  dump
  echo "--- on screen: $1"
  python3 - "$LOGS/ui.xml" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
for node in re.findall(r"<node[^>]*>", xml):
    text = re.search(r'text="([^"]*)"', node).group(1)
    if text:
        print("   ", re.search(r'bounds="([^"]*)"', node).group(1), text)
PY
}

# Taps the centre of the first element whose text is exactly $1, found through the
# accessibility tree, so the script does not depend on screen coordinates.
tap_text() {
  dump
  tap_in_dump "$1"
}

finish() {
  adb logcat -d > "$LOGS/logcat.txt" || true
}
trap finish EXIT

adb install -r apk/spike-release.apk
# Let the freshly booted emulator settle, and clear any dialog it raised while booting.
sleep 20
dump
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
shot 01-opening
sleep 5
shot 02-open
describe "after the open animation"

adb shell input swipe 800 1000 250 1000 300
sleep 3
shot 03-after-swipe-left
describe "after swiping left"

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

tap_text "Replay open"
sleep 0.3
shot 07-replay-mid-open
sleep 3

tap_text "Run 20 s test"
sleep 6
shot 08-test-running
sleep 22
shot 09-test-result
describe "after the 20-second test"

# The PDF screen, once per engine, with a generated sample file. This proves each engine
# opens a document in a release build; it is not a performance comparison.
python3 .github/scripts/make_sample_pdf.py "$LOGS/sample.pdf" 40
adb shell mkdir -p "/sdcard/Android/data/$PKG/files"
adb push "$LOGS/sample.pdf" "/sdcard/Android/data/$PKG/files/sample.pdf"
for engine in androidx readium; do
  adb shell am start -W -n "$PKG/.PdfSpikeActivity" --es engine "$engine"
  sleep 10
  shot "10-pdf-$engine"
  describe "PDF opened with $engine"
  adb shell input swipe 540 1900 540 1100 400
  sleep 4
  shot "11-pdf-$engine-scrolled"
  adb shell input keyevent BACK
  sleep 2
done

adb logcat -d > "$LOGS/logcat.txt"
grep -E "PdfSpike|AndroidRuntime" "$LOGS/logcat.txt" | tail -40 || true
if ! adb shell pidof "$PKG" > /dev/null; then
  echo "The app is no longer running."
  grep -A 30 "FATAL EXCEPTION" "$LOGS/logcat.txt" || true
  exit 1
fi
if grep -q "FATAL EXCEPTION" "$LOGS/logcat.txt"; then
  grep -A 30 "FATAL EXCEPTION" "$LOGS/logcat.txt"
  exit 1
fi
echo "Smoke test passed: the app launched, swiped, switched modes and finished a test run."

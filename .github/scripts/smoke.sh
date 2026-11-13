#!/usr/bin/env bash
# Drives the staging APK on the CI emulator: imports sample books through the real import
# path of a minified build, checks the library shows them, and saves screenshots.
#
# It proves the app works end to end. It says nothing about speed: the emulator renders in
# software.
set -euo pipefail

APP=io.github.manrajsingh1.tsundoku
STAGING=$APP.staging
SHOTS=shots
LOGS=smoke-logs
mkdir -p "$SHOTS" "$LOGS"

shot() { adb exec-out screencap -p > "$SHOTS/$1.png"; }

# Reads the accessibility tree into $LOGS/ui.xml. The emulator is slow enough that system
# apps sometimes raise "isn't responding" dialogs over ours; those are dismissed here so
# they cannot swallow the next tap or hide the screen being checked.
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

# Prints every piece of text on screen, so the job log shows what the app displayed.
describe() {
  dump
  echo "--- on screen: $1"
  python3 - "$LOGS/ui.xml" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
for node in re.findall(r"<node[^>]*>", xml):
    text = re.search(r'text="([^"]*)"', node).group(1)
    if text:
        print("   ", html.unescape(text))
PY
}

# Fails unless every argument appears somewhere in the text on screen.
expect() {
  python3 - "$LOGS/ui.xml" "$@" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
shown = " | ".join(html.unescape(t) for t in re.findall(r'text="([^"]*)"', xml) if t)
missing = [want for want in sys.argv[2:] if want not in shown]
if missing:
    sys.exit(f"Not on screen: {missing}")
print("    as expected:", ", ".join(sys.argv[2:]))
PY
}

finish() {
  adb logcat -d > "$LOGS/logcat.txt" || true
  adb shell settings put system font_scale 1.0 || true
  adb shell cmd uimode night no || true
}
trap finish EXIT

fail_on_crash() {
  adb logcat -d > "$LOGS/logcat.txt"
  if grep -q "FATAL EXCEPTION" "$LOGS/logcat.txt"; then
    grep -A 40 "FATAL EXCEPTION" "$LOGS/logcat.txt" | head -120
    exit 1
  fi
}

adb install -r apk/app-staging.apk
# Let the freshly booted emulator settle, and clear any dialog it raised while booting.
sleep 20
dump
adb logcat -c

# 1. A first launch with nothing in the library.
adb shell am start -W -n "$STAGING/$APP.MainActivity"
sleep 6
shot 01-empty-library
describe "first launch"
expect "TSUNDOKU" "Your shelves are empty" "Add folder" "Add files"
fail_on_crash

# 2. Import five sample files: two comics, an exact copy of one, an EPUB and a PDF.
python3 .github/scripts/make_fixtures.py "$LOGS/fixtures"
adb shell mkdir -p "/sdcard/Android/data/$STAGING/files/import"
adb push "$LOGS/fixtures/." "/sdcard/Android/data/$STAGING/files/import/"
adb shell am start -W -n "$STAGING/$APP.StagingImportActivity"
for _ in $(seq 1 40); do
  sleep 3
  dump
  # The Dismiss button only appears once the whole batch has finished.
  if grep -q 'text="Dismiss"' "$LOGS/ui.xml"; then break; fi
done
shot 02-after-import
describe "after the import finished"
fail_on_crash
expect "Added 4 books" "1 was already in your library"
expect "Manga" "Novels" "Documents"
expect "The Third Volume" "Quiet Shelf v07" "A Test Novel" "Quarterly report"
expect "Test Manga, vol. 3" "A. Author" "Nora Novelist" "Right to left" "40 pages"

# 3. The same screen in dark theme and at the largest text size.
adb shell cmd uimode night yes
sleep 4
shot 03-dark-theme
adb shell cmd uimode night no
adb shell settings put system font_scale 2.0
sleep 4
shot 04-largest-text
describe "at 200% text size"
expect "The Third Volume" "A Test Novel"
adb shell settings put system font_scale 1.0
sleep 2

# 4. The library survives the app being killed and started again.
adb shell am force-stop "$STAGING"
adb shell am start -W -n "$STAGING/$APP.MainActivity"
sleep 6
describe "after a restart"
expect "The Third Volume" "A Test Novel" "Quarterly report"
fail_on_crash

# 5. The APK that is actually published starts too.
adb install -r apk/app-release.apk
adb shell am start -W -n "$APP/$APP.MainActivity"
sleep 6
shot 05-release-build
describe "the release build"
expect "TSUNDOKU" "Your shelves are empty"
fail_on_crash

echo "Smoke test passed."

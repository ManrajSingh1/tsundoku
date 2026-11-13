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
LOGCAT_PID=
mkdir -p "$SHOTS" "$LOGS"

# No adb command may wait for ever. If the emulator dies, adb's own behaviour is to wait for
# it to come back, which once held a job until its 45-minute limit.
ADB="$(command -v adb)"
adb() { timeout 180 "$ADB" "$@"; }

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
import html, re, subprocess, sys
text, path = sys.argv[1], sys.argv[2]
xml = open(path, encoding="utf-8").read()
# Matches what is written on an element or, for one with no writing such as a book's spine,
# the start of what a screen reader says for it.
wanted = re.escape(html.escape(text, quote=False))
m = re.search(r'text="%s"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % wanted, xml) or \
    re.search(r'content-desc="%s[^"]*"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % wanted, xml)
if not m:
    sys.exit(f"No element with text {text!r} on screen")
x1, y1, x2, y2 = map(int, m.groups())
subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)], check=True)
PY
}

# Taps an element found by tap_in_dump's rules in a fresh reading of the screen.
tap() {
  dump
  tap_in_dump "$1"
  sleep "${2:-3}"
}

# Prints every piece of text on screen, and what a screen reader would say for the things
# that have no text, so the job log shows what the app displayed.
describe() {
  dump
  echo "--- on screen: $1"
  python3 - "$LOGS/ui.xml" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
for node in re.findall(r"<node[^>]*>", xml):
    text = re.search(r'text="([^"]*)"', node).group(1)
    spoken = re.search(r'content-desc="([^"]*)"', node).group(1)
    if text:
        print("   ", html.unescape(text))
    elif spoken:
        print("    [%s]" % html.unescape(spoken))
PY
}

# Fails unless every argument appears somewhere in the text on screen or in what a screen
# reader would say for it.
expect() {
  python3 - "$LOGS/ui.xml" "$@" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
shown = " | ".join(html.unescape(t) for t in re.findall(r'(?:text|content-desc)="([^"]*)"', xml) if t)
missing = [want for want in sys.argv[2:] if want not in shown]
if missing:
    sys.exit(f"Not on screen: {missing}")
print("    as expected:", ", ".join(sys.argv[2:]))
PY
}

# Whatever happens, leave the evidence in the job log: a crash's stack trace is the first
# thing anyone needs, and the full logcat is only in a downloadable artifact.
finish() {
  status=$?
  kill "$LOGCAT_PID" 2> /dev/null || true
  if ! timeout 20 "$ADB" get-state > /dev/null 2>&1; then
    echo "--- the emulator is no longer reachable. What the host knows:"
    timeout 20 "$ADB" devices || true
    pgrep -fa "qemu-system|emulator" | cut -c1-200 || echo "No emulator process is running."
    free -m || true
    sudo dmesg 2> /dev/null | grep -iE "out of memory|killed process|oom-kill|segfault" | tail -10 || true
    echo "--- the last of the device log before it went:"
    tail -120 "$LOGS/logcat-live.txt" || true
    exit "$status"
  fi
  adb logcat -d > "$LOGS/logcat.txt" || true
  if [ "$status" -ne 0 ]; then
    echo "--- the smoke test failed; crashes and hangs in the device log:"
    grep -A 40 "FATAL EXCEPTION" "$LOGS/logcat.txt" | head -150 || true
    grep -E "ANR in|Force finishing|Process .* has died|has crashed" "$LOGS/logcat.txt" | tail -20 || true
    echo "--- what is in front:"
    adb shell dumpsys activity activities | grep -E "topResumedActivity|ResumedActivity" | head -5 || true
    adb exec-out screencap -p > "$SHOTS/99-at-failure.png" || true
  fi
  adb shell settings put system font_scale 1.0 || true
  adb shell cmd uimode night no || true
  adb shell wm size reset || true
  adb shell wm density reset || true
}
trap finish EXIT

# Starts an activity and makes sure it really is in front. The emulator's own launcher
# sometimes hangs and takes the screen back; if so, the activity is started again.
launch() {
  for attempt in 1 2 3; do
    adb shell am start -W -n "$1"
    sleep "${2:-6}"
    dump
    if adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | grep -q "${1%%/*}/"; then
      return 0
    fi
    echo "Attempt $attempt: $1 is not in front; trying again."
    adb shell input keyevent KEYCODE_HOME
    sleep 3
  done
  echo "$1 never came to the front."
  return 1
}

fail_on_crash() {
  adb logcat -d > "$LOGS/logcat.txt"
  if grep -q "FATAL EXCEPTION" "$LOGS/logcat.txt"; then
    grep -A 40 "FATAL EXCEPTION" "$LOGS/logcat.txt" | head -120
    exit 1
  fi
}

adb install -r apk/app-staging.apk
# The emulator renders in software and its launcher regularly stops responding. Keep the
# system from putting "isn't responding" dialogs over the app under test.
adb shell settings put global hide_error_dialogs 1
# Let the freshly booted emulator settle, and clear any dialog it raised while booting.
sleep 20
dump
adb logcat -c
# A running copy of the device log, kept on the host, so its last lines survive the emulator.
"$ADB" logcat -v time > "$LOGS/logcat-live.txt" 2> /dev/null &
LOGCAT_PID=$!

# 1. A first launch with nothing in the library.
launch "$STAGING/$APP.MainActivity"
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
tap "Dismiss"

# 3. The shelves: every book is a spine that says what it is, on the shelf for its format.
describe "the shelves"
shot 03-shelves
expect "Manga" "2 books" "Novels" "1 book" "Add books"
expect "The Third Volume, volume 3, CBZ" "Quiet Shelf" "A Test Novel, EPUB"
adb shell input swipe 500 1700 500 700 400
sleep 3
describe "the shelves, scrolled"
expect "Documents" "Quarterly report, PDF"
adb shell input swipe 500 700 500 1900 400
sleep 3

# 4. Tapping a spine opens its shelf at that book; the arrows step along it; back returns.
tap "The Third Volume"
describe "an open book"
shot 04-open-book
expect "The Third Volume" "A. Author · vol. 3 · CBZ" "Book 1 of 2"
tap "Next book"
describe "the next book"
expect "Book 2 of 2"
adb shell input keyevent KEYCODE_BACK
sleep 3
describe "back in the library"
expect "Manga" "Add books"
fail_on_crash

# 5. Edit mode, through the real database: move a book along its shelf, send it to the next
#    shelf, start to remove it and think better of it, then delete a shelf and keep its books.
tap "More options"
tap "Edit shelves"
describe "edit mode"
expect "Tap a book to move or remove it" "Done" "Add files to Manga"
tap "The Third Volume"
describe "a selected book"
shot 05-edit-selected
expect "1 of 2 · Manga"
tap "Move right"
describe "moved right"
expect "2 of 2 · Manga"
tap "Next shelf"
describe "moved to the next shelf"
shot 06-edit-moved-shelf
expect "2 of 2 · Novels"
tap "Remove"
describe "asked about removing"
expect "Remove from library?" "The file itself is not deleted."
tap "Cancel"
tap "Delete shelf Novels"
describe "asked about deleting a shelf"
shot 07-delete-shelf
expect "Delete Novels?" "Its 2 books move to Manga. No files are deleted."
tap "Delete"
tap "Done"
describe "after editing"
shot 08-after-editing
# Nothing was lost: the novel and the moved comic now stand on the first shelf.
expect "Manga" "3 books" "A Test Novel, EPUB" "The Third Volume, volume 3, CBZ" "Add books"
fail_on_crash

# 6. The same screen in dark theme and at the largest text size.
adb shell cmd uimode night yes
sleep 4
shot 09-dark-theme
adb shell cmd uimode night no
adb shell settings put system font_scale 2.0
sleep 4
shot 10-largest-text
describe "at 200% text size"
expect "Manga" "3 books" "Quiet Shelf"
tap "More options"
tap "Edit shelves"
tap "Quiet Shelf"
describe "edit mode at 200% text size"
shot 11-largest-text-editing
expect "1 of 3 · Manga" "Move left" "Move right" "Next shelf" "Remove" "Done"
tap "Done"
adb shell settings put system font_scale 1.0
sleep 2

# 7. Other window sizes. A tablet on its side shows the library beside the open shelf; an
#    unfolded foldable's inner screen, with no hinge reported by the emulator, shows one pane.
adb shell wm size 2560x1600
adb shell wm density 320
sleep 6
launch "$STAGING/$APP.MainActivity"
describe "tablet, landscape: nothing open"
expect "Manga" "Pick a book"
tap "The Third Volume"
describe "tablet, landscape: a book open beside the library"
shot 12-tablet-two-panes
expect "Manga" "3 books" "Book 3 of 3" "Add books"
tap "More options"
tap "Edit shelves"
tap "Quiet Shelf"
describe "tablet, landscape: editing beside the open book"
shot 13-tablet-editing
expect "1 of 3 · Manga" "Book 3 of 3"
tap "Done"
fail_on_crash

adb shell wm size 1812x2176
adb shell wm density 420
sleep 6
launch "$STAGING/$APP.MainActivity"
describe "foldable inner screen"
shot 14-foldable-inner-screen
fail_on_crash
adb shell input keyevent KEYCODE_BACK
sleep 3
dump
expect "Manga" "Add books"

adb shell wm size reset
adb shell wm density reset
sleep 6

# 8. The library survives the app being killed and started again, edits included.
adb shell am force-stop "$STAGING"
launch "$STAGING/$APP.MainActivity"
describe "after a restart"
expect "Manga" "3 books" "A Test Novel, EPUB" "The Third Volume, volume 3, CBZ"
fail_on_crash

# 9. An upgrade: the previously published build creates its database, then the build that
#    is about to be published is installed over it and has to open that database.
if [ -f apk-previous/tsundoku-preview.apk ]; then
  adb install -r apk-previous/tsundoku-preview.apk
  launch "$APP/$APP.MainActivity"
  adb shell am force-stop "$APP"
  echo "Installed and ran the previous preview; upgrading it."
else
  echo "No previous preview to upgrade from; installing fresh."
fi
adb install -r apk/app-release.apk
launch "$APP/$APP.MainActivity"
shot 15-release-build
describe "the release build"
expect "TSUNDOKU" "Your shelves are empty"
fail_on_crash

echo "Smoke test passed."

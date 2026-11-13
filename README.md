# TSUNDOKU

A local-first Android reader for manga, comics, EPUBs and PDFs that shows your library as
bookshelves of spines and opens each shelf as a rotating cover carousel.

**Status: Phase 0 (plan and spikes).** The only code here is `spike/`, a throwaway app that
proves the risky parts before the real app is built. The decisions behind it are in
[`docs/decisions/`](docs/decisions/).

## Getting a build

Nobody needs Android Studio to try a build. Every push to `main` runs the **CI** workflow,
which builds the spike in release mode and publishes the APK:

1. On the phone, sign in to GitHub and open the
   [spike-latest pre-release](../../releases/tag/spike-latest).
2. Download `spike-release.apk` and install it.

CI replaces that pre-release on every green build. It also drives the app on an emulator
and keeps the screenshots on the [`ci-screenshots`](../../tree/ci-screenshots) branch.

The APK is signed with a test key that lives in this repository
(`spike/keystore/spike-test.jks`), so each build installs over the previous one. That key
protects nothing and must never sign a Play release.

## Building locally

Needs JDK 17 or newer and the Android SDK.

```
./gradlew :spike:assembleRelease
```

The APK lands in `spike/build/outputs/apk/release/`.

## The carousel spike

The spike draws the 3D carousel from the brief with generated books and measures every
frame the system renders.

1. Install the release APK on a Galaxy phone. Leave Motion smoothness on Adaptive.
2. Open the app and tap **Run 20 s test**. Do not touch the screen while it sweeps.
3. Run it a second time and read the second result; the first run includes warm-up.
4. Note the `RESULT` line and the three lines above it.

`RESULT PASS` means fewer than 1% of frames took twice the frame budget or longer, and the
99th-percentile frame stayed under 1.5 times the budget. Those marks are a proposal to be
tuned after the first real run.

**Tuning** switches between 3D and the flat fallback, changes how many books are in the
library and how many are drawn, and adjusts the neighbour angle and camera distance.

## The PDF engine spike

Tap **PDF** on the main screen. Pick a PDF, then open it in each engine in turn:
**androidx.pdf** (Google's Jetpack viewer) and **Readium** (its PDFium adapter).

For each engine and each file:

1. Note the first status line, which gives the load time.
2. Tap **Reset stats**, scroll and zoom for about ten seconds, and note the next two lines:
   frame times, dropped frames and memory.

The load times are not like for like. androidx.pdf reports when the document has loaded;
Readium gives no such signal, so its time stops when the viewer is attached, before the
first page is drawn. Judge the wait for the first page by eye.

Readium opens paginated, swiping sideways; androidx.pdf scrolls vertically. That is each
engine's default, not a fault.

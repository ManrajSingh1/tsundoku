# TSUNDOKU

A local-first Android reader for manga, comics, EPUBs and PDFs that shows your library as
bookshelves of spines and opens each shelf as a rotating cover carousel.

**Status: Phase 0 (plan and spikes).** The only code here is `spike/`, a throwaway app that
proves the risky parts before the real app is built. The decisions behind it are in
[`docs/decisions/`](docs/decisions/).

## Getting a build

Nobody needs Android Studio to try a build. Every push to `main` runs the **CI** workflow,
which builds the spike in release mode and uploads the APK:

1. Open the repository's **Actions** tab and pick the latest green **CI** run.
2. Download the `tsundoku-spike-apk` artifact and unzip it.
3. Copy `spike-release.apk` to the phone and install it.

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

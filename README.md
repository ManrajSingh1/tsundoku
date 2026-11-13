# TSUNDOKU

A local-first Android reader for manga, comics, EPUBs and PDFs that shows your library as
bookshelves of spines and opens each shelf as a rotating cover carousel.

**Status: Phase 1 (foundations).** The app imports CBZ, EPUB and PDF files and lists them
shelf by shelf. The bookshelf, the carousel and the readers come in later phases. The
decisions behind the code are in [`docs/decisions/`](docs/decisions/).

## Getting a build

Nobody needs Android Studio to try a build.

1. On the phone, sign in to GitHub and open the
   [preview-latest pre-release](../../releases/tag/preview-latest).
2. Download `tsundoku-preview.apk` and install it.

CI replaces that pre-release on every green build of `main`. The APK is signed with a test
key kept in this repository (`app/keystore/test-key.jks`), so each build installs over the
last. That key protects nothing and must never sign a Play release.

## What to try

- **Add folder** imports every CBZ, EPUB and PDF in a folder and the folders inside it.
  Use this for a library of any size: Android lets an app remember a limited number of
  individually picked files, and a folder counts as one.
- **Add files** imports the files you pick.

Your files stay where they are. The app stores a reference to each one, a small cover
thumbnail and what it read from the file: title, author, series, volume, page count, and
whether a comic reads right to left.

## How it is built

| Module | What it holds |
| --- | --- |
| `app` | The one activity. Wires features together and nothing else. |
| `core:model` | Plain Kotlin: the library's types and the rules that need no Android (telling formats apart, reading `ComicInfo.xml`, parsing file names, spine colours). |
| `core:database` | The Room database: books, shelves, reading progress and the import queue. |
| `core:data` | Repositories, and the import pipeline that turns a picked file into a shelved book. |
| `core:ui` | The theme. |
| `feature:library` | The library screen. |
| `feature:import` | The file and folder pickers and the import progress. |
| `build-logic` | Convention plugins, so each module's build file is a few lines. |

Features never depend on each other, and only repositories talk to the database.

## Building and testing locally

Needs JDK 17 or newer and the Android SDK.

```
./gradlew :core:model:test testDebugUnitTest      # unit tests, on the JVM
./gradlew :app:assembleRelease                    # the APK, in app/build/outputs/apk/release/
./gradlew :core:database:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

The last line needs a device or emulator. It runs the database tests and imports a real
CBZ, EPUB and PDF end to end.

## What CI checks on every push

- Unit tests, then the release build.
- The APK does not ask for the network permission.
- Every native library is linked for 16 KB memory pages.
- Database and end-to-end import tests on an emulator.
- A smoke test that drives the minified app: first launch, an import of five sample files,
  dark theme, largest text size, and a restart. Its screenshots are kept on the
  [`ci-screenshots`](../../tree/ci-screenshots) branch.

# Gate 1 hand-over: Foundations

Date: 2026-11-13. Status: waiting for a check on a real phone.

The gate in the brief is "CBZ, EPUB and PDF import end to end; CI green with unit tests".
Both hold in CI. Nothing has run on a real phone yet. The build is the
[preview-latest pre-release](../../../../releases/tag/preview-latest) and the code came in
through [pull request 1](../../../../pull/1).

## What was built

- **Modules:** `app`, `core:model`, `core:database`, `core:data`, `core:ui`,
  `feature:library`, `feature:import`, with convention plugins in `build-logic` and Hilt for
  dependency injection.
- **Data layer:** a Room 3 database of books, shelves, reading progress and the import
  queue. Its version 1 schema is exported and committed.
- **Import:** a picked file is identified by its bytes, recognised if it is already in the
  library, read for metadata and cover through Readium, and put on a shelf by format. Files
  stay where they are.
- **A library list** with **Add folder**, **Add files** and import progress. It is a
  stand-in; the bookshelf of spines replaces it in Phase 2.
- **The spike is deleted.** Its code is in the repository's history for Phase 3 to draw on.

## How it was checked

| Check | Result |
| --- | --- |
| Unit tests on the JVM | 37 pass |
| Tests on an emulator: database, bounded image decoding, end-to-end import | 24 pass |
| Smoke test of the minified build: first launch, five sample files imported, dark theme, 200% text, restart | Pass |
| Release APK asks for the network permission | No |
| Native libraries linked for 16 KB pages | All 8 |
| Android lint | 0 errors, 13 warnings, all "a newer version is available" |
| Release APK size | 22 MB, most of it PDFium for four ABIs |

The end-to-end tests import a real CBZ, EPUB and PDF with the real engines and check title,
author, series, volume, page count, reading direction, cover and shelf. They also cover a
renamed duplicate, a moved file being relinked, a text file named `.cbz`, a truncated PDF,
an archive with no pages, and a file that has gone.

## What differs from the plan, and why

- **A release-only crash, found and fixed.** The minified build crashed at launch: the
  stricter shrinking in Android Gradle plugin 9 removed a constructor WorkManager creates
  by reflection. Debug builds and the instrumented tests are not minified and did not show
  it. Only the smoke test of the minified build did, which is why CI has one.
- **Folder import comes first.** Android limits how many individually picked files one app
  can keep access to; a picked folder counts as one. A large library should be added as a
  folder.
- **Comics default to right to left.** A comic whose file says nothing about direction
  follows an app-wide default, set to right to left. A file whose `ComicInfo.xml` states a
  direction keeps it.
- **Navigation is not added yet.** There is one screen; Navigation 3 arrives with the
  second, in Phase 2.
- **ktlint and detekt are not wired in.** Android lint runs; the two style tools are still
  to add.
- **Several libraries are a version or more behind.** Versions known to resolve were
  pinned; lint now names the current ones (for example Coil 3.6.3, lifecycle 2.11.0,
  DataStore 1.3.0). They are raised at the start of Phase 2.
- **WorkManager adds four permissions** to the APK: wake lock, network state, run at boot
  and foreground service. None reaches the network. They can be stripped if a shorter list
  is preferred.

## Not verified

- **Anything on a real phone.** Import has only seen small synthetic files on an emulator.
- **Samsung's file picker.** The picker offers files by type, and file managers disagree
  about what a `.cbz` is. It may refuse to let one be selected.
- **Large files and big folders.** Each file is read whole once to fingerprint it. How
  long a 500 MB volume or a 2,000-file folder takes on a phone is unknown.
- **The PDFium fork's licence file**, still unconfirmed.

## Needed to close the gate

1. Install the preview build and add a folder of real manga. Note how many files, roughly
   how long it took, and whether titles, volumes, covers and "Right to left" labels are right.
2. Add an EPUB and a PDF with **Add files**, to check the phone's picker allows each type.
3. A go for Phase 2 (Library and editing).

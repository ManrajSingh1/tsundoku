# 0001: Versions and engines chosen in Phase 0

Date: 2026-11-12. Status: accepted, except where marked provisional.

Every version below was read from its official release page on the date above.

| Component | Version | Notes |
| --- | --- | --- |
| Android Gradle plugin | 9.5.1 | Needs Gradle 9.6.0 and JDK 17. Kotlin support is built in. |
| Kotlin | 2.4.20 | Sets the Compose compiler plugin version. |
| Compose BOM | 2026.09.00 | foundation 1.12.1, Material 3 1.4.0, Material 3 Adaptive 1.3.0. |
| Navigation 3 | 1.2.1 | Chosen over Navigation 2. |
| Room 3 | 3.0.3 | `androidx.room3`, KSP only. |
| Hilt | 2.59.2 or newer | 2.59 is the first release that supports Android Gradle plugin 9. |
| androidx.pdf | 1.0.0-beta01 | Provisional PDF engine. minSdk 28. Compose viewer still experimental. |
| Readium Kotlin Toolkit | 3.4.0 | EPUB engine. minSdk 24. BSD-3-Clause. |

## SDK levels

- minSdk 28: the floor androidx.pdf reads and renders on; Readium needs 24.
- targetSdk 36: required by Google Play since 31 August 2026.
- compileSdk 37.

## Provisional until the phone spike

- PDF engine: androidx.pdf, with Readium's PDFium adapter as the fallback.
- 3D carousel: kept if it passes the frame-rate test on a mid-range Galaxy, otherwise the
  flat fallback ships.

## Not decided here

- Package id. The spike uses `io.github.manrajsingh1.tsundoku.spike` and is thrown away.
- Whether right-to-left series also mirror the shelf and carousel order in v1.0.

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
| androidx.pdf | 1.0.0-beta01 | Fallback PDF engine. minSdk 28. Compose viewer still experimental. |
| Readium Kotlin Toolkit | 3.4.0 | EPUB engine, and PDF through its PDFium adapter. minSdk 24. BSD-3-Clause. Needs core library desugaring. |

## SDK levels

- minSdk 28: the floor androidx.pdf reads and renders on; Readium needs 24.
- targetSdk 36: required by Google Play since 31 August 2026.
- compileSdk 37.

## Settled by the phone spikes

The PDF engine, the 3D carousel, the package id and manga reading direction were left open
here. They are settled in [0002](0002-phase-0-spike-results.md): Readium's PDFium adapter
is the v1 PDF engine and androidx.pdf the fallback, reversing the provisional pick above.

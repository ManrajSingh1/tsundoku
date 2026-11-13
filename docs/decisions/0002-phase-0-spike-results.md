# 0002: Phase 0 spike results and the decisions they settled

Date: 2026-11-13. Status: accepted.

Both spikes were run by the project owner on a Samsung Galaxy S26 Ultra, from the
release-mode spike APK built by CI.

## 3D carousel: kept

The 20-second automated sweep at 120 Hz (frame budget 8.3 ms):

| Measure | Result | Pass mark |
| --- | --- | --- |
| Frames drawn | 1,777 | |
| Median frame | 4.8 ms | |
| 90th-percentile frame | 6.5 ms | |
| 99th-percentile frame | 11.8 ms | Under 12.5 ms |
| Worst frame | 16.1 ms | |
| Late frames | 4.2% | |
| Dropped frames (twice the budget or longer) | 0.0% | Under 1% |

It passes with little room, on a flagship. A mid-range Galaxy, flat mode and a 200-book
library were not tested. Flat mode ships as the reduced-motion and low-power fallback.

Known fault in the spike's read-out: its `RESULT` line compared against the refresh rate
at the moment it was written (60 Hz, after the display idled down), not the rate during
the test. The pass above was judged by hand against the 120 Hz budget. The real app's
overlay must take the budget from the frames it measured.

## PDF engine: Readium's PDFium adapter

One file, hand-scrolled in each engine:

| Measure | androidx.pdf 1.0.0-beta01 | Readium PDFium 3.4.0 |
| --- | --- | --- |
| Load time | 159 ms to document loaded | 191 ms to viewer attached |
| Median frame | 3.1 ms | 3.9 ms |
| 99th-percentile frame | 14.7 ms | 15.2 ms |
| Worst frame | 15.4 ms | 59.7 ms |
| Late frames | 20.9% | 27.2% |
| Dropped frames | 0.0% | 0.1% |
| Native memory | 43 MB | 102 MB |
| Java memory | 11 MB | 8 MB |
| Owner's verdict | | Felt better |

The numbers favour androidx.pdf slightly on frames and clearly on memory. The owner
preferred Readium in the hand, and the brief makes the on-device spike the judge.

What choosing Readium costs, found by CI:

- About 5.1 MB of PDFium for arm64 before compression (18 MB across four ABIs).
- A JitPack dependency on `com.github.marain87:PdfiumAndroid` and `AndroidPdfViewer`,
  a personal fork. Its own licence file is unconfirmed; upstream is Apache-2.0. This must
  be confirmed before any public release.
- Core library desugaring (Readium needs it for EPUB regardless).

Its PDFium libraries are linked for 16 KB pages, checked from their ELF headers.

androidx.pdf stays the fallback. Both sit behind the `BookReader` interface, so swapping
is contained to one module.

## Other decisions confirmed by the owner

- Package id: `io.github.manrajsingh1.tsundoku`.
- Manga reads right to left. v1.0: per-series reading direction in the reader, set from
  `ComicInfo.xml` at import when present, by hand otherwise, with an app-wide default.
  v1.x: mirrored spine order on the shelf and a reversed carousel.
- Backup: file backup only in v1, no cloud.
- Distribution: an APK from GitHub. There is no Play developer account yet; the app is
  still built to Play's rules so a listing stays possible.

# Gates 3 and 4 hand-over: Carousel, readers and progress

Date: 2026-11-14. Status: both pass in CI; neither has run on a real phone.

Phases 3 and 4 were built together at your request, so this is one hand-over for two gates.

- **Gate 3:** "Holds refresh rate on a mid-range Galaxy; reduced-motion fallback." The
  fallback is built and tested. The frame rate was **not measured** (see below).
- **Gate 4:** CBZ, EPUB and PDF readers, resume, and return to the same carousel spot.
  All three open, read and resume in the minified build on an emulator.

The build is the [preview-latest pre-release](../../../../releases/tag/preview-latest), the
code came in through [pull request 3](../../../../pull/3), and the screenshots are on the
[ci-screenshots branch](../../../../tree/ci-screenshots).

## Checked on the phone, 15 November

On the S26 Ultra: PDF pages turn sideways, and double-tap zoom, pinch zoom and tap-to-turn
all work and are to be kept. That answers both decisions below. Three changes were asked
for and built: sort A to Z in edit mode, running a finger along a shelf to lift and open a
book, and a much slimmer bar in the reader. See
[decision record 0006](../decisions/0006-sort-riffle-reader-bar.md).

## What was built

- **The carousel.** Opening a shelf shows its books as 3D covers: the tapped book faces
  front, neighbours turn 58 degrees towards it and stand back, spines showing on the left
  and page edges on the right, darkening with distance. It opens from spines over 750 ms.
  Drag to browse (it follows the finger and snaps to a book), tap a side book to bring it
  forward, or use the arrows. It scales to the pane, so it fills a tablet's right-hand pane.
- **The info panel.** Title, one quiet line, and one wide button: **Start reading**,
  **Continue reading** or **Read again**, with a thin line along its bottom edge and the
  percentage on the right. Tapping the centred cover does the same as the button.
- **Reduced motion.** With animations removed in the system settings the carousel is flat
  and still.
- **Comic reader (CBZ).** One page to the screen. Right to left by default, as manga is
  read: the next page is on the left. Swipe, or tap an edge, to turn; tap the middle for
  the bars; pinch or double-tap to zoom. The bar has a page slider that runs the same way
  as the book, and a button to switch direction for the book and its whole series.
- **EPUB reader.** Readium's navigator: swipe or tap an edge to turn, tap the middle for
  the bars. Follows the system's light or dark theme. **A−** and **A+** change the text
  size, remembered for every EPUB.
- **PDF reader.** Readium's PDFium navigator, set up exactly as in the comparison build
  you preferred. Tap for the bars; the bar shows the page number.
- **Progress and resume.** Every page turn is saved. A book reopens where it was left;
  **Read again** starts a finished book from the beginning. Closing a book returns to the
  same shelf with the same book facing front.
- **A book whose file has gone** says so, with a way back, instead of an empty screen.

## How it was checked

| Check | Result |
| --- | --- |
| Unit tests on the JVM | 78 pass (carousel poses, progress, tap zones among them) |
| Tests on an emulator | 59 pass |
| Smoke test of the minified build | Pass |
| Release APK asks for the network permission | No |
| Native libraries linked for 16 KB pages | All 8 |
| Android lint | 0 errors, 6 warnings |
| Release APK size | 25.2 MB (was 22.7 MB; the EPUB and PDF navigators) |

The smoke test now also: opens a manga volume, checks it starts at page 1 of 6 reading
right to left, swipes right and checks page 2, closes it and checks the shelf says
**Continue reading** at 20% on the same book; reopens the shelf with animations removed;
opens the EPUB and makes its text larger; opens the PDF at page 1 of 40. Everything
compiled and passed on the first CI run, which used about 17 minutes of Actions time; the
run on `main` after merging uses about the same.

## What differs from the brief, and why

| The brief says | What was built | Why |
| --- | --- | --- |
| Carousel holds the refresh rate on a mid-range Galaxy, measured | Not measured | No such device, and the spike's frame overlay was not carried over. The only measurement is Phase 0's on the S26 Ultra. Macrobenchmark is Phase 5. |
| Cover 188 x 282 dp | That size on a phone; scaled from 0.45x to 1.6x elsewhere | A fixed size is lost in a tablet pane and does not fit a phone on its side. |
| Drag, arrows and back | Also: tap a side book, tap the centred cover | Costs nothing and is what a finger tries first. |
| Info panel: title, one line, button | Also a small "Book 2 of 5" line | Kept from Phase 2; it is what a screen reader announces. |
| ReadingProgress holds a page index for PDFs | A Readium locator for PDFs and EPUBs | It reopens the navigator exactly where it was. No schema change. |
| Readium comics navigator, or a small custom pager | The custom pager | Direct control of right-to-left paging, tap zones and zoom. |
| EPUB themes and font size | System light or dark, and font size | No settings screen yet. |
| Detect moved files and offer to relink | The reader says the file has gone | Relinking from the reader is not built; re-adding the file relinks it (Phase 1). |
| Baseline Profile, Macrobenchmark | Not built | Phase 5. |

Full reasoning is in [decision record 0005](../decisions/0005-phases-3-4-carousel-readers.md).

## Not verified

- **Anything on a real device**, above all how the carousel feels and whether it holds
  120 Hz with your own covers.
- **Your own files.** Everything was read from five small generated samples. Real manga
  volumes are hundreds of megabytes, and Readium reads them through Android's document
  permission; how quickly a page deep in a large CBZ appears is unknown. This is the
  largest open risk in the readers.
- **"Last position within one second of tapping Continue."** Not timed.
- **Gestures a script cannot make:** pinch zoom, double-tap, edge taps in the EPUB.
- **Sideways paging in the PDF.** A scripted sideways swipe did not turn the page.
- **TalkBack in the readers**, fixed-layout EPUBs, right-to-left EPUBs, password-protected
  or damaged PDFs, a book removed while it is open, and the app being killed mid-book
  (it should reopen at the saved position; not tried).

## Decisions needed

1. **PDF paging.** It behaves as the comparison build did. If what you liked there was
   turning pages sideways and this build does not do that, say so and it will be forced
   to one page per sideways swipe.
2. **Double-tap to zoom in comics** makes a single tap wait about a third of a second
   before turning the page (swipes are immediate). Keep it, or drop double-tap and keep
   pinch only?
3. Still open from Gate 2: when to build drag-to-reorder, whether whole shelves should be
   reorderable, and whether to accept files dropped in from other apps.

None of these blocks Phase 5.

## What to check on the phone

1. Install the new preview over the old one. Your library should still be there.
2. Tap a spine. Watch the shelf open, drag the carousel, tap a side book, use the arrows.
3. Open one of your own manga volumes. Check it reads right to left, turn pages by swipe
   and by tapping the left edge, zoom in and out, drag the slider, and note how long a
   page takes to appear, near the start and deep into the book.
4. Close it. The same book should face front and say **Continue reading**. Reopen it.
5. Do the same with an EPUB (try **A+**) and a PDF (say which way its pages turn).
6. In Settings, Accessibility, turn on **Remove animations**, open a shelf, turn it off.
7. Turn the phone on its side in the carousel and in each reader.

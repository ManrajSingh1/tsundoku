# 0005: Choices made building the carousel and the readers

Date: 2026-11-14. Status: accepted.

## The carousel is the Phase 0 spike, drawn from real books

The technique that passed on the S26 Ultra is kept as it was: each book is three flat
layers (cover, spine, page edge) sharing a hinge, and every per-frame value is read inside
a layer or draw block, so a swipe redraws without recomposing. Only the centred book and
three either side are composed.

What changed from the spike:

- **It scales to its pane.** The prototype's sizes (188 x 282 dp cover, 118 and 50 dp
  steps) fit a phone held upright. Every distance is multiplied by one factor chosen from
  the pane's width and height, from 0.45 to 1.6, so the same carousel fills a tablet pane
  and still fits a phone on its side.
- **The pose arithmetic is a plain object with unit tests** (`CarouselPose`): angles,
  depth, darkening, where a fling settles, which book a tap means, and that every cover is
  centred on its slot whichever edge it turns about.
- **Taps do something.** Tapping a book to the side brings it to the centre; tapping the
  centred cover opens it. The brief names drag, arrows and back only.
- **The opening plays once per shelf**, not again on rotation or on returning from a book.

## Reduced motion is a flat, still carousel

If the user has removed animations in the system settings, or Android reports a low-memory
device, covers do not turn (they overlap like cards), there is no opening animation and a
drag lands on its book at once. This is the brief's "flat fallback", decided once when the
shelf opens. There is no in-app switch for it yet.

## Screen readers browse with the panel, not the covers

The covers are drawn for the eye and hidden from the accessibility tree. The panel below
gives the same browsing: the centred book's title, author and place (announced when it
changes), the arrows, and the read button, whose state says how much has been read.

## The carousel's frame rate was not measured in this phase

The Phase 3 gate asks that it "holds refresh rate on a mid-range Galaxy". There is no such
device, and the spike's frame overlay was not carried into the app. What is known is the
Phase 0 result on the S26 Ultra (passes at 120 Hz with little headroom), with generated
covers. Real covers are bitmaps of at most 600 x 900 drawn by Coil, which should cost the
same to draw, but that is reasoning, not a measurement. Macrobenchmark belongs to Phase 5.

## Readers live in their own activity

`feature:reader` holds one `ReaderActivity`. The library activity stays underneath exactly
as it was, which is the whole of "return to the same carousel spot": closing a book shows
the same shelf with the same book facing front, and its progress has already updated.

Readium's EPUB and PDF navigators are fragments built on AppCompat and Material, so this
one activity uses an AppCompat theme; the rest of the app does not. Navigation 3 is still
not used: there are two screens and the system's activity stack does the work.

The page and the bars are separate views. The bars take only the space they draw in, so a
touch anywhere else reaches the page, whoever draws it.

## Comics use the app's own pager over Readium's container

The brief allows "Readium comics navigator, or a small custom pager". The pager is about
200 lines of Compose and gives direct control of the things manga needs: right-to-left
paging, tap zones that follow the direction, a slider that runs the same way, and zoom.
Readium still opens the archive and orders the pages; nothing parses a zip by hand.

- **Direction.** A comic reads right to left unless its file or the user says otherwise.
  The button in the reader changes it for that book and, if the book has a series, for
  every volume of the series.
- **Decoding.** A page is decoded no larger than one and a half times the screen, through
  the same bounded decoder as covers. A scan too large for that is retried at half and
  quarter size before the reader gives up on the page.
- **Zoom** is pinch or double-tap. One finger on an unzoomed page is left to the pager.
  Because double-tap exists, a single tap waits about 0.3 s before it turns the page.

## Progress is a fraction, and positions are what each engine understands

`reading_progress.percent` holds 0.0 to 1.0. A comic's position is its page index; an EPUB's
or PDF's is Readium's locator as JSON (the data model in the brief suggested a page index
for PDFs, but the locator is what reopens the navigator exactly where it was). The schema
did not change, so there is no migration.

- A comic or PDF is finished on its last page. Looking at page 1 is 0%.
- An EPUB follows Readium's estimate and is finished at its last position. A very short
  EPUB (one position) never quite reaches 100%.
- Writes go through a conflated queue owned by the repository, not the screen, so the last
  page turn before the reader closes is saved.

## PDF paging is left exactly as in the engine comparison

The PDF navigator is created with Readium's defaults, the same call as the Phase 0 build
that was preferred on the phone. In CI a sideways swipe did not turn the page, which
suggests those defaults scroll vertically. Whether that is what was liked on the phone is
a question for the Gate 4 hand-over; forcing sideways paging is a one-line preference.

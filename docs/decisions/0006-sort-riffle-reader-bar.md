# 0006: Changes after the first reading on the phone

Date: 2026-11-15. Status: accepted.

Three requests came back from the S26 Ultra, with confirmation that PDF pages turn sideways
and that double-tap zoom, pinch zoom and tap-to-turn should all stay as they are.

## Sort A to Z is a button on each shelf in edit mode, and can be undone

Each shelf's edit header gains **A–Z** between the name field and the delete button. It
orders the shelf by title the way a person would shelve it: case is ignored and numbers
count as numbers, so "Volume 2" comes before "Volume 10". Equal titles go by volume.

- The button is greyed out when the shelf is already in order, which doubles as the sign
  that it is.
- Sorting throws away an order that may have been arranged by hand, so the message that
  confirms it carries **Undo**, which puts back exactly the order there was.
- It is a one-off action, not a mode: books added later still go to the end.

The database gains one operation, "put this shelf in this order", used by both the sort and
its undo. Books on the shelf that the order does not name keep their place after the ones
it does, so a book imported between a sort and its undo is not lost. No schema change.

## A finger run along a shelf lifts each book in turn and opens the one it leaves from

Outside edit mode, touching a spine lifts it. Slide sideways and the lift follows the
finger from book to book, with a light tick for each; lift the finger and that book opens
in the carousel. Drifting well above or below the shelf drops the book and opens nothing.

What this changes: **a sideways drag no longer scrolls a long shelf.** A drag cannot mean
both. Instead, holding the finger near either end slides the shelf along underneath it,
faster the nearer the edge. Up-and-down drags still scroll the page, taps still open a
book, and the row still scrolls for a screen reader, whose gestures do not pass through
this code. Edit mode is unchanged: there a drag scrolls and a tap selects.

Only the spine reached and the spine left recompose as the finger moves.

## The reader's bar is one slim pill

The bar at the foot of the reader took about a fifth of the screen. It is now a single
rounded pill, 48 dp tall, floating over the foot of the page:

- **Comics:** `12 / 180`, a thin slider with a small round thumb, and an arrow that points
  the way the pages run (tap it to turn the book round).
- **EPUBs:** the percentage, a thin progress line, **A−** and **A+**.
- **PDFs:** the page number and a thin progress line.

Screen readers still hear "Page 12 of 180", "40% read" and what the arrow does.

## PDF paging is settled

Sideways paging was confirmed on the phone with Readium's defaults, so the PDF navigator
stays exactly as it is. The scripted swipe in CI still does not turn the page on the
emulator; the smoke test reports that without failing.

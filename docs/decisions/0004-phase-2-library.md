# 0004: Choices made building the library and edit mode

Date: 2026-11-13. Status: accepted.

## Edit lives at the bottom, not in the header

The prototype put Edit and Done in the header. The brief's One UI rules ask for the
opposite: looking at the top, doing at the bottom. So:

- The app bar keeps only the title and a **More** button at the far right, which holds
  **Edit shelves**, **Add files** and **Add folder**.
- **Add books** is a floating button at the bottom.
- In edit mode a bar at the bottom holds **Done** and the four actions on the selected
  book: move left, move right, next shelf, remove.

The four actions are always shown in edit mode and greyed out until a book is selected, so
the shelves do not jump when one is.

## Removing and deleting are asked about; moves are not

Removing a book and deleting a shelf cannot be undone inside the app, so each opens a
dialog that says what will happen and that no file is deleted. Moves are instant: the book
visibly moves, and moving it back is one tap.

## Deleting a shelf never orphans a book

A deleted shelf's books go, in order, to the end of the first shelf that is left. The last
remaining shelf cannot be deleted, so there is always somewhere for them to go. This is one
database transaction.

## Every edit is also a screen reader action on the spine

The brief asks for a non-drag way to reorder and for custom accessibility actions. Each
spine carries **Move left**, **Move right**, **Move to next shelf** and **Remove from
library** as actions, in and out of edit mode, so a TalkBack user rearranges a shelf from
the book they are on without finding the bar. Where a move is impossible (first book,
only shelf) the action is absent, not disabled. After a move the new position is spoken.

Long-press drag to reorder is not built. The brief calls it "the natural next step"; it is
the first candidate for Phase 5 polish. Nothing in the data layer needs to change for it.

## Spines are drawn, not composed

A spine is one draw call: colour, shading, rule, volume number, rotated title and format
tag, with the text measured once and cached. A shelf of a hundred books is a hundred draw
nodes, not several hundred text and layout nodes. The title is not a text node, so the
spine's label for screen readers is set by hand: title, volume, format, and "file not
found" when that applies. Reading progress joins the label in Phase 4, when there is some.

Spine size follows the brief: 22 to 44 dp wide by page count, 150 to 205 dp tall by format
(manga shortest, documents tallest) and page count.

## Spines grow with text size, up to a point

At 200% text a spine as wide as its text would leave two or three books on a shelf. Spines
and their text scale with the system text size up to 150% and stop there; the title is
then cut short on the spine and read in full by a screen reader. Everything that is
ordinary text (headers, the edit bar, dialogs) scales the whole way.

## Two panes without a navigation library, for now

Phase 0 planned to add Navigation 3 when the second screen arrived. The second screen is
here in outline only: the open shelf is a stand-in until the carousel is built in Phase 3,
and that screen's opening animation and predictive-back behaviour will decide how
navigation should work. Until then the layout is a small amount of plain code:

- under 840 dp wide: one pane, the open shelf covers the library, back returns;
- 840 dp and wider: library and open shelf side by side, 42:58, or 38:62 from 960 dp;
- a vertical hinge in a window at least 600 dp wide: half each.

These rules are one pure function with unit tests. The library's scroll positions survive
being covered and the window changing size. Navigation 3 is reconsidered at the start of
Phase 3.

There is no navigation bar or rail: the app has one top-level destination until settings
exist.

## The first migration

Adding files from a shelf's own slot has to remember which shelf, across the app being
closed mid-import, so the import queue gained a nullable `targetShelfId` column. That made
this database version 2, with a generated migration from version 1. If the shelf has gone
by the time the import runs, the book is shelved by format as before.

The migration test builds a version 1 database from its exported schema, fills every
table, and opens it with the current code. The smoke test also installs the previously
published build, runs it, and installs the new build over it.

## Walnut and amber by default

Phase 1 took its colours from the wallpaper on Android 12 and later. The brief wants the
bookcase palette by default and wallpaper colours as a setting, and with wooden shelves on
screen the difference now matters. The theme defaults to walnut and amber, with every
Material colour role defined for light and dark. The wallpaper option returns as a setting
when there is a settings screen.

## Not built in this phase

- **Dragging files into the library from another app.** A dropped file comes with a
  temporary permission, and the library keeps references, not copies. It needs a decision
  about copying dropped files first.
- **More than one window of the app at once.** Not attempted.
- **Automated accessibility scanning.** The standard scanner flags any target under 48 dp,
  which narrow spines are by design (the brief allows this where the action bar offers the
  same thing). It needs a tuned rule set; planned for Phase 5. Until then the UI tests
  assert labels, roles, states and actions directly.
- **Static analysis beyond style.** ktlint runs in CI as a report. detekt is not added
  yet; whether it works with Kotlin 2.4 has not been checked.

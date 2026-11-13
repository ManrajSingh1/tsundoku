# Gate 2 hand-over: Library and editing

Date: 2026-11-13. Status: passes in CI; waiting for a check on a real phone and with TalkBack.

The gate in the brief is "Shelf editing works on phone, fold, tablet and with TalkBack".
In CI, shelf editing works at phone, tablet and foldable window sizes, and through the
accessibility tree that TalkBack reads. Nothing has run on a real device, and nobody has
listened to it with TalkBack; both are yours to do. The build is the
[preview-latest pre-release](../../../../releases/tag/preview-latest), the code came in
through [pull request 2](../../../../pull/2), and the screenshots are on the
[ci-screenshots branch](../../../../tree/ci-screenshots).

## What was built

- **The bookcase.** Each shelf is its name and count, a strip of spines that scrolls
  sideways, and a plank. A spine shows a rule, the volume number (comics only), the title
  running down it, and CBZ, EPUB or PDF. Width follows page count, height follows format.
- **Opening a shelf.** Tapping a spine opens its shelf at that book: the cover, the title,
  one quiet line, and arrows to the books either side. This is the place the carousel takes
  in Phase 3.
- **Edit mode**, from **More** in the app bar. Tap a spine to select it (it lifts and gains
  an outline), then use the bar at the bottom: move left, move right, next shelf, remove.
  Each shelf has a name field and a delete button; a dashed slot at the end of each shelf
  adds files straight to it; a dashed button at the bottom adds a shelf.
- **Deleting a shelf** moves its books to the first shelf left. The last shelf cannot be
  deleted. **Removing a book** takes it out of the library and deletes its thumbnail; the
  file is never touched. Both ask first.
- **Two panes** on windows at least 840 dp wide and 480 dp tall: the library on the left,
  the open shelf on the right. An unfolded foldable is split in half at its hinge.
- **Screen readers.** Every spine is a button whose label is the title, volume and format.
  Move left, move right, move to next shelf and remove are actions on the spine itself, and
  the new position is spoken after a move.
- **The first database migration** (version 1 to 2), needed so files added from a shelf's
  slot go to that shelf even if the app is closed mid-import.
- **Default shelves keep their order.** Manga, Novels, Documents, whichever format is
  imported first. A library imported before this build keeps the order it has.

## How it was checked

| Check | Result |
| --- | --- |
| Unit tests on the JVM | 56 pass |
| Tests on an emulator: database, migration, import, and the library screen driven through its semantics | 55 pass |
| Smoke test of the minified build | Pass |
| Upgrade: previously published build installed and run, new build installed over it | Opens |
| Release APK asks for the network permission | No |
| Native libraries linked for 16 KB pages | All 8 |
| Android lint | 0 errors, 1 warning (a newer Gradle exists). Errors now fail the build. |
| ktlint | 0 findings. Report only, for now. |
| Release APK size | 22.7 MB |

The smoke test now drives the real app through the real database: import five files, open
a book and step to its neighbour, enter edit mode, move a book right, send it to the next
shelf, start to remove it and cancel, delete a shelf and confirm its books moved, repeat
part of that at 200% text, then again in a tablet-sized landscape window (two panes) and a
foldable-sized window, restart the app, and check everything is still where it was left.

The unit-test count above includes one test added after the last full CI run on the pull
request (for a phone on its side); the run on `main` after merging is the one that counts.

## What differs from the brief, and why

| The brief says | What was built | Why |
| --- | --- | --- |
| Edit and Done in the library header | Edit is under **More**; Done and the actions are in a bar at the bottom | The brief's own One UI rule: controls within thumb reach. It says to move Edit out of the header. |
| Tapping a spine opens the carousel | It opens one cover at a time, with arrows | The carousel is Phase 3. |
| A navigation bar or rail by window size | None | There is only one destination until settings exist. |
| Navigation 3 (planned in Phase 0) | Plain two-pane code with tests | The carousel's opening animation and predictive back will decide what navigation needs. Reconsidered at the start of Phase 3. |
| Drag and drop of files into the library | Not built | A dropped file arrives with a temporary permission, and the library keeps references, not copies. Needs a decision (below). |
| Multi-instance | Not built | Not attempted in this phase. |
| Long-press drag to reorder | Not built | The brief calls it the next step after the action bar. Proposed for Phase 5. |
| Spine text truncates at large text sizes | Spines grow up to 150%, then truncate | A shelf of spines sized for 200% text would show two or three books. |
| Dynamic colour as an optional setting | Off; walnut and amber always | There is no settings screen yet. Phase 1 had it on by default, which the brief did not want. |
| Static analysis in CI | ktlint only, as a report | detekt's support for Kotlin 2.4 is unchecked. |
| Automated accessibility checks | Semantics asserted test by test | The standard scanner fails narrow spines for size, which the brief allows. Needs tuning; Phase 5. |

Full reasoning is in [decision record 0004](../decisions/0004-phase-2-library.md).

## Not verified

- **Anything on a real device.** In particular: how the shelves look and scroll at 120 Hz
  on the S26 Ultra, and renaming a shelf with the Samsung keyboard.
- **TalkBack by ear.** The tests prove the labels, roles, states and actions exist and
  work. They cannot tell whether it sounds right or is pleasant to use.
- **A real hinge.** The emulator reported no hinge, so the half-and-half split is covered
  by unit tests only. So is the 42:58 split between 840 and 959 dp.
- **Samsung DeX, split screen, pop-up windows, keyboard, mouse and S Pen.** The layout
  resizes, and spines can be focused and activated from a keyboard in principle; none of it
  was tried.
- **A large library.** The shelves are lazy lists, but nothing over five books has been
  drawn. A 2,000-book test belongs with the performance work in Phase 5.
- **Shelves in right-to-left locales.** "Move left" and "Move right" assume a left-to-right
  shelf.

## A CI change you should know about

The emulator's renderer crashed the first time the shelves were drawn (a segfault in the
emulator itself, on the CI machine). CI now runs the emulator through a different software
renderer, and the crash has not recurred in three runs. Which drawing operation set it off
was not isolated. It is an emulator fault, not evidence about real devices, but if the
shelves misbehave on the phone, say so and this is the first place to look.

That crash also held one job for 45 minutes before a time limit was added. This phase used
roughly three and a half hours of GitHub Actions time in all.

## Decisions needed

1. **Drag to reorder.** Build it in Phase 5 as proposed, or before the carousel?
2. **Reordering shelves themselves.** The brief's edit mode has no way to move a whole
   shelf up or down. Small to add; say if you want it.
3. **Files dropped in from other apps.** To keep a dropped file the app would have to copy
   it into its own storage, which the library otherwise never does. Recommendation: leave
   it out of v1.

None of these blocks Phase 3.

## What to check on the phone

1. Install the new preview over the old one. Your library should still be there.
2. Look at the shelves, in light and dark. Scroll a long shelf sideways.
3. **More**, **Edit shelves**: select a book, move it, send it to the next shelf, remove
   one, rename a shelf, add a shelf, add files from a shelf's dashed slot, delete a shelf.
4. Turn TalkBack on and do step 3 again using the actions menu on a spine.
5. Set the text size to its largest and look again.
6. Turn the phone on its side.

# 0008: Backup to a file, and colour themes

Date: 2026-11-15. Status: accepted. Performance measurement and the accessibility pass
were dropped from Phase 5 at the owner's word: performance is fine on the phone, and an
accessibility pass is not wanted.

## Backup

**Settings, Backup** saves one small JSON file wherever the user chooses, and restores from
one. It holds what the app knows that is not in the user's own files:

- the shelves, in order, and the books on each, in order
- where the reader is in each book, and each book's reading direction
- the settings

It does not hold the books, and it does not hold covers or titles beyond a label for the
person reading the file: both are rebuilt from the book when it is added.

### A book is known by its contents, not its name

Each book is listed by the SHA-256 fingerprint the app already takes at import. A restore
therefore finds a book whatever its file is now called and wherever it now lives.

### Restoring on an empty phone

A restore on a new phone finds no books. It creates the shelves, applies the settings, and
keeps the rest of the backup aside. From then on, each file that is added is checked
against it: a book the backup knows goes straight to its shelf, its place on that shelf and
its page. Files can be added in any order and over any number of days. A backup taken in
the meantime still includes the books that have not come back yet.

What is kept aside is two plain files beside the database (the backup, and the fingerprints
already put back), deleted when nothing is left waiting. It is not a database table, so
there is no schema change and no migration.

### What a restore will and will not do

- It never removes a book or a shelf. Shelves named in the backup come first, in its
  order; shelves made since are kept after them. Books added since stay where they are.
- It does overwrite reading positions with the backup's, for the books the backup knows.
  That is what a restore is for, and the screen asks before it starts.
- A file that is not a backup, or is one from a newer version of the app, is refused and
  nothing changes. Sizes and counts inside a backup are capped: it is a file from outside.
- The steps are not one transaction. Each is safe to repeat, so a restore that is
  interrupted is finished by running it again.

Android's own backup stays off (it was never on): this file is the one way a library moves.

## Colour themes

Light, dark or "same as the phone" is one choice; the colours are now a second one.

| Colours | Light side | Dark side |
| --- | --- | --- |
| Walnut and amber | the app's own | the app's own |
| Wallpaper colours | Material You | Material You (Android 12 and later) |
| Catppuccin | Latte | Mocha |
| Dracula | Alucard | Dracula |
| Everforest | Light | Dark |
| Gruvbox | Light | Dark |
| Nord | Snow Storm | Polar Night |
| Rosé Pine | Dawn | Rosé Pine |
| Solarized | Light | Dark |
| Tokyo Night | Day | Night |

The earlier "Wallpaper colours" switch became one of the choices; a phone that had it on
keeps it.

### Ten colours in, a whole Material scheme out

Each side of each theme is entered as ten of its published colours (background, two raised
surfaces, outline, text, quiet text, three accents and red), named in the code by the
theme's own names for them. Every other colour Material asks for is worked out from those.

**Accents are nudged where they could not be read.** These themes were made for code
editors, where an accent is a highlight. An app also uses its accent as text on the
background (buttons, section titles), and some published shades are too faint for that,
mostly on the light sides: Everforest's green on its cream is 2.7:1. An accent that falls
short of 4.5:1 is moved towards the theme's text colour only as far as it takes. Colours
that already pass are used exactly as published. Unit tests hold every theme, both sides,
to those ratios.

### The shelves follow the theme

In the app's own colours and with wallpaper colours the shelves stay wood. In a named
theme a brown plank under a Nord screen looked like a mistake, so the shelf back and the
plank are built from the theme. Spine colours still come from each book's cover.

### Books' pages follow too

"Novel page colour: Same as the app" now gives an EPUB the theme's own background and text
colours. White, sepia and black are still there for anyone who wants plain pages. Comics
and PDFs are images of pages and are shown as they are.

The palettes were entered from each theme's published values as known at the time of
writing and were not checked against the upstream sources from this workspace.

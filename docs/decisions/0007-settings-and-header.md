# 0007: A settings screen, and a one-line header on the shelves

Date: 2026-11-15. Status: accepted. Phase 5 was started with these two items only.

## The shelves' header is one line

The library used One UI's extended app bar, as the brief asks: tall with a large title at
rest, condensing on scroll. On the phone it gave about a fifth of the screen to the word
"Tsundoku" before anything was scrolled. It is now a single 64 dp bar that does not change
height, with the title in the app's serif. This departs from the brief's One UI rule on
purpose: the shelves are what the screen is for.

## Settings is its own screen, reached from the menu

**More, Settings** opens one scrolling page. Everything takes effect as it is changed.

| Setting | Default | What it does |
| --- | --- | --- |
| Theme | Same as the phone | Light, dark, or follow the phone |
| Wallpaper colours | Off | Material You colours instead of walnut and amber |
| Flat covers | Off | Opens a shelf as flat, still covers instead of the 3D carousel |
| Comics read right to left | On | The direction used when a file does not say |
| Novel page colour | Same as the app | White, sepia or black pages in EPUBs |

EPUB text size stays in the reader, where its effect can be seen. A book's own reading
direction stays in the reader too; the setting here is only the default.

- **Theme uses Android's own per-app switch** (`UiModeManager.setApplicationNightMode`,
  Android 12 and later). Android remembers the choice and applies it to every screen
  before it is drawn, including Readium's, so nothing is done at start-up and nothing
  flashes. Before Android 12 the option is not shown and the app follows the phone.
- **Wallpaper colours** existed in the theme since Phase 1 and were switched off in Phase 2
  until there was somewhere to choose them. The first frame reads the stored choice
  directly so the app never starts in one palette and jumps to the other.
- **Flat covers** is the same fallback the carousel already uses when animations are
  removed in the system settings, now also available by choice.
- **It is an activity**, like the reader, opened over the library. There is still no
  navigation library; three screens do not need one.

Not included yet: backup to a file, a default shelf order, and licences. They belong to
the rest of Phase 5.

# 0003: Choices made building the foundations

Date: 2026-11-13. Status: accepted.

## Files are referenced, never copied

The brief asks that the user's files stay where they are. A book row holds the document URI
the system picker returned, and the app takes a lasting read grant for it. Nothing is
copied except a cover thumbnail.

Consequence: Android limits how many such grants one app may hold, and a picked folder
uses one grant for everything inside it. Large libraries should be added as a folder. The
library screen puts **Add folder** first for that reason.

Not yet measured: how fast the reading engines open a large archive through a document
URI rather than a file path. That is a Phase 4 question. If it is slow, the fallback is a
cache copy of the book being read, not of the library.

## A book is identified by its bytes

The SHA-256 of the whole file is stored and unique. The same file under another name, or
picked twice, is one book. A book whose file went missing and is picked again from a new
place is relinked instead of duplicated.

## The file's bytes decide its format

The first bytes say whether a file is a PDF or a zip, and an EPUB declares itself inside
the zip. The name and MIME type are only used to tell a comic archive from any other zip.
A text file renamed `.cbz` is refused.

## Readium reads every format at import

Metadata and covers for CBZ, EPUB and PDF come through Readium's streamer, so the app has
no parser of its own for zip archives, EPUB packages or PDF structure. The one exception is
`ComicInfo.xml`, a small flat XML file read with the platform's XML parser with document
types and entity expansion switched off.

## Images are bounded before they are decoded

A cover is decoded by reading its declared size first, refusing absurd sizes, and
downsampling to thumbnail size during the decode. A file a few bytes long that claims to
be 30,000 pixels square never gets memory allocated for it.

## The import queue lives in the database

Picked files go into an `import_queue` table and a WorkManager job drains it. The queue
survives the app being closed mid-import, has no size limit of the kind WorkManager's
input data has, and gives the screen its progress for free by observing the table.

The worker gets its collaborators from Hilt through an entry point. That avoids replacing
WorkManager's default initialiser.

## Room 3 on the platform's SQLite

Room 3 needs a SQLite driver. The app uses `AndroidSQLiteDriver`, which is the SQLite that
ships with Android, so no native library is bundled for the database.

## One shelf per book, enforced by an index

`shelf_books` is a join table with a unique index on the book. Allowing a book on several
shelves later means dropping that index, not reshaping the schema.

## Comics default to right to left

A comic whose file says nothing about direction is stored as "follow the default", and the
default is right to left, changeable in settings. The app is built around manga. A file
whose `ComicInfo.xml` says `Manga` is `YesAndRightToLeft` or `No` is stored as that
direction outright.

## A staging build for the smoke test

`staging` is the release build, minified identically, plus one screen that imports files
from the app's own files folder. CI cannot work the system file picker; this lets it
exercise the real import path in the code that ships. The screen does not exist in the
release APK.

## Deferred

- Navigation 3 is not added yet: there is one screen. It arrives with the second, in Phase 2.
- ktlint and detekt are not wired into CI yet. Android lint's fatal checks run as part of
  the release build.

package io.github.manrajsingh1.tsundoku.core.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.manrajsingh1.tsundoku.core.data.SettingsRepository
import io.github.manrajsingh1.tsundoku.core.database.BookEntity
import io.github.manrajsingh1.tsundoku.core.database.ReadingProgressEntity
import io.github.manrajsingh1.tsundoku.core.database.TsundokuDatabase
import io.github.manrajsingh1.tsundoku.core.model.AppPreferences
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.model.ColourTheme
import io.github.manrajsingh1.tsundoku.core.model.PageTheme
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.ThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Saving a library to a file and getting it back, on the same phone and on an empty one. */
@RunWith(AndroidJUnit4::class)
class BackupTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsRepository(context)
    private lateinit var workspace: File
    private val databases = ArrayList<TsundokuDatabase>()

    /** One installation of the app: its own database and its own place for a waiting restore. */
    private inner class Phone(name: String) {
        val database = TsundokuDatabase.inMemory(context).also { databases += it }
        val books = database.bookDao()
        val shelves = database.shelfDao()
        val progress = database.readingProgressDao()
        val pending = PendingRestore(File(workspace, name).apply { mkdirs() }, books, shelves, progress)
        val backups = BackupRepository(context, books, shelves, progress, settings, pending)

        suspend fun add(hash: String, shelf: String): Long = shelves.addBookToShelf(book(hash), shelf)

        /** A book arriving through import: shelved by format, then offered to a waiting restore. */
        suspend fun import(hash: String): Long = add(hash, "Inbox").also { pending.place(it, hash) }

        suspend fun layout(): Map<String, List<String>> = shelves.allShelves().associate { shelf ->
            shelf.name to shelves.links(shelf.id).map { books.findById(it.bookId)!!.contentHash }
        }
    }

    private fun book(hash: String) = BookEntity(
        documentUri = "content://test/$hash",
        contentHash = hash,
        format = BookFormat.CBZ,
        title = "Book $hash",
        author = null,
        series = null,
        volume = null,
        pageCount = 10,
        fileSize = 1L,
        coverPath = null,
        spineColor = 0,
        readingDirection = ReadingDirection.AUTO,
        addedAt = 0L,
        lastOpenedAt = null,
    )

    @Before
    fun setUp() {
        workspace = File(context.cacheDir, "backup-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() = runBlocking {
        databases.forEach { it.close() }
        workspace.deleteRecursively()
        settings.setThemeMode(ThemeMode.SYSTEM)
        settings.setColourTheme(ColourTheme.TSUNDOKU)
        settings.setFlatCarousel(false)
        settings.setComicReadingDirection(ReadingDirection.RIGHT_TO_LEFT)
        settings.setEpubPageTheme(PageTheme.SYSTEM)
        settings.setEpubFontSize(1.0)
    }

    /** A library with two shelves, one book part-read and one turned round. */
    private suspend fun Phone.stock() {
        val a = add("a", "Manga")
        val b = add("b", "Manga")
        add("c", "Manga")
        add("n", "Novels")
        progress.save(ReadingProgressEntity(a, "12", 0.4, 1_000L))
        books.setReadingDirection(b, ReadingDirection.LEFT_TO_RIGHT)
        shelves.setShelfOrder(shelves.findByName("Manga")!!.id, listOf(shelves.linkFor(b)!!.bookId, a))
    }

    @Test
    fun aBackupSurvivesBeingWrittenAndReadBack() {
        val backup = LibraryBackup(
            exportedAt = 42L,
            preferences = AppPreferences(
                themeMode = ThemeMode.DARK,
                colourTheme = ColourTheme.CATPPUCCIN,
                flatCarousel = true,
                comicReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
                epubPageTheme = PageTheme.SEPIA,
            ),
            epubFontSize = 1.3,
            shelves = listOf(
                BackupShelf("Manga", listOf(BackupBook("h1", "One", ReadingDirection.RIGHT_TO_LEFT, "7", 0.5, 99L), BackupBook("h2", "Two"))),
                BackupShelf("Empty", emptyList()),
            ),
        )

        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }

    @Test
    fun whatIsNotABackupIsTurnedAway() {
        assertNull(BackupCodec.decode("not json at all"))
        assertNull(BackupCodec.decode("""{"some":"other file"}"""))
        assertNull(BackupCodec.decode("""{"format":"tsundoku-backup","version":99,"shelves":[]}"""))
    }

    @Test
    fun aBackupWithOddContentsIsReadAsFarAsItMakesSense() {
        val backup = BackupCodec.decode(
            """
            {"format":"tsundoku-backup","version":1,
             "settings":{"themeMode":"PURPLE","colourTheme":"NORD"},
             "shelves":[
               {"name":"Manga","books":[{"hash":"a","title":"A","fraction":7,"position":"3"},{"hash":"a","title":"again"},{"title":"no hash"}]},
               {"name":"  ","books":[{"hash":"lost"}]},
               {"name":"Manga","books":[{"hash":"b","direction":"SIDEWAYS"}]}
             ]}
            """.trimIndent(),
        )!!

        assertEquals(ThemeMode.SYSTEM, backup.preferences.themeMode)
        assertEquals(ColourTheme.NORD, backup.preferences.colourTheme)
        assertEquals(listOf("Manga"), backup.shelves.map { it.name })
        assertEquals(listOf("a", "b"), backup.shelves[0].books.map { it.hash })
        assertEquals(1.0, backup.shelves[0].books[0].fraction!!, 0.0)
        assertEquals(ReadingDirection.AUTO, backup.shelves[0].books[1].direction)
    }

    @Test
    fun aRearrangedLibraryIsPutBackAsTheBackupHadIt() = runBlocking {
        val phone = Phone("same")
        phone.stock()
        val file = Uri.fromFile(File(workspace, "backup.json"))
        assertEquals(BackupSummary(shelves = 2, books = 4), phone.backups.save(file))

        // Since the backup: a book moved shelf, a shelf was reordered, progress went on.
        val c = phone.books.findByHash("c")!!.id
        phone.shelves.moveBookToNextShelf(c)
        phone.progress.save(ReadingProgressEntity(phone.books.findByHash("a")!!.id, "99", 0.9, 2_000L))
        phone.add("new", "Later")

        val result = phone.backups.restore(file)

        assertEquals(RestoreResult.Restored(shelves = 2, placed = 4, waiting = 0), result)
        // Shelves from the backup come first, in its order; one made since is kept, after them.
        assertEquals(
            linkedMapOf("Manga" to listOf("b", "a", "c"), "Novels" to listOf("n"), "Later" to listOf("new")).toList(),
            phone.layout().toList(),
        )
        assertEquals("12", phone.progress.find(phone.books.findByHash("a")!!.id)!!.position)
        assertTrue(phone.pending.waiting().isEmpty())
    }

    @Test
    fun onAnEmptyPhoneBooksReturnToTheirPlacesAsTheirFilesAreAdded() = runBlocking {
        val old = Phone("old")
        old.stock()
        val file = Uri.fromFile(File(workspace, "backup.json"))
        old.backups.save(file)

        val new = Phone("new")
        val result = new.backups.restore(file)
        assertEquals(RestoreResult.Restored(shelves = 2, placed = 0, waiting = 4), result)
        assertEquals(listOf("Manga", "Novels"), new.shelves.allShelves().map { it.name })

        // Files are added in any order; each goes to its shelf and its place on it.
        val c = new.import("c")
        assertEquals(listOf("c"), new.layout()["Manga"])
        val a = new.import("a")
        assertEquals(listOf("a", "c"), new.layout()["Manga"])
        val b = new.import("b")
        assertEquals(listOf("b", "a", "c"), new.layout()["Manga"])
        assertEquals(ReadingDirection.LEFT_TO_RIGHT, new.books.findById(b)!!.readingDirection)
        assertEquals(0.4, new.progress.find(a)!!.percent, 0.0)
        assertNull(new.progress.find(c))

        // A book the backup never knew stays where import put it.
        new.import("stranger")
        assertEquals(listOf("stranger"), new.layout()["Inbox"])

        // One book is still to come, and a second installation over the same files knows it.
        assertEquals(listOf("Novels" to "n"), new.pending.waiting().map { it.first to it.second.hash })
        val afterRestart = PendingRestore(File(workspace, "new"), new.books, new.shelves, new.progress)
        assertEquals(listOf("n"), afterRestart.waiting().map { it.second.hash })

        // A backup taken now still remembers the book that has not come back yet.
        assertTrue(new.backups.snapshot().shelves.first { it.name == "Novels" }.books.any { it.hash == "n" })

        new.import("n")
        assertEquals(listOf("n"), new.layout()["Novels"])
        assertTrue(new.pending.waiting().isEmpty())
        assertFalse(File(File(workspace, "new"), "restore-pending.json").exists())
    }

    @Test
    fun settingsComeBackWithTheLibrary() = runBlocking {
        val old = Phone("old")
        settings.setColourTheme(ColourTheme.GRUVBOX)
        settings.setFlatCarousel(true)
        settings.setEpubFontSize(1.4)
        val file = Uri.fromFile(File(workspace, "backup.json"))
        old.backups.save(file)
        settings.setColourTheme(ColourTheme.TSUNDOKU)
        settings.setFlatCarousel(false)
        settings.setEpubFontSize(1.0)

        Phone("new").backups.restore(file)

        val restored = settings.preferences.first()
        assertEquals(ColourTheme.GRUVBOX, restored.colourTheme)
        assertTrue(restored.flatCarousel)
        assertEquals(1.4, settings.epubFontSize.first(), 1e-9)
    }

    @Test
    fun aFileThatIsNotABackupChangesNothing() = runBlocking {
        val phone = Phone("same")
        phone.stock()
        val before = phone.layout()
        val notABackup = File(workspace, "holiday.json").apply { writeText("""{"photos":[1,2,3]}""") }

        assertEquals(RestoreResult.NotABackup, phone.backups.restore(Uri.fromFile(notABackup)))
        assertEquals(RestoreResult.Unreadable, phone.backups.restore(Uri.fromFile(File(workspace, "missing.json"))))
        assertEquals(before, phone.layout())
    }
}

package io.github.manrajsingh1.tsundoku.core.data.importing

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.manrajsingh1.tsundoku.core.data.OfflineLibraryRepository
import io.github.manrajsingh1.tsundoku.core.database.ImportItemEntity
import io.github.manrajsingh1.tsundoku.core.database.TsundokuDatabase
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.model.ImportState
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.importing.SpineColour
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Phase 1 gate, as a test: a CBZ, an EPUB and a PDF each go from a file on the device to
 * a shelved book with its metadata and cover, through the same importer the app uses, with
 * the real Readium engines and a real database.
 */
@RunWith(AndroidJUnit4::class)
class ImportEndToEndTest {

    private lateinit var context: Context
    private lateinit var database: TsundokuDatabase
    private lateinit var importer: BookImporter
    private lateinit var library: OfflineLibraryRepository
    private lateinit var workspace: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = TsundokuDatabase.inMemory(context)
        val documents = DocumentSource(context)
        importer = BookImporter(
            context = context,
            documents = documents,
            bookDao = database.bookDao(),
            shelfDao = database.shelfDao(),
            inspector = ReadiumPublicationInspector(context),
            covers = CoverStore(context),
        )
        library = OfflineLibraryRepository(database.bookDao(), database.shelfDao(), CoverStore(context), documents)
        workspace = File(context.cacheDir, "import-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        database.close()
        workspace.deleteRecursively()
    }

    private fun file(name: String) = File(workspace, name)

    private suspend fun shelf(name: String): List<Book> =
        library.observeShelves().first().firstOrNull { it.shelf.name == name }?.books.orEmpty()

    private fun assertCoverSaved(book: Book) {
        val path = book.coverPath
        assertNotNull("${book.title} has no cover", path)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        assertTrue("The cover is not a readable image", bounds.outWidth > 0 && bounds.outHeight > 0)
        assertTrue("The cover was stored larger than a thumbnail", bounds.outWidth <= CoverStore.MAX_WIDTH)
        assertTrue(bounds.outHeight <= CoverStore.MAX_HEIGHT)
        assertTrue(
            "White spine text would not be readable on this spine colour",
            SpineColour.contrastRatio(book.spineColor, 0xFFFFFFFF.toInt()) >= SpineColour.MIN_CONTRAST,
        )
    }

    @Test
    fun aComicIsImportedWithItsMetadataDirectionAndCover() = runBlocking {
        val cbz = Fixtures.cbz(file("Test Manga v03 (Digital).cbz"), pages = 3, comicInfo = Fixtures.MANGA_COMIC_INFO)

        val outcome = importer.import(Uri.fromFile(cbz))

        assertEquals(ImportState.IMPORTED, outcome.state)
        val book = shelf("Manga").single()
        assertEquals(BookFormat.CBZ, book.format)
        assertEquals("The Third Volume", book.title)
        assertEquals("Test Manga", book.series)
        assertEquals(3.0, book.volume!!, 0.0)
        assertEquals("A. Author", book.author)
        assertEquals(3, book.pageCount)
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, book.readingDirection)
        assertEquals(cbz.length(), book.fileSize)
        assertCoverSaved(book)
    }

    @Test
    fun aComicWithNoMetadataTakesItsSeriesAndVolumeFromItsName() = runBlocking {
        val cbz = Fixtures.cbz(file("Quiet Shelf v07 [Scans].cbz"), pages = 5)

        importer.import(Uri.fromFile(cbz))

        val book = shelf("Manga").single()
        assertEquals("Quiet Shelf v07", book.title)
        assertEquals("Quiet Shelf", book.series)
        assertEquals(7.0, book.volume!!, 0.0)
        assertEquals(5, book.pageCount)
        // Nothing in the file states a direction, so the reader will follow the app default.
        assertEquals(ReadingDirection.AUTO, book.readingDirection)
    }

    @Test
    fun anEpubIsImportedWithItsTitleAuthorAndDeclaredCover() = runBlocking {
        val epub = Fixtures.epub(file("download-4812.epub"))

        val outcome = importer.import(Uri.fromFile(epub))

        assertEquals(ImportState.IMPORTED, outcome.state)
        val book = shelf("Novels").single()
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("A Test Novel", book.title)
        assertEquals("Nora Novelist", book.author)
        assertNull(book.series)
        assertCoverSaved(book)
    }

    @Test
    fun aPdfIsImportedWithItsPageCountAndFirstPageAsCover() = runBlocking {
        val pdf = Fixtures.pdf(file("Quarterly report.pdf"), pages = 5)

        val outcome = importer.import(Uri.fromFile(pdf))

        assertEquals(ImportState.IMPORTED, outcome.state)
        val book = shelf("Documents").single()
        assertEquals(BookFormat.PDF, book.format)
        assertEquals("Quarterly report", book.title)
        assertEquals(5, book.pageCount)
        assertCoverSaved(book)
    }

    @Test
    fun theThreeFormatsLandOnThreeShelvesInTheOrderTheyArrived() = runBlocking {
        importer.import(Uri.fromFile(Fixtures.pdf(file("a.pdf"))))
        importer.import(Uri.fromFile(Fixtures.cbz(file("b.cbz"))))
        importer.import(Uri.fromFile(Fixtures.epub(file("c.epub"))))

        val shelves = library.observeShelves().first()
        assertEquals(listOf("Documents", "Manga", "Novels"), shelves.map { it.shelf.name })
        assertEquals(listOf(1, 1, 1), shelves.map { it.books.size })
    }

    @Test
    fun theSameFileUnderAnotherNameIsRecognisedAsADuplicate() = runBlocking {
        val original = Fixtures.cbz(file("original.cbz"), comicInfo = Fixtures.MANGA_COMIC_INFO)
        val copy = original.copyTo(file("renamed copy.cbz"))

        assertEquals(ImportState.IMPORTED, importer.import(Uri.fromFile(original)).state)
        val second = importer.import(Uri.fromFile(copy))

        assertEquals(ImportState.DUPLICATE, second.state)
        assertEquals(1, database.bookDao().count())
    }

    @Test
    fun aMissingBookPickedAgainFromItsNewPlaceIsRelinked() = runBlocking {
        val original = Fixtures.pdf(file("moved.pdf"))
        val id = importer.import(Uri.fromFile(original)).bookId!!
        database.bookDao().setMissing(id, true)
        val moved = original.copyTo(file("elsewhere.pdf"))

        val outcome = importer.import(Uri.fromFile(moved))

        assertEquals(ImportState.DUPLICATE, outcome.state)
        val book = database.bookDao().findById(id)!!
        assertEquals(Uri.fromFile(moved).toString(), book.documentUri)
        assertEquals(false, book.isMissing)
    }

    @Test
    fun aFileThatIsNotABookIsRefusedWhateverItIsCalled() = runBlocking {
        val impostor = file("not really.cbz").apply { writeText("This is a text file with a comic's name.") }

        val outcome = importer.import(Uri.fromFile(impostor))

        assertEquals(ImportState.UNSUPPORTED, outcome.state)
        assertEquals(0, database.bookDao().count())
    }

    @Test
    fun aTruncatedPdfFailsCleanlyAndLeavesNothingBehind() = runBlocking {
        val whole = Fixtures.pdf(file("whole.pdf"), pages = 5)
        val truncated = file("truncated.pdf").apply { writeBytes(whole.readBytes().copyOf(60)) }

        val outcome = importer.import(Uri.fromFile(truncated))

        assertEquals(ImportState.FAILED, outcome.state)
        assertNotNull(outcome.message)
        assertEquals(0, database.bookDao().count())
    }

    @Test
    fun anArchiveWithNoPagesFails() = runBlocking {
        val empty = Fixtures.cbz(file("empty.cbz"), pages = 0, comicInfo = Fixtures.MANGA_COMIC_INFO)

        val outcome = importer.import(Uri.fromFile(empty))

        assertEquals(ImportState.FAILED, outcome.state)
        assertEquals(0, database.bookDao().count())
    }

    @Test
    fun aFileThatHasGoneFailsWithoutThrowing() = runBlocking {
        val outcome = importer.import(Uri.fromFile(file("never existed.pdf")))

        assertEquals(ImportState.FAILED, outcome.state)
    }

    @Test
    fun theQueueRunnerImportsEveryPendingFileAndRecordsEachOutcome() = runBlocking {
        val queue = database.importQueueDao()
        val good = Fixtures.epub(file("good.epub"))
        val bad = file("bad.pdf").apply { writeText("not a pdf") }
        queue.insertAll(
            listOf(
                ImportItemEntity(uri = Uri.fromFile(good).toString(), isTree = false, createdAt = 1L),
                ImportItemEntity(uri = Uri.fromFile(bad).toString(), isTree = false, createdAt = 1L),
                ImportItemEntity(uri = Uri.fromFile(good).toString(), isTree = false, createdAt = 1L),
            ),
        )

        ImportRunner(queue, DocumentSource(context), importer).drain()

        assertNull(queue.nextPending())
        assertEquals(
            listOf(ImportState.IMPORTED, ImportState.UNSUPPORTED, ImportState.DUPLICATE),
            queue.finished().map { it.state },
        )
        assertEquals(1, database.bookDao().count())
    }

    @Test
    fun aBookAddedToAChosenShelfLandsThereWhateverItsFormat() = runBlocking {
        val favourites = library.createShelf("Favourites")
        val queue = database.importQueueDao()
        val pdf = Fixtures.pdf(file("chosen.pdf"))
        queue.insertAll(
            listOf(
                ImportItemEntity(
                    uri = Uri.fromFile(pdf).toString(),
                    isTree = false,
                    createdAt = 1L,
                    targetShelfId = favourites,
                ),
            ),
        )

        ImportRunner(queue, DocumentSource(context), importer).drain()

        assertEquals(1, shelf("Favourites").size)
        assertTrue("The book was also shelved by format", shelf("Documents").isEmpty())
    }

    @Test
    fun aBookAddedToAShelfThatHasGoneFallsBackToItsFormatsShelf() = runBlocking {
        val outcome = importer.import(Uri.fromFile(Fixtures.pdf(file("orphan.pdf"))), targetShelfId = 9_999L)

        assertEquals(ImportState.IMPORTED, outcome.state)
        assertEquals(1, shelf("Documents").size)
    }

    @Test
    fun removingABookDeletesItsThumbnailButNeverTheUsersFile() = runBlocking {
        val cbz = Fixtures.cbz(file("Keep Me v01.cbz"), pages = 2)
        val id = importer.import(Uri.fromFile(cbz)).bookId!!
        val cover = File(shelf("Manga").single().coverPath!!)
        assertTrue(cover.exists())

        library.removeBook(id)

        assertTrue(shelf("Manga").isEmpty())
        assertTrue("The thumbnail was left behind", !cover.exists())
        assertTrue("The user's file was deleted", cbz.exists())
        // Removed, not blocked: the same file can be added again later.
        assertEquals(ImportState.IMPORTED, importer.import(Uri.fromFile(cbz)).state)
    }
}

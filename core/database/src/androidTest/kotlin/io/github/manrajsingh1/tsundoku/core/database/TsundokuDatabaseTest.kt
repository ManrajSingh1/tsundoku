package io.github.manrajsingh1.tsundoku.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.model.DefaultShelf
import io.github.manrajsingh1.tsundoku.core.model.ImportState
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TsundokuDatabaseTest {

    private lateinit var database: TsundokuDatabase

    @Before
    fun open() {
        database = TsundokuDatabase.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun close() {
        database.close()
    }

    private fun book(hash: String, title: String = "Book $hash") = BookEntity(
        documentUri = "content://test/$hash",
        contentHash = hash,
        format = BookFormat.CBZ,
        title = title,
        author = null,
        series = null,
        volume = null,
        pageCount = 10,
        fileSize = 1_000,
        coverPath = null,
        spineColor = 0xFF223344.toInt(),
        readingDirection = ReadingDirection.RIGHT_TO_LEFT,
        addedAt = 1L,
        lastOpenedAt = null,
    )

    @Test
    fun aBookGoesToTheEndOfItsShelfAndTheShelfIsCreatedOnce() = runTest {
        val shelves = database.shelfDao()
        shelves.addBookToShelf(book("a"), "Manga")
        shelves.addBookToShelf(book("b"), "Manga")
        shelves.addBookToShelf(book("c"), "Novels")

        val allShelves = shelves.observeAll().first()
        assertEquals(listOf("Manga", "Novels"), allShelves.map { it.name })
        assertEquals(listOf(0, 1), allShelves.map { it.position })

        val shelved = database.bookDao().observeShelved().first()
        assertEquals(listOf("a", "b", "c"), shelved.map { it.book.contentHash })
        assertEquals(listOf(0, 1, 0), shelved.map { it.position })
    }

    @Test
    fun enumsAndNullsSurviveARoundTrip() = runTest {
        val id = database.shelfDao().addBookToShelf(book("a"), "Manga")
        val stored = database.bookDao().findById(id)
        assertNotNull(stored)
        assertEquals(BookFormat.CBZ, stored!!.format)
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, stored.readingDirection)
        assertNull(stored.author)
        assertEquals(0xFF223344.toInt(), stored.spineColor)
    }

    @Test
    fun theSameContentCannotBeStoredTwice() = runTest {
        database.shelfDao().addBookToShelf(book("same"), "Manga")
        try {
            database.shelfDao().addBookToShelf(book("same", title = "A copy"), "Manga")
            fail("A second book with the same content hash was accepted")
        } catch (expected: Exception) {
            // The unique index refused it.
        }
        assertEquals(1, database.bookDao().count())
        // The failed insert rolled back whole: no stray shelf link was left behind.
        assertEquals(1, database.bookDao().observeShelved().first().size)
    }

    @Test
    fun deletingABookRemovesItFromItsShelfAndDropsItsProgress() = runTest {
        val id = database.shelfDao().addBookToShelf(book("a"), "Manga")
        database.readingProgressDao().save(ReadingProgressEntity(id, position = "4", percent = 0.4, updatedAt = 2L))

        database.bookDao().delete(id)

        assertTrue(database.bookDao().observeShelved().first().isEmpty())
        assertNull(database.readingProgressDao().find(id))
    }

    @Test
    fun aMovedFileIsRelinkedAndNoLongerMissing() = runTest {
        val id = database.shelfDao().addBookToShelf(book("a"), "Manga")
        database.bookDao().setMissing(id, true)
        database.bookDao().relink(id, "content://test/moved")

        val stored = database.bookDao().findById(id)!!
        assertEquals("content://test/moved", stored.documentUri)
        assertEquals(false, stored.isMissing)
    }

    @Test
    fun theImportQueueHandsOutPendingItemsInOrderAndCountsOutcomes() = runTest {
        val queue = database.importQueueDao()
        queue.insertAll(
            listOf(
                ImportItemEntity(uri = "content://test/1", isTree = false, createdAt = 1L),
                ImportItemEntity(uri = "content://test/2", isTree = false, createdAt = 1L),
                ImportItemEntity(uri = "content://test/3", isTree = false, createdAt = 1L),
            ),
        )

        val first = queue.nextPending()!!
        assertEquals("content://test/1", first.uri)
        queue.finish(first.id, ImportState.IMPORTED, "One", null)
        val second = queue.nextPending()!!
        assertEquals("content://test/2", second.uri)
        queue.finish(second.id, ImportState.DUPLICATE, "Two", null)

        val counts = queue.observeCounts().first().associate { it.state to it.count }
        assertEquals(1, counts[ImportState.PENDING])
        assertEquals(1, counts[ImportState.IMPORTED])
        assertEquals(1, counts[ImportState.DUPLICATE])

        queue.clearFinished()
        assertEquals(listOf(ImportState.PENDING), queue.observeCounts().first().map { it.state })
    }

    private suspend fun order(shelfName: String): List<String> {
        val shelf = database.shelfDao().findByName(shelfName) ?: return emptyList()
        return database.bookDao().observeShelved().first()
            .filter { it.shelfId == shelf.id }
            .sortedBy { it.position }
            .map { it.book.contentHash }
    }

    @Test
    fun aBookMovesAlongItsShelfAndStopsAtTheEnds() = runTest {
        val shelves = database.shelfDao()
        val a = shelves.addBookToShelf(book("a"), "Manga")
        shelves.addBookToShelf(book("b"), "Manga")
        val c = shelves.addBookToShelf(book("c"), "Manga")

        assertTrue(shelves.moveBook(a, 1))
        assertEquals(listOf("b", "a", "c"), order("Manga"))

        assertTrue(shelves.moveBook(c, -2))
        assertEquals(listOf("c", "b", "a"), order("Manga"))

        // Already at the left end: nothing moves and nothing is renumbered.
        assertEquals(false, shelves.moveBook(c, -1))
        assertEquals(false, shelves.moveBook(a, 1))
        assertEquals(listOf("c", "b", "a"), order("Manga"))
    }

    @Test
    fun movingABookClosesGapsLeftByRemovedBooks() = runTest {
        val shelves = database.shelfDao()
        shelves.addBookToShelf(book("a"), "Manga")
        val b = shelves.addBookToShelf(book("b"), "Manga")
        shelves.addBookToShelf(book("c"), "Manga")
        val d = shelves.addBookToShelf(book("d"), "Manga")
        database.bookDao().delete(b)

        shelves.moveBook(d, -1)

        assertEquals(listOf("a", "d", "c"), order("Manga"))
        val positions = database.bookDao().observeShelved().first().map { it.position }.sorted()
        assertEquals(listOf(0, 1, 2), positions)
    }

    @Test
    fun aBookMovesToTheEndOfTheNextShelfAndRoundFromTheLast() = runTest {
        val shelves = database.shelfDao()
        val a = shelves.addBookToShelf(book("a"), "Manga")
        shelves.addBookToShelf(book("b"), "Novels")

        assertEquals("Novels", shelves.moveBookToNextShelf(a)?.name)
        assertEquals(emptyList<String>(), order("Manga"))
        assertEquals(listOf("b", "a"), order("Novels"))

        assertEquals("Manga", shelves.moveBookToNextShelf(a)?.name)
        assertEquals(listOf("a"), order("Manga"))
    }

    @Test
    fun withOneShelfThereIsNoNextShelfToMoveTo() = runTest {
        val a = database.shelfDao().addBookToShelf(book("a"), "Manga")
        assertNull(database.shelfDao().moveBookToNextShelf(a))
        assertEquals(listOf("a"), order("Manga"))
    }

    @Test
    fun deletingAShelfMovesItsBooksToTheFirstShelfInOrder() = runTest {
        val shelves = database.shelfDao()
        shelves.addBookToShelf(book("a"), "Manga")
        shelves.addBookToShelf(book("b"), "Novels")
        shelves.addBookToShelf(book("c"), "Novels")
        val novels = shelves.findByName("Novels")!!

        assertEquals("Manga", shelves.deleteShelfKeepingBooks(novels.id)?.name)

        assertEquals(listOf("Manga"), shelves.observeAll().first().map { it.name })
        assertEquals(listOf("a", "b", "c"), order("Manga"))
        assertEquals(3, database.bookDao().count())
    }

    @Test
    fun deletingTheFirstShelfMovesItsBooksToTheNextOne() = runTest {
        val shelves = database.shelfDao()
        shelves.addBookToShelf(book("a"), "Manga")
        shelves.addBookToShelf(book("b"), "Novels")
        val manga = shelves.findByName("Manga")!!

        assertEquals("Novels", shelves.deleteShelfKeepingBooks(manga.id)?.name)

        assertEquals(listOf("b", "a"), order("Novels"))
    }

    @Test
    fun theLastShelfCannotBeDeleted() = runTest {
        val shelves = database.shelfDao()
        shelves.addBookToShelf(book("a"), "Manga")
        val manga = shelves.findByName("Manga")!!

        assertNull(shelves.deleteShelfKeepingBooks(manga.id))

        assertEquals(listOf("a"), order("Manga"))
    }

    @Test
    fun aNewShelfGoesLastAndCanBeRenamed() = runTest {
        val shelves = database.shelfDao()
        shelves.addBookToShelf(book("a"), "Manga")
        val id = shelves.createShelf("New shelf")
        shelves.renameShelf(id, "Favourites")

        val all = shelves.observeAll().first()
        assertEquals(listOf("Manga", "Favourites"), all.map { it.name })
        assertEquals(listOf(0, 1), all.map { it.position })
    }

    @Test
    fun anImportItemRemembersTheShelfItWasAddedTo() = runTest {
        val queue = database.importQueueDao()
        queue.insertAll(
            listOf(
                ImportItemEntity(uri = "content://test/1", isTree = false, createdAt = 1L, targetShelfId = 7L),
                ImportItemEntity(uri = "content://test/2", isTree = false, createdAt = 1L),
            ),
        )
        val first = queue.nextPending()!!
        assertEquals(7L, first.targetShelfId)
        queue.finish(first.id, ImportState.IMPORTED, "One", null)
        assertNull(queue.nextPending()!!.targetShelfId)
    }

    @Test
    fun theDefaultShelvesStandInTheirOwnOrderAboveShelvesTheUserAdds() = runTest {
        val shelves = database.shelfDao()
        // Imported in the "wrong" order, with a shelf of the user's own made in between.
        shelves.addBookToShelfOrDefault(book("a"), null, "Documents", DefaultShelf.DOCUMENTS.position)
        shelves.addBookToShelfOrDefault(book("b"), null, "Manga", DefaultShelf.MANGA.position)
        val mine = shelves.createShelf("Mine")
        shelves.addBookToShelfOrDefault(book("c"), null, "Novels", DefaultShelf.NOVELS.position)
        shelves.addBookToShelfOrDefault(book("d"), mine, "Manga", DefaultShelf.MANGA.position)

        assertEquals(listOf("Manga", "Novels", "Documents", "Mine"), shelves.observeAll().first().map { it.name })
        assertEquals(listOf("d"), order("Mine"))
        assertEquals(listOf("b"), order("Manga"))
    }
}

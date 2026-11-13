package io.github.manrajsingh1.tsundoku.feature.library

import io.github.manrajsingh1.tsundoku.core.data.LibraryRepository
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.Shelf
import io.github.manrajsingh1.tsundoku.core.model.ShelfWithBooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    /** Records what it was asked to do; the shelves it reports are set by each test. */
    private class FakeLibraryRepository : LibraryRepository {
        val shelves = MutableSharedFlow<List<ShelfWithBooks>>(replay = 1)
        val calls = ArrayList<String>()
        var nextShelf: Shelf? = null
        var shelfBooksMoveTo: Shelf? = null

        override fun observeShelves(): Flow<List<ShelfWithBooks>> = shelves

        override suspend fun moveBook(bookId: Long, delta: Int) {
            calls += "move $bookId by $delta"
        }

        override suspend fun moveBookToNextShelf(bookId: Long): Shelf? {
            calls += "next shelf $bookId"
            return nextShelf
        }

        override suspend fun removeBook(bookId: Long) {
            calls += "remove $bookId"
        }

        override suspend fun createShelf(name: String): Long {
            calls += "create $name"
            return 99L
        }

        override suspend fun renameShelf(shelfId: Long, name: String) {
            calls += "rename $shelfId to $name"
        }

        override suspend fun deleteShelf(shelfId: Long): Shelf? {
            calls += "delete $shelfId"
            return shelfBooksMoveTo
        }
    }

    private val repository = FakeLibraryRepository()

    // One scheduler for the test body and for the view model's scope, advanced by hand, so
    // each assertion runs at a known point.
    private val dispatcher = StandardTestDispatcher()

    private val manga = Shelf(id = 1, name = "Manga", position = 0)
    private val novels = Shelf(id = 2, name = "Novels", position = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun book(id: Long) = Book(
        id = id,
        documentUri = "content://test/$id",
        contentHash = "hash$id",
        format = BookFormat.CBZ,
        title = "Book $id",
        author = null,
        series = null,
        volume = null,
        pageCount = 10,
        fileSize = 1L,
        coverPath = null,
        spineColor = 0xFF000000.toInt(),
        readingDirection = ReadingDirection.AUTO,
        addedAt = 0L,
        lastOpenedAt = null,
        isMissing = false,
    )

    /** A view model that has loaded [shelves], with its messages gathered into the returned list. */
    private suspend fun TestScope.loaded(
        vararg shelves: ShelfWithBooks,
    ): Pair<LibraryViewModel, List<LibraryMessage>> {
        val viewModel = LibraryViewModel(repository)
        val messages = ArrayList<LibraryMessage>()
        backgroundScope.launch { viewModel.uiState.collect() }
        backgroundScope.launch { viewModel.messages.collect { messages += it } }
        runCurrent()
        repository.shelves.emit(shelves.toList())
        runCurrent()
        return viewModel to messages
    }

    @Test
    fun `starts as loading until the database answers`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository)
        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `shows the shelves the repository emits and follows later changes`() = runTest(dispatcher) {
        val (viewModel, _) = loaded(ShelfWithBooks(manga, listOf(book(1))))
        val first = viewModel.uiState.value as LibraryUiState.Loaded
        assertEquals(listOf("Manga"), first.shelves.map { it.shelf.name })
        assertEquals(false, first.isEmpty)

        repository.shelves.emit(listOf(ShelfWithBooks(manga, listOf(book(1), book(2)))))
        runCurrent()
        val second = viewModel.uiState.value as LibraryUiState.Loaded
        assertEquals(2, second.shelves.single().books.size)
    }

    @Test
    fun `a library whose shelves hold no books counts as empty`() = runTest(dispatcher) {
        val (viewModel, _) = loaded()
        assertTrue((viewModel.uiState.value as LibraryUiState.Loaded).isEmpty)

        repository.shelves.emit(listOf(ShelfWithBooks(manga, emptyList())))
        runCurrent()
        assertTrue((viewModel.uiState.value as LibraryUiState.Loaded).isEmpty)
    }

    @Test
    fun `moving a book asks the repository and says where the book now is`() = runTest(dispatcher) {
        val (viewModel, messages) = loaded(ShelfWithBooks(manga, listOf(book(1), book(2), book(3))))

        viewModel.moveBook(bookId = 1, delta = 1)
        runCurrent()

        assertEquals(listOf("move 1 by 1"), repository.calls)
        assertEquals(listOf<LibraryMessage>(LibraryMessage.BookMoved("Book 1", position = 2, count = 3)), messages)
    }

    @Test
    fun `a book at the end of its shelf does not move further`() = runTest(dispatcher) {
        val (viewModel, messages) = loaded(ShelfWithBooks(manga, listOf(book(1), book(2))))

        viewModel.moveBook(bookId = 1, delta = -1)
        viewModel.moveBook(bookId = 2, delta = 1)
        viewModel.moveBook(bookId = 77, delta = 1)
        runCurrent()

        assertTrue(repository.calls.isEmpty())
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `moving to the next shelf reports the shelf the book landed on`() = runTest(dispatcher) {
        val (viewModel, messages) = loaded(ShelfWithBooks(manga, listOf(book(1))), ShelfWithBooks(novels, emptyList()))
        repository.nextShelf = novels

        viewModel.moveBookToNextShelf(1)
        runCurrent()

        assertEquals(listOf("next shelf 1"), repository.calls)
        assertEquals(listOf<LibraryMessage>(LibraryMessage.BookMovedToShelf("Book 1", "Novels")), messages)
    }

    @Test
    fun `with nowhere to move to nothing is announced`() = runTest(dispatcher) {
        val (viewModel, messages) = loaded(ShelfWithBooks(manga, listOf(book(1))))
        repository.nextShelf = null

        viewModel.moveBookToNextShelf(1)
        runCurrent()

        assertTrue(messages.isEmpty())
    }

    @Test
    fun `removing a book tells the user which one went`() = runTest(dispatcher) {
        val (viewModel, messages) = loaded(ShelfWithBooks(manga, listOf(book(1))))

        viewModel.removeBook(book(1))
        runCurrent()

        assertEquals(listOf("remove 1"), repository.calls)
        assertEquals(listOf<LibraryMessage>(LibraryMessage.BookRemoved("Book 1")), messages)
    }

    @Test
    fun `a new shelf gets a name no other shelf has`() = runTest(dispatcher) {
        val (viewModel, _) = loaded(ShelfWithBooks(manga, emptyList()))

        viewModel.addShelf("New shelf")
        runCurrent()
        repository.shelves.emit(
            listOf(ShelfWithBooks(manga, emptyList()), ShelfWithBooks(Shelf(3, "New shelf", 1), emptyList())),
        )
        runCurrent()
        viewModel.addShelf("New shelf")
        runCurrent()

        assertEquals(listOf("create New shelf", "create New shelf 2"), repository.calls)
    }

    @Test
    fun `deleting a shelf says how many books moved and where`() = runTest(dispatcher) {
        val (viewModel, messages) = loaded(
            ShelfWithBooks(manga, listOf(book(1))),
            ShelfWithBooks(novels, listOf(book(2), book(3))),
        )
        repository.shelfBooksMoveTo = manga

        viewModel.deleteShelf(novels)
        runCurrent()

        assertEquals(listOf("delete 2"), repository.calls)
        assertEquals(
            listOf<LibraryMessage>(LibraryMessage.ShelfDeleted("Novels", bookCount = 2, movedTo = "Manga")),
            messages,
        )
    }

    @Test
    fun `a shelf the repository refuses to delete is not reported as deleted`() = runTest(dispatcher) {
        val (viewModel, messages) = loaded(ShelfWithBooks(manga, listOf(book(1))))
        repository.shelfBooksMoveTo = null

        viewModel.deleteShelf(manga)
        runCurrent()

        assertTrue(messages.isEmpty())
    }

    @Test
    fun `renaming passes the new name on`() = runTest(dispatcher) {
        val (viewModel, _) = loaded(ShelfWithBooks(manga, emptyList()))

        viewModel.renameShelf(1, "Shonen")
        runCurrent()

        assertEquals(listOf("rename 1 to Shonen"), repository.calls)
    }
}

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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private class FakeLibraryRepository : LibraryRepository {
        val shelves = MutableSharedFlow<List<ShelfWithBooks>>(replay = 1)
        override fun observeShelves(): Flow<List<ShelfWithBooks>> = shelves
    }

    private val repository = FakeLibraryRepository()

    // One scheduler for the test body and for the view model's scope, advanced by hand, so
    // each assertion runs at a known point.
    private val dispatcher = StandardTestDispatcher()

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

    @Test
    fun `starts as loading until the database answers`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository)
        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `shows the shelves the repository emits and follows later changes`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository)
        val collector = backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()

        val manga = Shelf(id = 1, name = "Manga", position = 0)
        repository.shelves.emit(listOf(ShelfWithBooks(manga, listOf(book(1)))))
        runCurrent()
        val first = viewModel.uiState.value as LibraryUiState.Loaded
        assertEquals(listOf("Manga"), first.shelves.map { it.shelf.name })
        assertEquals(false, first.isEmpty)

        repository.shelves.emit(listOf(ShelfWithBooks(manga, listOf(book(1), book(2)))))
        runCurrent()
        val second = viewModel.uiState.value as LibraryUiState.Loaded
        assertEquals(2, second.shelves.single().books.size)

        collector.cancel()
    }

    @Test
    fun `a library whose shelves hold no books counts as empty`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository)
        val collector = backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()

        repository.shelves.emit(emptyList())
        runCurrent()
        assertTrue((viewModel.uiState.value as LibraryUiState.Loaded).isEmpty)

        repository.shelves.emit(listOf(ShelfWithBooks(Shelf(1, "Manga", 0), emptyList())))
        runCurrent()
        assertTrue((viewModel.uiState.value as LibraryUiState.Loaded).isEmpty)

        collector.cancel()
    }
}

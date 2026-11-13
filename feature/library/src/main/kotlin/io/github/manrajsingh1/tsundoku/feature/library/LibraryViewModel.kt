package io.github.manrajsingh1.tsundoku.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.manrajsingh1.tsundoku.core.data.LibraryRepository
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.Shelf
import io.github.manrajsingh1.tsundoku.core.model.ShelfWithBooks
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the library screen draws, as one immutable value. */
sealed interface LibraryUiState {

    /** The first read of the database has not come back yet. */
    data object Loading : LibraryUiState

    data class Loaded(val shelves: List<ShelfWithBooks>) : LibraryUiState {
        val isEmpty: Boolean get() = shelves.all { it.books.isEmpty() }
    }
}

/** Something that just happened to the library, for the screen to tell the user about. */
sealed interface LibraryMessage {

    /** A book changed place on its shelf. [position] counts from one. */
    data class BookMoved(val title: String, val position: Int, val count: Int) : LibraryMessage

    data class BookMovedToShelf(val title: String, val shelfName: String) : LibraryMessage

    data class BookRemoved(val title: String) : LibraryMessage

    data class ShelfDeleted(val name: String, val bookCount: Int, val movedTo: String) : LibraryMessage
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = libraryRepository.observeShelves()
        .map<List<ShelfWithBooks>, LibraryUiState> { LibraryUiState.Loaded(it) }
        .stateIn(
            scope = viewModelScope,
            // Keeps the database query alive across a rotation, but not while the app is away.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState.Loading,
        )

    private val messageChannel = Channel<LibraryMessage>(Channel.BUFFERED)

    /** Each message is delivered once, to whichever screen is showing. */
    val messages: Flow<LibraryMessage> = messageChannel.receiveAsFlow()

    private val shelves: List<ShelfWithBooks>
        get() = (uiState.value as? LibraryUiState.Loaded)?.shelves.orEmpty()

    /** Moves a book along its shelf: -1 is one place towards the start. */
    fun moveBook(bookId: Long, delta: Int) {
        val place = locateBook(shelves, bookId) ?: return
        val books = place.shelf.books
        val target = (place.index + delta).coerceIn(0, books.lastIndex)
        if (target == place.index) return
        viewModelScope.launch {
            libraryRepository.moveBook(bookId, target - place.index)
            messageChannel.send(LibraryMessage.BookMoved(place.book.title, target + 1, books.size))
        }
    }

    fun moveBookToNextShelf(bookId: Long) {
        val place = locateBook(shelves, bookId) ?: return
        viewModelScope.launch {
            val shelf = libraryRepository.moveBookToNextShelf(bookId) ?: return@launch
            messageChannel.send(LibraryMessage.BookMovedToShelf(place.book.title, shelf.name))
        }
    }

    /** Takes a book out of the library. The user's file stays where it is. */
    fun removeBook(book: Book) {
        viewModelScope.launch {
            libraryRepository.removeBook(book.id)
            messageChannel.send(LibraryMessage.BookRemoved(book.title))
        }
    }

    /** Adds an empty shelf at the bottom, named [baseName] or the next free "[baseName] 2". */
    fun addShelf(baseName: String) {
        val name = uniqueShelfName(baseName, shelves.map { it.shelf.name })
        viewModelScope.launch { libraryRepository.createShelf(name) }
    }

    fun renameShelf(shelfId: Long, name: String) {
        viewModelScope.launch { libraryRepository.renameShelf(shelfId, name) }
    }

    /** Deletes a shelf. Its books move to the first shelf left; no file is touched. */
    fun deleteShelf(shelf: Shelf) {
        val bookCount = shelves.firstOrNull { it.shelf.id == shelf.id }?.books?.size ?: 0
        viewModelScope.launch {
            val movedTo = libraryRepository.deleteShelf(shelf.id) ?: return@launch
            messageChannel.send(LibraryMessage.ShelfDeleted(shelf.name, bookCount, movedTo.name))
        }
    }
}

/** Where a book is: its shelf and its place on it. */
internal class BookPlace(val shelf: ShelfWithBooks, val index: Int) {
    val book: Book get() = shelf.books[index]
}

internal fun locateBook(shelves: List<ShelfWithBooks>, bookId: Long?): BookPlace? {
    if (bookId == null) return null
    for (shelf in shelves) {
        val index = shelf.books.indexOfFirst { it.id == bookId }
        if (index >= 0) return BookPlace(shelf, index)
    }
    return null
}

/** [base] if no shelf has that name yet, otherwise "[base] 2", "[base] 3" and so on. */
internal fun uniqueShelfName(base: String, existing: List<String>): String {
    if (base !in existing) return base
    var number = 2
    while ("$base $number" in existing) number++
    return "$base $number"
}

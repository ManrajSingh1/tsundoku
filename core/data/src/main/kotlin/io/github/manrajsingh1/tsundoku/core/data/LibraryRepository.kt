package io.github.manrajsingh1.tsundoku.core.data

import androidx.core.net.toUri
import io.github.manrajsingh1.tsundoku.core.data.importing.CoverStore
import io.github.manrajsingh1.tsundoku.core.data.importing.DocumentSource
import io.github.manrajsingh1.tsundoku.core.database.BookDao
import io.github.manrajsingh1.tsundoku.core.database.BookEntity
import io.github.manrajsingh1.tsundoku.core.database.ShelfDao
import io.github.manrajsingh1.tsundoku.core.database.ShelfEntity
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.Shelf
import io.github.manrajsingh1.tsundoku.core.model.ShelfWithBooks
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** The library as the screens see it. Only repositories talk to the database. */
interface LibraryRepository {

    /** Every shelf in order, each with its books in order. Emits again whenever either changes. */
    fun observeShelves(): Flow<List<ShelfWithBooks>>

    /** Moves a book [delta] places along its shelf: -1 is one to the left. Stops at the ends. */
    suspend fun moveBook(bookId: Long, delta: Int)

    /**
     * Moves a book to the end of the next shelf, going round from the last to the first.
     * Returns the shelf it landed on, or null if there is only one shelf.
     */
    suspend fun moveBookToNextShelf(bookId: Long): Shelf?

    /** Puts a shelf's books in the order given; books not named keep their order after them. */
    suspend fun setShelfOrder(shelfId: Long, bookIds: List<Long>)

    /** Takes a book out of the library. The user's file is left exactly where it is. */
    suspend fun removeBook(bookId: Long)

    suspend fun createShelf(name: String): Long

    /** Blank names are ignored: a shelf always has a name to be announced by. */
    suspend fun renameShelf(shelfId: Long, name: String)

    /**
     * Deletes a shelf, moving its books to the first shelf that is left. Returns that shelf,
     * or null if nothing was deleted: the last shelf cannot be.
     */
    suspend fun deleteShelf(shelfId: Long): Shelf?
}

class OfflineLibraryRepository @Inject constructor(
    private val bookDao: BookDao,
    private val shelfDao: ShelfDao,
    private val covers: CoverStore,
    private val documents: DocumentSource,
) : LibraryRepository {

    override fun observeShelves(): Flow<List<ShelfWithBooks>> =
        combine(shelfDao.observeAll(), bookDao.observeShelved()) { shelves, shelved ->
            val byShelf = shelved.groupBy { it.shelfId }
            shelves.map { shelf ->
                ShelfWithBooks(
                    shelf = shelf.toModel(),
                    books = byShelf[shelf.id].orEmpty().sortedBy { it.position }.map { it.book.toModel() },
                )
            }
        }

    override suspend fun moveBook(bookId: Long, delta: Int) {
        shelfDao.moveBook(bookId, delta)
    }

    override suspend fun moveBookToNextShelf(bookId: Long): Shelf? =
        shelfDao.moveBookToNextShelf(bookId)?.toModel()

    override suspend fun setShelfOrder(shelfId: Long, bookIds: List<Long>) {
        shelfDao.setShelfOrder(shelfId, bookIds)
    }

    override suspend fun removeBook(bookId: Long) {
        val book = bookDao.findById(bookId) ?: return
        bookDao.delete(bookId)
        withContext(Dispatchers.IO) {
            book.coverPath?.let(covers::delete)
            documents.releaseReadPermission(book.documentUri.toUri())
        }
    }

    override suspend fun createShelf(name: String): Long = shelfDao.createShelf(name.trim())

    override suspend fun renameShelf(shelfId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) shelfDao.renameShelf(shelfId, trimmed)
    }

    override suspend fun deleteShelf(shelfId: Long): Shelf? =
        shelfDao.deleteShelfKeepingBooks(shelfId)?.toModel()
}

internal fun ShelfEntity.toModel() = Shelf(id = id, name = name, position = position)

internal fun BookEntity.toModel() = Book(
    id = id,
    documentUri = documentUri,
    contentHash = contentHash,
    format = format,
    title = title,
    author = author,
    series = series,
    volume = volume,
    pageCount = pageCount,
    fileSize = fileSize,
    coverPath = coverPath,
    spineColor = spineColor,
    readingDirection = readingDirection,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    isMissing = isMissing,
)

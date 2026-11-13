package io.github.manrajsingh1.tsundoku.core.data

import io.github.manrajsingh1.tsundoku.core.database.BookDao
import io.github.manrajsingh1.tsundoku.core.database.BookEntity
import io.github.manrajsingh1.tsundoku.core.database.ShelfDao
import io.github.manrajsingh1.tsundoku.core.database.ShelfEntity
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.Shelf
import io.github.manrajsingh1.tsundoku.core.model.ShelfWithBooks
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The library as the screens see it. Only repositories talk to the database. */
interface LibraryRepository {

    /** Every shelf in order, each with its books in order. Emits again whenever either changes. */
    fun observeShelves(): Flow<List<ShelfWithBooks>>
}

class OfflineLibraryRepository @Inject constructor(
    private val bookDao: BookDao,
    private val shelfDao: ShelfDao,
) : LibraryRepository {

    override fun observeShelves(): Flow<List<ShelfWithBooks>> =
        combine(shelfDao.observeAll(), bookDao.observeShelved()) { shelves, shelved ->
            val byShelf = shelved.groupBy { it.shelfId }
            shelves.map { shelf ->
                ShelfWithBooks(
                    shelf = shelf.toModel(),
                    books = byShelf[shelf.id].orEmpty().map { it.book.toModel() },
                )
            }
        }
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

package io.github.manrajsingh1.tsundoku.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import io.github.manrajsingh1.tsundoku.core.model.ImportState
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BookDao {

    @Query("SELECT * FROM books WHERE id = :id")
    abstract suspend fun findById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE contentHash = :hash LIMIT 1")
    abstract suspend fun findByHash(hash: String): BookEntity?

    @Query("SELECT COUNT(*) FROM books")
    abstract suspend fun count(): Int

    /** Every shelved book, in shelf order and then in its order on the shelf. */
    @Query(
        "SELECT books.*, shelf_books.shelfId AS shelfId, shelf_books.position AS position " +
            "FROM books INNER JOIN shelf_books ON shelf_books.bookId = books.id " +
            "ORDER BY shelf_books.shelfId, shelf_books.position",
    )
    abstract fun observeShelved(): Flow<List<ShelvedBook>>

    /** Points an existing book at a new location, for a file that was moved and picked again. */
    @Query("UPDATE books SET documentUri = :uri, isMissing = 0 WHERE id = :id")
    abstract suspend fun relink(id: Long, uri: String)

    @Query("UPDATE books SET isMissing = :missing WHERE id = :id")
    abstract suspend fun setMissing(id: Long, missing: Boolean)

    @Query("DELETE FROM books WHERE id = :id")
    abstract suspend fun delete(id: Long)
}

@Dao
abstract class ShelfDao {

    @Query("SELECT * FROM shelves ORDER BY position, id")
    abstract fun observeAll(): Flow<List<ShelfEntity>>

    @Query("SELECT * FROM shelves WHERE name = :name LIMIT 1")
    abstract suspend fun findByName(name: String): ShelfEntity?

    /** After every other shelf, and never among the default shelves' places below zero. */
    @Query("SELECT MAX(COALESCE(MAX(position), -1) + 1, 0) FROM shelves")
    abstract suspend fun nextShelfPosition(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM shelf_books WHERE shelfId = :shelfId")
    abstract suspend fun nextBookPosition(shelfId: Long): Int

    @Insert
    abstract suspend fun insertShelf(shelf: ShelfEntity): Long

    @Insert
    abstract suspend fun insertBook(book: BookEntity): Long

    @Insert
    abstract suspend fun insertLink(link: ShelfBookEntity)

    @Query("DELETE FROM shelves WHERE id = :id")
    abstract suspend fun deleteShelf(id: Long)

    @Query("SELECT * FROM shelves ORDER BY position, id")
    abstract suspend fun allShelves(): List<ShelfEntity>

    @Query("SELECT * FROM shelves WHERE id = :id")
    abstract suspend fun findShelf(id: Long): ShelfEntity?

    @Query("UPDATE shelves SET name = :name WHERE id = :id")
    abstract suspend fun renameShelf(id: Long, name: String)

    @Query("SELECT * FROM shelf_books WHERE shelfId = :shelfId ORDER BY position")
    abstract suspend fun links(shelfId: Long): List<ShelfBookEntity>

    @Query("SELECT * FROM shelf_books WHERE bookId = :bookId LIMIT 1")
    abstract suspend fun linkFor(bookId: Long): ShelfBookEntity?

    @Query("UPDATE shelf_books SET position = :position WHERE bookId = :bookId")
    abstract suspend fun setBookPosition(bookId: Long, position: Int)

    @Query("UPDATE shelf_books SET shelfId = :shelfId, position = :position WHERE bookId = :bookId")
    abstract suspend fun setBookShelf(bookId: Long, shelfId: Long, position: Int)

    /**
     * Adds a book at the end of the shelf with [preferredShelfId] if there still is one, and
     * otherwise of the shelf named [fallbackName], created at [fallbackPosition] if need be.
     */
    @Transaction
    open suspend fun addBookToShelfOrDefault(
        book: BookEntity,
        preferredShelfId: Long?,
        fallbackName: String,
        fallbackPosition: Int,
    ): Long {
        val preferred = preferredShelfId?.let { findShelf(it) }
        val shelfId = preferred?.id
            ?: findByName(fallbackName)?.id
            ?: insertShelf(ShelfEntity(name = fallbackName, position = fallbackPosition))
        val bookId = insertBook(book)
        insertLink(ShelfBookEntity(shelfId = shelfId, bookId = bookId, position = nextBookPosition(shelfId)))
        return bookId
    }

    /**
     * Moves a book [delta] places along its shelf, stopping at either end. Returns false if
     * it did not move. The whole shelf is renumbered from zero, so gaps left by removed
     * books never build up.
     */
    @Transaction
    open suspend fun moveBook(bookId: Long, delta: Int): Boolean {
        val link = linkFor(bookId) ?: return false
        val order = links(link.shelfId)
        val from = order.indexOfFirst { it.bookId == bookId }
        if (from < 0) return false
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (to == from) return false
        val reordered = order.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        reordered.forEachIndexed { index, item ->
            if (item.position != index) setBookPosition(item.bookId, index)
        }
        return true
    }

    /**
     * Moves a book to the end of the shelf after its own, going round from the last shelf to
     * the first. Returns the shelf it landed on, or null if there is no other shelf.
     */
    @Transaction
    open suspend fun moveBookToNextShelf(bookId: Long): ShelfEntity? {
        val link = linkFor(bookId) ?: return null
        val shelves = allShelves()
        if (shelves.size < 2) return null
        val index = shelves.indexOfFirst { it.id == link.shelfId }
        val target = shelves[(index + 1) % shelves.size]
        setBookShelf(bookId, target.id, nextBookPosition(target.id))
        return target
    }

    @Transaction
    open suspend fun createShelf(name: String): Long =
        insertShelf(ShelfEntity(name = name, position = nextShelfPosition()))

    /**
     * Deletes a shelf and moves its books, in order, to the end of the first shelf that is
     * left. The last remaining shelf cannot be deleted: its books would have nowhere to go.
     * Returns the shelf the books moved to, or null if nothing was deleted.
     */
    @Transaction
    open suspend fun deleteShelfKeepingBooks(shelfId: Long): ShelfEntity? {
        val shelves = allShelves()
        if (shelves.none { it.id == shelfId }) return null
        val target = shelves.firstOrNull { it.id != shelfId } ?: return null
        var position = nextBookPosition(target.id)
        for (link in links(shelfId)) {
            setBookShelf(link.bookId, target.id, position)
            position++
        }
        deleteShelf(shelfId)
        return target
    }

    /**
     * Adds a book at the end of the named shelf, creating the shelf if this is its first
     * book. One transaction: a book is never left in the library without a shelf.
     */
    @Transaction
    open suspend fun addBookToShelf(book: BookEntity, shelfName: String): Long {
        val shelfId = findByName(shelfName)?.id
            ?: insertShelf(ShelfEntity(name = shelfName, position = nextShelfPosition()))
        val bookId = insertBook(book)
        insertLink(ShelfBookEntity(shelfId = shelfId, bookId = bookId, position = nextBookPosition(shelfId)))
        return bookId
    }
}

@Dao
abstract class ReadingProgressDao {

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    abstract suspend fun find(bookId: Long): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun save(progress: ReadingProgressEntity)
}

@Dao
abstract class ImportQueueDao {

    @Insert
    abstract suspend fun insertAll(items: List<ImportItemEntity>)

    @Query("SELECT * FROM import_queue WHERE state = 'PENDING' ORDER BY id LIMIT 1")
    abstract suspend fun nextPending(): ImportItemEntity?

    @Query("UPDATE import_queue SET state = :state, displayName = :displayName, message = :message WHERE id = :id")
    abstract suspend fun finish(id: Long, state: ImportState, displayName: String?, message: String?)

    @Query("DELETE FROM import_queue WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT state, COUNT(*) AS count FROM import_queue GROUP BY state")
    abstract fun observeCounts(): Flow<List<ImportStateCount>>

    @Query("SELECT * FROM import_queue WHERE state != 'PENDING' ORDER BY id")
    abstract suspend fun finished(): List<ImportItemEntity>

    /** Forgets the outcome of earlier imports, so the next one starts its tally from zero. */
    @Query("DELETE FROM import_queue WHERE state != 'PENDING'")
    abstract suspend fun clearFinished()
}

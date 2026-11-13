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

    @Query("SELECT * FROM shelves ORDER BY position")
    abstract fun observeAll(): Flow<List<ShelfEntity>>

    @Query("SELECT * FROM shelves WHERE name = :name LIMIT 1")
    abstract suspend fun findByName(name: String): ShelfEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM shelves")
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

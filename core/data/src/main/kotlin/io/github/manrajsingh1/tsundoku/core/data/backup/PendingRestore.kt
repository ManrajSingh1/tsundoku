package io.github.manrajsingh1.tsundoku.core.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.manrajsingh1.tsundoku.core.database.BookDao
import io.github.manrajsingh1.tsundoku.core.database.ReadingProgressDao
import io.github.manrajsingh1.tsundoku.core.database.ReadingProgressEntity
import io.github.manrajsingh1.tsundoku.core.database.ShelfBookEntity
import io.github.manrajsingh1.tsundoku.core.database.ShelfDao
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The part of a restored backup that is still waiting for its books.
 *
 * A backup holds shelves and reading positions but not the books themselves, so on a new
 * phone a restore finds an empty library. What it could not place is kept here, and each
 * book goes back to its shelf, its place on the shelf and its page as its file is added.
 *
 * It is kept as two plain files beside the database: the backup itself, and the
 * fingerprints of the books already put back. When none are left waiting, both are deleted.
 */
@Singleton
class PendingRestore(
    private val directory: File,
    private val bookDao: BookDao,
    private val shelfDao: ShelfDao,
    private val progressDao: ReadingProgressDao,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        bookDao: BookDao,
        shelfDao: ShelfDao,
        progressDao: ReadingProgressDao,
    ) : this(context.filesDir, bookDao, shelfDao, progressDao)

    private val backupFile get() = File(directory, "restore-pending.json")
    private val placedFile get() = File(directory, "restore-placed.txt")

    private val mutex = Mutex()
    private var loaded = false
    private val waiting = LinkedHashMap<String, Pair<BackupShelf, BackupBook>>()

    /** Starts waiting for every book of [backup] whose fingerprint is not in [placed]. */
    suspend fun begin(backup: LibraryBackup, placed: Set<String>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            waiting.clear()
            for (shelf in backup.shelves) {
                for (book in shelf.books) if (book.hash !in placed) waiting[book.hash] = shelf to book
            }
            loaded = true
            if (waiting.isEmpty()) {
                forget()
            } else {
                backupFile.writeText(BackupCodec.encode(backup))
                placedFile.writeText(placed.joinToString("\n", postfix = "\n"))
            }
        }
    }

    /**
     * Called for each book as it is added. If a restore is waiting for it, the book goes to
     * its shelf, its place and its page, and true is returned.
     */
    suspend fun place(bookId: Long, hash: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            load()
            val (shelf, book) = waiting.remove(hash) ?: return@withContext false
            val shelfId = putInPlace(bookId, shelf, book)
            putInOrder(shelfId, shelf)
            if (waiting.isEmpty()) forget() else placedFile.appendText(hash + "\n")
            true
        }
    }

    /** The books still waiting, with the name of the shelf each belongs on. */
    suspend fun waiting(): List<Pair<String, BackupBook>> = mutex.withLock {
        withContext(Dispatchers.IO) {
            load()
            waiting.values.map { (shelf, book) -> shelf.name to book }
        }
    }

    /** Puts one book on the shelf the backup names, with its direction and its page. */
    internal suspend fun putInPlace(bookId: Long, shelf: BackupShelf, book: BackupBook): Long {
        val shelfId = shelfDao.findByName(shelf.name)?.id ?: shelfDao.createShelf(shelf.name)
        val link = shelfDao.linkFor(bookId)
        when {
            link == null -> shelfDao.insertLink(ShelfBookEntity(shelfId, bookId, shelfDao.nextBookPosition(shelfId)))
            link.shelfId != shelfId -> shelfDao.setBookShelf(bookId, shelfId, shelfDao.nextBookPosition(shelfId))
        }
        bookDao.setReadingDirection(bookId, book.direction)
        if (book.position != null && book.fraction != null) {
            progressDao.save(
                ReadingProgressEntity(bookId, book.position, book.fraction, book.progressAt ?: System.currentTimeMillis()),
            )
        }
        return shelfId
    }

    /** Orders a shelf as the backup had it. Books the backup does not know stay after the rest. */
    internal suspend fun putInOrder(shelfId: Long, shelf: BackupShelf) {
        shelfDao.setShelfOrder(shelfId, shelf.books.mapNotNull { bookDao.findByHash(it.hash)?.id })
    }

    private fun load() {
        if (loaded) return
        loaded = true
        waiting.clear()
        val backup = runCatching { backupFile.takeIf { it.exists() }?.readText()?.let(BackupCodec::decode) }.getOrNull()
            ?: return
        val placed = runCatching { placedFile.readLines().toHashSet() }.getOrDefault(HashSet())
        for (shelf in backup.shelves) {
            for (book in shelf.books) if (book.hash !in placed) waiting[book.hash] = shelf to book
        }
    }

    private fun forget() {
        backupFile.delete()
        placedFile.delete()
    }
}

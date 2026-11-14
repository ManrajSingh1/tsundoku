package io.github.manrajsingh1.tsundoku.core.data

import io.github.manrajsingh1.tsundoku.core.database.BookDao
import io.github.manrajsingh1.tsundoku.core.database.ReadingProgressDao
import io.github.manrajsingh1.tsundoku.core.database.ReadingProgressEntity
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Where the reader is in each book, and how each book is read. */
interface ReadingRepository {

    /** How far through each started book the reader is: book id to a fraction from 0 to 1. */
    fun observeProgress(): Flow<Map<Long, Double>>

    suspend fun findBook(bookId: Long): Book?

    /**
     * Where to open a book: a page index for comics, a serialised Readium locator for EPUBs
     * and PDFs. Null if it has never been opened.
     */
    suspend fun position(bookId: Long): String?

    /**
     * Remembers where the reader is. Returns at once and never fails the caller: it is
     * called on every page turn, and the write outlives the screen that asked for it.
     */
    fun recordProgress(bookId: Long, position: String, fraction: Double)

    suspend fun markOpened(bookId: Long)

    /** The direction a book reads in, with "follow the default" already resolved. */
    suspend fun readingDirection(book: Book): ReadingDirection

    /** Sets the direction for a book and, if it belongs to a series, for the whole series. */
    suspend fun setReadingDirection(book: Book, direction: ReadingDirection)
}

@Singleton
class OfflineReadingRepository @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ReadingProgressDao,
    private val settings: SettingsRepository,
) : ReadingRepository {

    // Progress is written from here, not from a screen's scope, so the last page turn before
    // the reader closes is still saved.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = Channel<ReadingProgressEntity>(Channel.CONFLATED)

    init {
        scope.launch {
            for (progress in pending) {
                // A book removed while it was open has nothing to attach progress to.
                runCatching { progressDao.save(progress) }
            }
        }
    }

    override fun observeProgress(): Flow<Map<Long, Double>> =
        progressDao.observeAll().map { all -> all.associate { it.bookId to it.percent } }

    override suspend fun findBook(bookId: Long): Book? = bookDao.findById(bookId)?.toModel()

    override suspend fun position(bookId: Long): String? = progressDao.find(bookId)?.position

    override fun recordProgress(bookId: Long, position: String, fraction: Double) {
        pending.trySend(
            ReadingProgressEntity(
                bookId = bookId,
                position = position,
                percent = fraction.coerceIn(0.0, 1.0),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun markOpened(bookId: Long) {
        bookDao.setLastOpened(bookId, System.currentTimeMillis())
    }

    override suspend fun readingDirection(book: Book): ReadingDirection = when (book.readingDirection) {
        ReadingDirection.AUTO -> settings.comicReadingDirection.first()
        else -> book.readingDirection
    }

    override suspend fun setReadingDirection(book: Book, direction: ReadingDirection) {
        val series = book.series
        if (series.isNullOrBlank()) {
            bookDao.setReadingDirection(book.id, direction)
        } else {
            bookDao.setSeriesReadingDirection(series, book.format, direction)
        }
    }
}

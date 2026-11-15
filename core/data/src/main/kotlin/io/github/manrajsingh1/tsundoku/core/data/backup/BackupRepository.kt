package io.github.manrajsingh1.tsundoku.core.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.manrajsingh1.tsundoku.core.data.SettingsRepository
import io.github.manrajsingh1.tsundoku.core.database.BookDao
import io.github.manrajsingh1.tsundoku.core.database.ReadingProgressDao
import io.github.manrajsingh1.tsundoku.core.database.ShelfDao
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** What a saved backup holds. */
data class BackupSummary(val shelves: Int, val books: Int)

sealed interface RestoreResult {
    /**
     * @param placed books that were in the library and are back where the backup had them.
     * @param waiting books not in the library yet; they return to their places when added.
     */
    data class Restored(val shelves: Int, val placed: Int, val waiting: Int) : RestoreResult

    /** The file could be read but is not a backup this version of the app understands. */
    data object NotABackup : RestoreResult

    data object Unreadable : RestoreResult
}

/**
 * Saves the library's arrangement to a file the user keeps, and restores it. Books are
 * never part of a backup and a restore never removes one: it only moves books between
 * shelves, reorders them, and sets reading positions and settings.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val shelfDao: ShelfDao,
    private val progressDao: ReadingProgressDao,
    private val settings: SettingsRepository,
    private val pending: PendingRestore,
) {

    /** The library as it stands, including books an earlier restore is still waiting for. */
    suspend fun snapshot(): LibraryBackup = withContext(Dispatchers.IO) {
        val stillWaiting = pending.waiting().groupBy({ it.first }, { it.second })
        val shelves = shelfDao.allShelves().map { shelf ->
            val books = shelfDao.links(shelf.id).mapNotNull { link ->
                bookDao.findById(link.bookId)?.let { book ->
                    val progress = progressDao.find(book.id)
                    BackupBook(
                        hash = book.contentHash,
                        title = book.title,
                        direction = book.readingDirection,
                        position = progress?.position,
                        fraction = progress?.percent,
                        progressAt = progress?.updatedAt,
                    )
                }
            }
            BackupShelf(shelf.name, books + stillWaiting[shelf.name].orEmpty())
        }
        val names = shelves.mapTo(HashSet()) { it.name }
        val waitingShelves = stillWaiting.filterKeys { it !in names }.map { (name, books) -> BackupShelf(name, books) }
        LibraryBackup(
            exportedAt = System.currentTimeMillis(),
            preferences = settings.preferences.first(),
            epubFontSize = settings.epubFontSize.first(),
            shelves = shelves + waitingShelves,
        )
    }

    /** Writes a backup to [uri]. Null if the file could not be written. */
    suspend fun save(uri: Uri): BackupSummary? = withContext(Dispatchers.IO) {
        try {
            val backup = snapshot()
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext null
            stream.use { it.write(BackupCodec.encode(backup).toByteArray(Charsets.UTF_8)) }
            BackupSummary(backup.shelves.size, backup.shelves.sumOf { it.books.size })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun restore(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val text = readText(uri) ?: return@withContext RestoreResult.Unreadable
        val backup = BackupCodec.decode(text) ?: return@withContext RestoreResult.NotABackup
        restoreFrom(backup)
    }

    private fun readText(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use(::readLimited)
    } catch (e: Exception) {
        null
    }

    /**
     * Makes the library match [backup] as far as the books present allow. Shelves named in
     * the backup come first, in its order; shelves it does not name are kept, after them.
     */
    suspend fun restoreFrom(backup: LibraryBackup): RestoreResult.Restored = withContext(Dispatchers.IO) {
        val restoredIds = backup.shelves.map { shelf ->
            shelfDao.findByName(shelf.name)?.id ?: shelfDao.createShelf(shelf.name)
        }
        val others = shelfDao.allShelves().map { it.id }.filterNot { it in restoredIds }
        (restoredIds + others).forEachIndexed { position, id -> shelfDao.setShelfPosition(id, position) }

        val placed = HashSet<String>()
        var waiting = 0
        backup.shelves.forEachIndexed { index, shelf ->
            for (book in shelf.books) {
                val present = bookDao.findByHash(book.hash)
                if (present == null) {
                    waiting++
                } else {
                    pending.putInPlace(present.id, shelf, book)
                    placed += book.hash
                }
            }
            pending.putInOrder(restoredIds[index], shelf)
        }
        pending.begin(backup, placed)

        settings.setColourTheme(backup.preferences.colourTheme)
        settings.setFlatCarousel(backup.preferences.flatCarousel)
        settings.setComicReadingDirection(backup.preferences.comicReadingDirection)
        settings.setEpubPageTheme(backup.preferences.epubPageTheme)
        settings.setEpubFontSize(backup.epubFontSize)
        // Last: changing light or dark redraws every screen.
        settings.setThemeMode(backup.preferences.themeMode)

        RestoreResult.Restored(backup.shelves.size, placed.size, waiting)
    }

    /** Null if the file is larger than any backup could be. */
    private fun readLimited(stream: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_BYTES) return null
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val MAX_BYTES = 32 * 1024 * 1024
    }
}

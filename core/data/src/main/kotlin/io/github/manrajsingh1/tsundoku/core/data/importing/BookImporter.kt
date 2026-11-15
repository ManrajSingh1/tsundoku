package io.github.manrajsingh1.tsundoku.core.data.importing

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.manrajsingh1.tsundoku.core.data.R
import io.github.manrajsingh1.tsundoku.core.data.backup.PendingRestore
import io.github.manrajsingh1.tsundoku.core.database.BookDao
import io.github.manrajsingh1.tsundoku.core.database.BookEntity
import io.github.manrajsingh1.tsundoku.core.database.ShelfDao
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.model.DefaultShelf
import io.github.manrajsingh1.tsundoku.core.model.ImportState
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.importing.ContentHash
import io.github.manrajsingh1.tsundoku.core.model.importing.FileNameParser
import io.github.manrajsingh1.tsundoku.core.model.importing.FormatSniffer
import io.github.manrajsingh1.tsundoku.core.model.importing.SpineColour
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What became of one file. */
class ImportOutcome(
    val state: ImportState,
    val displayName: String?,
    val message: String? = null,
    val bookId: Long? = null,
)

/**
 * Turns one picked file into a book in the library: works out what it is from its bytes,
 * recognises it if it is already there, reads its metadata and cover, and shelves it.
 *
 * The file is treated as untrusted throughout. Nothing in it is executed, its claims about
 * its own type are checked against its bytes, and images are size-bounded before decoding.
 */
class BookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documents: DocumentSource,
    private val bookDao: BookDao,
    private val shelfDao: ShelfDao,
    private val inspector: PublicationInspector,
    private val covers: CoverStore,
    private val pendingRestore: PendingRestore,
) {

    /**
     * @param targetShelfId the shelf the user was adding to, or null to shelve by format.
     */
    suspend fun import(uri: Uri, targetShelfId: Long? = null): ImportOutcome = withContext(Dispatchers.IO) {
        val info = documents.describe(uri)
        val digest = documents.open(uri)?.use { ContentHash.digest(it) }
            ?: return@withContext failed(info, R.string.core_data_import_cannot_open)

        val format = FormatSniffer.sniff(digest.head, info.displayName, info.mimeType)
            ?: return@withContext ImportOutcome(ImportState.UNSUPPORTED, info.displayName)

        bookDao.findByHash(digest.sha256)?.let { existing ->
            // The same bytes again. If the book had gone missing, this is the user showing
            // where it lives now.
            if (existing.isMissing) bookDao.relink(existing.id, uri.toString())
            return@withContext ImportOutcome(ImportState.DUPLICATE, existing.title, bookId = existing.id)
        }

        val inspection = try {
            inspector.inspect(uri, format)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Inspection(readable = false)
        }
        if (!inspection.readable) return@withContext failed(info, R.string.core_data_import_cannot_read)
        if (format == BookFormat.CBZ && (inspection.pageCount ?: 0) == 0) {
            return@withContext failed(info, R.string.core_data_import_no_pages)
        }

        val fromName = FileNameParser.parse(info.displayName, inferSeries = format == BookFormat.CBZ)
        val series = inspection.series.clean() ?: fromName.series
        val volume = inspection.volume ?: fromName.volume
        val title = inspection.title.clean() ?: fromName.title

        val cover = inspection.cover
        val coverPath = cover?.let { covers.save(digest.sha256, it) }
        val spineColor = cover?.let { covers.dominantColour(it) }?.let { SpineColour.readableBehindWhite(it) }
            ?: SpineColour.fromTitle(title)
        cover?.recycle()

        val book = BookEntity(
            documentUri = uri.toString(),
            contentHash = digest.sha256,
            format = format,
            title = title,
            author = inspection.author.clean(),
            series = series,
            volume = volume,
            pageCount = inspection.pageCount,
            fileSize = digest.size,
            coverPath = coverPath,
            spineColor = spineColor,
            readingDirection = when (inspection.rightToLeft) {
                true -> ReadingDirection.RIGHT_TO_LEFT
                false -> ReadingDirection.LEFT_TO_RIGHT
                null -> ReadingDirection.AUTO
            },
            addedAt = System.currentTimeMillis(),
            lastOpenedAt = null,
        )
        val bookId = try {
            val fallback = DefaultShelf.forFormat(format)
            shelfDao.addBookToShelfOrDefault(book, targetShelfId, shelfName(fallback), fallback.position)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Two copies of one file in the same batch: the second insert meets the unique index.
            val winner = bookDao.findByHash(digest.sha256) ?: throw e
            return@withContext ImportOutcome(ImportState.DUPLICATE, winner.title, bookId = winner.id)
        }
        // If a restored backup has been waiting for this book, it goes back to its own
        // shelf, place and page. Failing to is not a reason to fail the import.
        try {
            pendingRestore.place(bookId, digest.sha256)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The book stays where the import put it.
        }
        ImportOutcome(ImportState.IMPORTED, title, bookId = bookId)
    }

    private fun failed(info: DocumentInfo, reason: Int) =
        ImportOutcome(ImportState.FAILED, info.displayName, message = context.getString(reason))

    private fun shelfName(shelf: DefaultShelf): String = context.getString(
        when (shelf) {
            DefaultShelf.MANGA -> R.string.core_data_shelf_manga
            DefaultShelf.NOVELS -> R.string.core_data_shelf_novels
            DefaultShelf.DOCUMENTS -> R.string.core_data_shelf_documents
        },
    )

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}

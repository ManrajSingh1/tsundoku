package io.github.manrajsingh1.tsundoku.core.data.reading

import android.content.Context
import android.graphics.Bitmap
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.manrajsingh1.tsundoku.core.data.importing.BoundedBitmapDecoder
import io.github.manrajsingh1.tsundoku.core.model.Book
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Opens a book's file for reading. The file stays where the user keeps it; this reads it
 * through the permission granted when it was imported.
 */
@OptIn(ExperimentalReadiumApi::class)
@Singleton
class PublicationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val opener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = PdfiumDocumentFactory(context),
        ),
    )

    /** Null if the file has gone, the permission has lapsed or the file cannot be read. */
    suspend fun open(book: Book): Publication? = withContext(Dispatchers.IO) {
        try {
            val url = book.documentUri.toUri().toAbsoluteUrl() ?: return@withContext null
            val asset = assetRetriever.retrieve(url).getOrElse { return@withContext null }
            opener.open(asset, allowUserInteraction = false).getOrElse { return@withContext null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * One page of a comic, decoded no larger than it will be shown. Null if the page cannot
     * be read or is not an image this device can decode.
     */
    suspend fun comicPage(publication: Publication, index: Int, maxWidth: Int, maxHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val link = publication.readingOrder.getOrNull(index) ?: return@withContext null
                val resource = publication.get(link) ?: return@withContext null
                val length = resource.length().getOrNull()
                if (length != null && length > MAX_PAGE_BYTES) return@withContext null
                val bytes = resource.read().getOrNull() ?: return@withContext null
                // A scan far larger than the screen is refused at this size; it is shown smaller
                // sooner than not at all.
                BoundedBitmapDecoder.decode(bytes, maxWidth, maxHeight)
                    ?: BoundedBitmapDecoder.decode(bytes, maxWidth / 2, maxHeight / 2)
                    ?: BoundedBitmapDecoder.decode(bytes, maxWidth / 4, maxHeight / 4)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
        }

    private companion object {
        /** No real comic page is this large; a file that claims to be is not decoded. */
        const val MAX_PAGE_BYTES = 48L * 1024 * 1024
    }
}

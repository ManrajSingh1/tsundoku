package io.github.manrajsingh1.tsundoku.core.data.importing

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.model.importing.ComicInfo
import io.github.manrajsingh1.tsundoku.core.model.importing.ComicInfoParser
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** What a book file says about itself. Every field is optional: files are often silent. */
class Inspection(
    /** False when the file could not be opened as the format its bytes claimed. */
    val readable: Boolean,
    val title: String? = null,
    val author: String? = null,
    val series: String? = null,
    val volume: Double? = null,
    val pageCount: Int? = null,
    val rightToLeft: Boolean? = null,
    /** Already downsampled to thumbnail size. The caller recycles it. */
    val cover: Bitmap? = null,
)

/** Opens a book file far enough to read its metadata and cover. The seam the engine sits behind. */
interface PublicationInspector {
    suspend fun inspect(uri: Uri, format: BookFormat): Inspection
}

/**
 * Reads all three formats with the Readium toolkit, so the app carries no parser of its own
 * for EPUB packages, PDF structure or zip archives.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReadiumPublicationInspector @Inject constructor(
    @ApplicationContext private val context: Context,
) : PublicationInspector {

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

    override suspend fun inspect(uri: Uri, format: BookFormat): Inspection {
        val unreadable = Inspection(readable = false)
        val url = uri.toAbsoluteUrl() ?: return unreadable
        val asset = assetRetriever.retrieve(url).getOrElse { return unreadable }
        val publication = opener.open(asset, allowUserInteraction = false).getOrElse { return unreadable }
        return try {
            when (format) {
                BookFormat.CBZ -> inspectComic(publication)
                BookFormat.EPUB -> inspectEpub(publication)
                BookFormat.PDF -> inspectPdf(publication)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unreadable
        } finally {
            publication.close()
        }
    }

    private suspend fun inspectComic(publication: Publication): Inspection {
        val pages = publication.readingOrder
        val info = readComicInfo(publication)
        return Inspection(
            readable = true,
            title = info?.title,
            author = info?.writer,
            series = info?.series,
            volume = info?.number,
            pageCount = pages.size,
            rightToLeft = info?.rightToLeft,
            cover = pages.firstOrNull()?.let { decodeImage(publication, it) },
        )
    }

    private suspend fun inspectEpub(publication: Publication): Inspection {
        val metadata = publication.metadata
        val series = metadata.belongsTo["series"]?.firstOrNull()
        val declaredCover = publication.linkWithRel("cover")?.let { decodeImage(publication, it) }
        return Inspection(
            readable = true,
            title = metadata.title,
            author = metadata.authors.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
            series = series?.name,
            volume = series?.position,
            pageCount = runCatching { publication.positions().size }.getOrNull()?.takeIf { it > 0 },
            cover = declaredCover ?: renderedCover(publication),
        )
    }

    private suspend fun inspectPdf(publication: Publication): Inspection {
        val metadata = publication.metadata
        return Inspection(
            readable = true,
            title = metadata.title,
            author = metadata.authors.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
            pageCount = metadata.numberOfPages
                ?: runCatching { publication.positions().size }.getOrNull()?.takeIf { it > 0 },
            // A PDF has no cover of its own: its first page, rendered small, stands in.
            cover = renderedCover(publication),
        )
    }

    private suspend fun readComicInfo(publication: Publication): ComicInfo? {
        // The file sits at the root of the archive under exactly this name.
        val url = Url("ComicInfo.xml") ?: return null
        val resource = publication.get(url) ?: return null
        val bytes = readCapped(resource, ComicInfoParser.MAX_BYTES.toLong()) ?: return null
        return ComicInfoParser.parse(bytes)
    }

    /** Decodes an image inside the publication with its size bounded before any pixels are allocated. */
    private suspend fun decodeImage(publication: Publication, link: Link): Bitmap? {
        val resource = publication.get(link) ?: return null
        val bytes = readCapped(resource, MAX_IMAGE_BYTES) ?: return null
        return BoundedBitmapDecoder.decode(bytes, CoverStore.MAX_WIDTH, CoverStore.MAX_HEIGHT)
    }

    private suspend fun renderedCover(publication: Publication): Bitmap? = try {
        publication.coverFitting(Size(CoverStore.MAX_WIDTH, CoverStore.MAX_HEIGHT))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Reads a resource whole, unless it is larger than [limit]: then it is not read at all. */
    private suspend fun readCapped(resource: Resource, limit: Long): ByteArray? {
        val length = resource.length().getOrNull() ?: return null
        if (length <= 0 || length > limit) return null
        return try {
            resource.read().getOrNull()
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private companion object {
        /** No real cover or comic page is larger than this; anything that is, is not decoded. */
        const val MAX_IMAGE_BYTES = 48L * 1024 * 1024
    }
}

package io.github.manrajsingh1.tsundoku.core.data.importing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes an image from an untrusted file without ever holding it at full size. An image a
 * few kilobytes long can declare itself tens of thousands of pixels square; decoding that
 * naively would exhaust the app's memory. The declared size is read first and the image is
 * refused or downsampled before any pixels are allocated.
 */
object BoundedBitmapDecoder {

    /** Declared sizes beyond this are refused outright. */
    const val MAX_DECLARED_PIXELS = 200_000_000L

    /** After downsampling, the decode may not allocate more than this many pixels. */
    const val MAX_DECODED_PIXELS = 16_000_000L

    fun decode(bytes: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        if (width.toLong() * height > MAX_DECLARED_PIXELS) return null

        var sample = 1
        while (width / (sample * 2) >= maxWidth && height / (sample * 2) >= maxHeight) sample *= 2
        if ((width / sample).toLong() * (height / sample) > MAX_DECODED_PIXELS) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val attempt: Bitmap? = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: OutOfMemoryError) {
            null
        }
        val decoded = attempt ?: return null

        val factor = min(maxWidth / decoded.width.toFloat(), maxHeight / decoded.height.toFloat())
        if (factor >= 1f) return decoded
        val scaled = decoded.scale(
            max(1, (decoded.width * factor).roundToInt()),
            max(1, (decoded.height * factor).roundToInt()),
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}

/** Keeps one small thumbnail per book in app storage. The original file is never copied. */
class CoverStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val directory = File(context.filesDir, "covers")

    /** Returns the saved file's path, or null if it could not be written. */
    fun save(contentHash: String, cover: Bitmap): String? = try {
        directory.mkdirs()
        val file = File(directory, "$contentHash.jpg")
        FileOutputStream(file).use { cover.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file.absolutePath
    } catch (e: Exception) {
        null
    }

    /** Deletes a book's thumbnail. The user's own file is not touched. */
    fun delete(coverPath: String) {
        try {
            val file = File(coverPath)
            // Only ever a file of our own making, whatever path the database holds.
            if (file.parentFile == directory) file.delete()
        } catch (e: Exception) {
            // A stray thumbnail costs a few kilobytes; it is not worth failing a removal over.
        }
    }

    /** A colour taken from the cover, to base the spine on. Not yet checked for contrast. */
    fun dominantColour(cover: Bitmap): Int? = try {
        val palette = Palette.from(cover).maximumColorCount(12).generate()
        (palette.darkMutedSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch)?.rgb
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Thumbnails are stored at twice the carousel's cover size in dp, enough for dense screens. */
        const val MAX_WIDTH = 600
        const val MAX_HEIGHT = 900
    }
}

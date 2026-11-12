package io.github.manrajsingh1.tsundoku.spike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A made-up library item. Nothing here is a real title. */
data class Book(
    val index: Int,
    val title: String,
    val author: String,
    val format: String,
    val spineWidth: Dp,
    val spineColor: Color,
    val coverIndex: Int,
)

private val titleHeads = listOf(
    "Paper", "Midnight", "Salt", "Copper", "Quiet", "Hollow", "Amber", "Winter",
    "Glass", "Northern", "Second", "Borrowed", "Last", "Slow", "Tin", "Walnut",
)
private val titleTails = listOf(
    "Lanterns", "Harbour", "Orchard", "Station", "Almanac", "Cartographer", "Ferry", "Archive",
    "Garden", "Signal", "Staircase", "Weather", "Atlas", "Kitchen", "Railway", "Tide",
)
private val authors = listOf(
    "A. Morimoto", "L. Okafor", "S. Lindqvist", "R. Banerjee", "T. Alvarez", "M. Kowalczyk", "E. Nakamura", "J. Hartley",
)
private val formats = listOf("CBZ", "CBZ", "EPUB", "PDF")

/** Deterministic sample data, so two runs of the spike draw exactly the same scene. */
fun sampleBooks(count: Int): List<Book> = List(count) { i ->
    val format = formats[i % formats.size]
    val head = titleHeads[(i * 7) % titleHeads.size]
    val tail = titleTails[(i * 5 + 3) % titleTails.size]
    val volume = if (format == "CBZ") " ${i % 12 + 1}" else ""
    Book(
        index = i,
        title = "$head $tail$volume",
        author = authors[(i * 3) % authors.size],
        format = format,
        // The brief clamps spines to 22..44 dp.
        spineWidth = (22 + (i * 5) % 23).dp,
        spineColor = Color.hsv(hue = (i * 47 % 360).toFloat(), saturation = 0.45f, value = 0.42f),
        coverIndex = i % CoverPool.SIZE,
    )
}

/**
 * Stand-ins for cover thumbnails. Real covers are bitmaps, so the spike draws bitmaps too:
 * a gradient drawn with vectors would be cheaper than the real thing and flatter the result.
 */
class CoverPool(widthPx: Int, heightPx: Int) {
    private val covers: List<ImageBitmap> = List(SIZE) { makeCover(it, widthPx, heightPx) }

    operator fun get(index: Int): ImageBitmap = covers[index % SIZE]

    private fun makeCover(index: Int, w: Int, h: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hue = (index * 47 % 360).toFloat()
        val top = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.78f))
        val bottom = android.graphics.Color.HSVToColor(floatArrayOf((hue + 40f) % 360f, 0.70f, 0.32f))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        paint.shader = null
        paint.color = android.graphics.Color.argb(46, 255, 255, 255)
        val step = w / 6f
        for (n in 0 until 5) {
            val radius = step * (1.2f + (index + n) % 4)
            canvas.drawCircle(w * (0.2f + 0.15f * n), h * (0.25f + 0.1f * ((index + n) % 5)), radius, paint)
        }
        paint.color = android.graphics.Color.argb(90, 0, 0, 0)
        canvas.drawRect(0f, h * 0.68f, w.toFloat(), h.toFloat(), paint)
        return bitmap.asImageBitmap()
    }

    companion object {
        const val SIZE = 16
    }
}

package io.github.manrajsingh1.tsundoku.core.model.importing

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Colours for a book's spine, as packed ARGB ints. Spine titles are drawn in white, so every
 * colour that leaves here is dark enough for white text to reach the 4.5:1 contrast the
 * accessibility guidelines ask of body text.
 */
object SpineColour {

    private const val WHITE = 0xFFFFFFFF.toInt()
    const val MIN_CONTRAST = 4.5

    /** A stable colour for a book with no cover: the same title always gets the same spine. */
    fun fromTitle(title: String): Int {
        val hue = Math.floorMod(title.trim().lowercase().hashCode(), 360).toFloat()
        return readableBehindWhite(hsvToArgb(hue, saturation = 0.45f, value = 0.42f))
    }

    /** Darkens [color] step by step until white text on it reaches [minimum] contrast. */
    fun readableBehindWhite(color: Int, minimum: Double = MIN_CONTRAST): Int {
        var red = (color shr 16) and 0xFF
        var green = (color shr 8) and 0xFF
        var blue = color and 0xFF
        var result = argb(red, green, blue)
        var steps = 0
        while (contrastRatio(result, WHITE) < minimum && steps < 64) {
            red = (red * 0.92f).toInt()
            green = (green * 0.92f).toInt()
            blue = (blue * 0.92f).toInt()
            result = argb(red, green, blue)
            steps++
        }
        return result
    }

    /** WCAG 2 contrast ratio between two opaque colours, from 1 (identical) to 21. */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val red = channel((color shr 16) and 0xFF)
        val green = channel((color shr 8) and 0xFF)
        val blue = channel(color and 0xFF)
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    private fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
        val chroma = value * saturation
        val sector = hue / 60f
        val x = chroma * (1f - kotlin.math.abs(sector % 2f - 1f))
        val (r, g, b) = when (sector.toInt()) {
            0 -> Triple(chroma, x, 0f)
            1 -> Triple(x, chroma, 0f)
            2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma)
            4 -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        val m = value - chroma
        return argb(((r + m) * 255f).toInt(), ((g + m) * 255f).toInt(), ((b + m) * 255f).toInt())
    }

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)
}

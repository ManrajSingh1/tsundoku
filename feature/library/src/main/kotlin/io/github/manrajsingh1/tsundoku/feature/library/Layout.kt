package io.github.manrajsingh1.tsundoku.feature.library

import io.github.manrajsingh1.tsundoku.core.model.BookFormat

/** How the window is divided between the library and the open shelf. */
internal data class PaneLayout(
    val twoPane: Boolean,
    /** The library's share of the width when there are two panes. */
    val listFraction: Float,
)

/**
 * One pane on phones and narrow windows; the library beside the open shelf once there is
 * room. The shares follow Samsung's large-screen guidance: 38:62 from 960 dp, 42:58 below
 * that, and half each either side of a foldable's hinge.
 */
internal fun paneLayoutFor(widthDp: Float, hasVerticalFold: Boolean): PaneLayout = when {
    hasVerticalFold && widthDp >= MEDIUM_WIDTH_DP -> PaneLayout(twoPane = true, listFraction = 0.5f)
    widthDp >= 960f -> PaneLayout(twoPane = true, listFraction = 0.38f)
    widthDp >= EXPANDED_WIDTH_DP -> PaneLayout(twoPane = true, listFraction = 0.42f)
    else -> PaneLayout(twoPane = false, listFraction = 1f)
}

private const val MEDIUM_WIDTH_DP = 600f
private const val EXPANDED_WIDTH_DP = 840f

/**
 * How big a spine is drawn, in dp. Thickness follows the page count and height follows the
 * format, so a shelf reads like real books: manga volumes short, documents tall.
 */
internal object SpineMetrics {
    const val MIN_WIDTH = 22f
    const val MAX_WIDTH = 44f
    const val MIN_HEIGHT = 150f
    const val MAX_HEIGHT = 205f

    private const val HEIGHT_RANGE = 20f
    private const val UNKNOWN_THICKNESS = 0.35f

    /** 0 for the thinnest book to 1 for the thickest that is drawn any thicker. */
    fun thickness(format: BookFormat, pageCount: Int?): Float {
        if (pageCount == null || pageCount <= 0) return UNKNOWN_THICKNESS
        val thickest = when (format) {
            BookFormat.CBZ -> 400f
            BookFormat.EPUB -> 600f
            BookFormat.PDF -> 500f
        }
        return (pageCount / thickest).coerceIn(0f, 1f)
    }

    fun width(format: BookFormat, pageCount: Int?): Float =
        MIN_WIDTH + (MAX_WIDTH - MIN_WIDTH) * thickness(format, pageCount)

    fun height(format: BookFormat, pageCount: Int?): Float {
        val shortest = when (format) {
            BookFormat.CBZ -> MIN_HEIGHT
            BookFormat.EPUB -> 170f
            BookFormat.PDF -> MAX_HEIGHT - HEIGHT_RANGE
        }
        return shortest + HEIGHT_RANGE * thickness(format, pageCount)
    }
}

/** A volume number as people write it: "3", not "3.0"; "3.5" stays "3.5". */
internal fun volumeNumber(volume: Double): String =
    if (volume % 1.0 == 0.0) volume.toLong().toString() else volume.toString()

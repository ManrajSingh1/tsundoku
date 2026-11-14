package io.github.manrajsingh1.tsundoku.feature.reader

import io.github.manrajsingh1.tsundoku.core.model.ReadingProgress

/** What a tap on a page asks for. */
internal enum class TapZone { PREVIOUS, NEXT, CONTROLS }

/** The reader's arithmetic, kept apart from the screen so it can be tested on its own. */
internal object ReaderMath {

    /** The share of the page's width, at each side, where a tap turns the page. */
    const val EDGE = 0.28f

    /**
     * Tapping an edge turns the page; tapping the middle shows or hides the controls. In a
     * book read right to left, the next page is on the left.
     */
    fun tapZone(x: Float, width: Int, rightToLeft: Boolean): TapZone {
        if (width <= 0) return TapZone.CONTROLS
        val share = x / width
        return when {
            share < EDGE -> if (rightToLeft) TapZone.NEXT else TapZone.PREVIOUS
            share > 1f - EDGE -> if (rightToLeft) TapZone.PREVIOUS else TapZone.NEXT
            else -> TapZone.CONTROLS
        }
    }

    /**
     * How far through an EPUB or PDF a Readium position is, from 0 to 1.
     *
     * @param paged true for a PDF, where a position is a page and the last page is the end.
     * @param position Readium's position number, counting from one.
     * @param totalProgression Readium's own estimate, which is where the visible page starts
     * and so never reaches 1 by itself.
     */
    fun locatorFraction(paged: Boolean, position: Int?, positionCount: Int, totalProgression: Double?): Double {
        if (position != null && positionCount > 0) {
            if (paged) return ReadingProgress.ofPage(position - 1, positionCount)
            if (positionCount > 1 && position >= positionCount) return 1.0
        }
        return (totalProgression ?: 0.0).coerceIn(0.0, 1.0)
    }

    /** The page a slider at [value] points to. */
    fun sliderPage(value: Float, pageCount: Int): Int =
        if (pageCount <= 0) 0 else Math.round(value).coerceIn(0, pageCount - 1)
}

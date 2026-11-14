package io.github.manrajsingh1.tsundoku.core.model

import kotlin.math.roundToInt

/** How far through a book the reader is, as the library shows it. */
enum class ReadingState { NOT_STARTED, IN_PROGRESS, FINISHED }

/**
 * Reading progress as plain arithmetic, shared by the readers that record it and the library
 * that shows it. A fraction runs from 0.0 (not started) to 1.0 (the last page has been seen).
 */
object ReadingProgress {

    fun state(fraction: Double?): ReadingState = when {
        fraction == null || fraction <= 0.0 -> ReadingState.NOT_STARTED
        fraction >= FINISHED_FROM -> ReadingState.FINISHED
        else -> ReadingState.IN_PROGRESS
    }

    /** A whole percentage. Only a finished book says 100, and only an unopened one says 0. */
    fun percent(fraction: Double?): Int = when (state(fraction)) {
        ReadingState.NOT_STARTED -> 0
        ReadingState.FINISHED -> 100
        ReadingState.IN_PROGRESS -> ((fraction ?: 0.0) * 100).roundToInt().coerceIn(1, 99)
    }

    /**
     * Progress through a book of pages. Page indexes count from zero. Looking at the first
     * page of a long book is not progress yet; looking at the last page is all of it.
     */
    fun ofPage(pageIndex: Int, pageCount: Int): Double {
        if (pageCount <= 0) return 0.0
        if (pageCount == 1) return 1.0
        return pageIndex.coerceIn(0, pageCount - 1).toDouble() / (pageCount - 1)
    }

    /** The page a stored position names, or the first page if it names none that exists. */
    fun pageOf(position: String?, pageCount: Int): Int {
        val page = position?.trim()?.toIntOrNull() ?: return 0
        return if (pageCount <= 0) 0 else page.coerceIn(0, pageCount - 1)
    }

    private const val FINISHED_FROM = 0.995
}

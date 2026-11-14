package io.github.manrajsingh1.tsundoku.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgressTest {

    @Test
    fun aBookIsNotStartedInProgressOrFinished() {
        assertEquals(ReadingState.NOT_STARTED, ReadingProgress.state(null))
        assertEquals(ReadingState.NOT_STARTED, ReadingProgress.state(0.0))
        assertEquals(ReadingState.IN_PROGRESS, ReadingProgress.state(0.004))
        assertEquals(ReadingState.IN_PROGRESS, ReadingProgress.state(0.99))
        assertEquals(ReadingState.FINISHED, ReadingProgress.state(1.0))
    }

    @Test
    fun onlyAFinishedBookSaysOneHundredPercent() {
        assertEquals(0, ReadingProgress.percent(null))
        assertEquals(1, ReadingProgress.percent(0.001))
        assertEquals(40, ReadingProgress.percent(0.4))
        assertEquals(99, ReadingProgress.percent(0.994))
        assertEquals(100, ReadingProgress.percent(1.0))
    }

    @Test
    fun pagesBecomeAFractionThatEndsAtOne() {
        assertEquals(0.0, ReadingProgress.ofPage(0, 20), 0.0)
        assertEquals(0.5, ReadingProgress.ofPage(5, 11), 1e-9)
        assertEquals(1.0, ReadingProgress.ofPage(19, 20), 0.0)
        assertEquals(1.0, ReadingProgress.ofPage(0, 1), 0.0)
        assertEquals(0.0, ReadingProgress.ofPage(3, 0), 0.0)
        assertEquals(1.0, ReadingProgress.ofPage(99, 20), 0.0)
    }

    @Test
    fun aStoredPositionIsReadBackAsAPageThatExists() {
        assertEquals(7, ReadingProgress.pageOf("7", 20))
        assertEquals(19, ReadingProgress.pageOf("400", 20))
        assertEquals(0, ReadingProgress.pageOf("-3", 20))
        assertEquals(0, ReadingProgress.pageOf(null, 20))
        assertEquals(0, ReadingProgress.pageOf("{\"href\":\"a\"}", 20))
        assertEquals(0, ReadingProgress.pageOf("4", 0))
    }
}

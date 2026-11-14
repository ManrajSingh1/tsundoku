package io.github.manrajsingh1.tsundoku.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderMathTest {

    @Test
    fun inABookReadLeftToRightTheNextPageIsOnTheRight() {
        assertEquals(TapZone.PREVIOUS, ReaderMath.tapZone(50f, 1000, rightToLeft = false))
        assertEquals(TapZone.CONTROLS, ReaderMath.tapZone(500f, 1000, rightToLeft = false))
        assertEquals(TapZone.NEXT, ReaderMath.tapZone(950f, 1000, rightToLeft = false))
    }

    @Test
    fun inMangaTheNextPageIsOnTheLeft() {
        assertEquals(TapZone.NEXT, ReaderMath.tapZone(50f, 1000, rightToLeft = true))
        assertEquals(TapZone.CONTROLS, ReaderMath.tapZone(500f, 1000, rightToLeft = true))
        assertEquals(TapZone.PREVIOUS, ReaderMath.tapZone(950f, 1000, rightToLeft = true))
    }

    @Test
    fun aPageThatHasNotBeenMeasuredOnlyTogglesTheControls() {
        assertEquals(TapZone.CONTROLS, ReaderMath.tapZone(10f, 0, rightToLeft = true))
    }

    private fun fraction(paged: Boolean, position: Int?, count: Int, progression: Double?) =
        ReaderMath.locatorFraction(paged, position, count, progression)

    @Test
    fun aPdfIsFinishedOnItsLastPage() {
        assertEquals(0.0, fraction(true, 1, 40, 0.0), 0.0)
        assertEquals(1.0, fraction(true, 40, 40, 0.975), 0.0)
        assertEquals(0.5, fraction(true, 21, 41, null), 1e-9)
    }

    @Test
    fun anEpubFollowsReadiumUntilItsLastPositionWhichIsTheEnd() {
        assertEquals(0.4, fraction(false, 12, 30, 0.4), 0.0)
        assertEquals(1.0, fraction(false, 30, 30, 0.97), 0.0)
        // A book of one position cannot be told apart this way; Readium's estimate stands.
        assertEquals(0.6, fraction(false, 1, 1, 0.6), 0.0)
    }

    @Test
    fun aLocatorWithNothingUsableCountsAsNotStarted() {
        assertEquals(0.0, fraction(true, null, 0, null), 0.0)
    }

    @Test
    fun theSliderAlwaysPointsAtAPageThatExists() {
        assertEquals(7, ReaderMath.sliderPage(7.4f, 20))
        assertEquals(19, ReaderMath.sliderPage(25f, 20))
        assertEquals(0, ReaderMath.sliderPage(-2f, 20))
        assertEquals(0, ReaderMath.sliderPage(3f, 0))
    }
}

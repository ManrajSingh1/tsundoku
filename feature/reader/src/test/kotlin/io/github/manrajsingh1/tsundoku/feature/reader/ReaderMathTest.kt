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

    @Test
    fun aPdfIsFinishedOnItsLastPage() {
        assertEquals(0.0, ReaderMath.locatorFraction(paged = true, position = 1, positionCount = 40, totalProgression = 0.0), 0.0)
        assertEquals(1.0, ReaderMath.locatorFraction(paged = true, position = 40, positionCount = 40, totalProgression = 0.975), 0.0)
        assertEquals(0.5, ReaderMath.locatorFraction(paged = true, position = 21, positionCount = 41, totalProgression = null), 1e-9)
    }

    @Test
    fun anEpubFollowsReadiumUntilItsLastPositionWhichIsTheEnd() {
        assertEquals(0.4, ReaderMath.locatorFraction(paged = false, position = 12, positionCount = 30, totalProgression = 0.4), 0.0)
        assertEquals(1.0, ReaderMath.locatorFraction(paged = false, position = 30, positionCount = 30, totalProgression = 0.97), 0.0)
        // A book of one position cannot be told apart this way; Readium's estimate stands.
        assertEquals(0.6, ReaderMath.locatorFraction(paged = false, position = 1, positionCount = 1, totalProgression = 0.6), 0.0)
    }

    @Test
    fun aLocatorWithNothingUsableCountsAsNotStarted() {
        assertEquals(0.0, ReaderMath.locatorFraction(paged = true, position = null, positionCount = 0, totalProgression = null), 0.0)
    }

    @Test
    fun theSliderAlwaysPointsAtAPageThatExists() {
        assertEquals(7, ReaderMath.sliderPage(7.4f, 20))
        assertEquals(19, ReaderMath.sliderPage(25f, 20))
        assertEquals(0, ReaderMath.sliderPage(-2f, 20))
        assertEquals(0, ReaderMath.sliderPage(3f, 0))
    }
}

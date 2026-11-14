package io.github.manrajsingh1.tsundoku.feature.library

import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselPoseTest {

    // The prototype's reference values, one pixel to the dp.
    private val m = CarouselMetrics(
        firstStep = 118f,
        furtherStep = 50f,
        depthPush = 60f,
        perspective = 1000f,
        shelfStep = 48f,
        coverWidth = 188f,
        coverHeight = 282f,
    )

    @Test
    fun theCentredBookFacesFrontAndItsNeighboursTurnTowardsIt() {
        assertEquals(0f, CarouselPose.angle(0f, 1f, flat = false), 0f)
        // A book on the right turns its right edge towards the viewer: its page edge shows.
        assertEquals(-58f, CarouselPose.angle(1f, 1f, flat = false), 0f)
        assertEquals(58f, CarouselPose.angle(-1f, 1f, flat = false), 0f)
    }

    @Test
    fun theTurnIsCappedAtOnePosition() {
        assertEquals(-58f, CarouselPose.angle(4f, 1f, flat = false), 0f)
        assertEquals(58f, CarouselPose.angle(-2.5f, 1f, flat = false), 0f)
    }

    @Test
    fun beforeOpeningEveryBookIsASpineAtReducedScale() {
        assertEquals(90f, CarouselPose.angle(0f, 0f, flat = false), 0f)
        assertEquals(90f, CarouselPose.angle(3f, 0f, flat = false), 0f)
        assertEquals(0.62f, CarouselPose.scale(2f, 0f, m), 1e-6f)
    }

    @Test
    fun theFlatFallbackNeverTurnsABook() {
        assertEquals(0f, CarouselPose.angle(1f, 1f, flat = true), 0f)
        assertEquals(0f, CarouselPose.angle(-3f, 0.4f, flat = true), 0f)
    }

    @Test
    fun booksStandFurtherBackWithDistanceToAtMostThreePlaces() {
        assertEquals(1f, CarouselPose.scale(0f, 1f, m), 1e-6f)
        assertEquals(1000f / 1060f, CarouselPose.scale(1f, 1f, m), 1e-6f)
        assertEquals(1000f / 1180f, CarouselPose.scale(3f, 1f, m), 1e-6f)
        assertEquals(CarouselPose.scale(3f, 1f, m), CarouselPose.scale(7f, 1f, m), 0f)
    }

    @Test
    fun sideCoversDarkenWithDistanceToAtMostFiftyFivePercent() {
        assertEquals(0f, CarouselPose.shade(0f, 1f), 0f)
        assertEquals(0.22f, CarouselPose.shade(-1f, 1f), 1e-6f)
        assertEquals(0.44f, CarouselPose.shade(2f, 1f), 1e-6f)
        assertEquals(0.55f, CarouselPose.shade(3f, 1f), 1e-6f)
        assertEquals(0f, CarouselPose.shade(3f, 0f), 0f)
    }

    @Test
    fun slotsAre118ThenFiftyApart() {
        assertEquals(0f, CarouselPose.slot(0f, m), 0f)
        assertEquals(118f, CarouselPose.slot(1f, m), 0f)
        assertEquals(-168f, CarouselPose.slot(-2f, m), 0f)
        assertEquals(218f, CarouselPose.slot(3f, m), 0f)
    }

    /** Where the middle of a cover ends up on screen, ignoring perspective. */
    private fun coverCentre(d: Float): Float {
        val angle = CarouselPose.angle(d, 1f, flat = false)
        val scale = CarouselPose.scale(d, 1f, m)
        val hinge = CarouselPose.hingeFromCentre(angle, m)
        val foreshortening = scale * cos(Math.toRadians(angle.toDouble())).toFloat()
        // The hinge stays put under the turn; the centre swings in towards it.
        return CarouselPose.shift(d, 1f, angle, scale, m) + hinge * (1f - foreshortening)
    }

    @Test
    fun everyCoverIsCentredOnItsSlotWhicheverEdgeItTurnsAbout() {
        for (d in listOf(-3f, -2f, -1f, -0.4f, 0f, 0.4f, 1f, 2f, 3f)) {
            assertEquals("book at $d", CarouselPose.slot(d, m), coverCentre(d), 1e-3f)
        }
    }

    @Test
    fun aBookDoesNotJumpAsItCrossesTheCentreAndItsHingeSwapsSides() {
        val justLeft = coverCentre(-0.001f)
        val justRight = coverCentre(0.001f)
        assertTrue(justRight - justLeft < 1f)
    }

    @Test
    fun aDragSettlesOnTheNearestBookAndAFlingCarriesItFurther() {
        assertEquals(2, CarouselPose.settleTarget(2.4f, 0f, 10))
        assertEquals(3, CarouselPose.settleTarget(2.6f, 0f, 10))
        assertEquals(4, CarouselPose.settleTarget(2.4f, 8f, 10))
        assertEquals(0, CarouselPose.settleTarget(1.2f, -30f, 10))
        assertEquals(9, CarouselPose.settleTarget(8.7f, 30f, 10))
        assertEquals(0, CarouselPose.settleTarget(0f, 5f, 1))
    }

    @Test
    fun aTapMovesToTheBookThatWasTapped() {
        assertEquals(0, CarouselPose.tapSteps(0f, m))
        assertEquals(0, CarouselPose.tapSteps(-90f, m))
        assertEquals(1, CarouselPose.tapSteps(120f, m))
        assertEquals(-1, CarouselPose.tapSteps(-120f, m))
        assertEquals(2, CarouselPose.tapSteps(170f, m))
        assertEquals(-3, CarouselPose.tapSteps(-220f, m))
        assertEquals(3, CarouselPose.tapSteps(900f, m))
    }
}

package io.github.manrajsingh1.tsundoku.core.model.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpineColourTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun `contrast of black on white is 21 and of a colour with itself is 1`() {
        assertEquals(21.0, SpineColour.contrastRatio(black, white), 0.01)
        assertEquals(1.0, SpineColour.contrastRatio(white, white), 0.0001)
    }

    @Test
    fun `a pale cover colour is darkened until white text is readable`() {
        val paleYellow = 0xFFFFF3B0.toInt()
        val spine = SpineColour.readableBehindWhite(paleYellow)
        assertTrue(SpineColour.contrastRatio(spine, white) >= SpineColour.MIN_CONTRAST)
        assertNotEquals(paleYellow, spine)
    }

    @Test
    fun `a colour that is already dark enough is left alone`() {
        val navy = 0xFF14213D.toInt()
        assertEquals(navy, SpineColour.readableBehindWhite(navy))
    }

    @Test
    fun `pure white still ends up readable`() {
        assertTrue(SpineColour.contrastRatio(SpineColour.readableBehindWhite(white), white) >= SpineColour.MIN_CONTRAST)
    }

    @Test
    fun `a title always gets the same readable colour`() {
        val first = SpineColour.fromTitle("Vinland Saga")
        assertEquals(first, SpineColour.fromTitle("  vinland saga "))
        assertTrue(SpineColour.contrastRatio(first, white) >= SpineColour.MIN_CONTRAST)
    }

    @Test
    fun `every hue produces an opaque readable colour`() {
        for (i in 0 until 500) {
            val colour = SpineColour.fromTitle("Book number $i")
            assertEquals(0xFF, (colour ushr 24) and 0xFF)
            assertTrue(SpineColour.contrastRatio(colour, white) >= SpineColour.MIN_CONTRAST)
        }
    }
}

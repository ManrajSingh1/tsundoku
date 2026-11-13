package io.github.manrajsingh1.tsundoku.feature.library

import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {

    @Test
    fun `phones and narrow windows get one pane`() {
        for (width in listOf(320f, 411f, 599f, 700f, 839f)) {
            assertEquals("at $width dp", false, paneLayoutFor(width, 900f, hasVerticalFold = false).twoPane)
        }
    }

    @Test
    fun `wide windows get two panes split as Samsung recommends`() {
        val narrower = PaneLayout(twoPane = true, listFraction = 0.42f)
        val wider = PaneLayout(twoPane = true, listFraction = 0.38f)
        assertEquals(narrower, paneLayoutFor(840f, 800f, hasVerticalFold = false))
        assertEquals(narrower, paneLayoutFor(959f, 800f, hasVerticalFold = false))
        assertEquals(wider, paneLayoutFor(960f, 800f, hasVerticalFold = false))
        assertEquals(wider, paneLayoutFor(1280f, 800f, hasVerticalFold = false))
    }

    @Test
    fun `an unfolded foldable is split in half at the hinge`() {
        val halves = PaneLayout(twoPane = true, listFraction = 0.5f)
        assertEquals(halves, paneLayoutFor(690f, 829f, hasVerticalFold = true))
        assertEquals(halves, paneLayoutFor(1000f, 700f, hasVerticalFold = true))
    }

    @Test
    fun `a fold in a window too narrow for two panes is ignored`() {
        assertEquals(false, paneLayoutFor(400f, 900f, hasVerticalFold = true).twoPane)
    }

    @Test
    fun `a phone on its side is wide but too short for two panes`() {
        assertEquals(false, paneLayoutFor(891f, 411f, hasVerticalFold = false).twoPane)
        assertEquals(false, paneLayoutFor(960f, 479f, hasVerticalFold = true).twoPane)
        assertEquals(true, paneLayoutFor(960f, 480f, hasVerticalFold = false).twoPane)
    }

    @Test
    fun `every spine stays inside the sizes the design allows`() {
        for (format in BookFormat.entries) {
            for (pages in listOf(null, 0, 1, 40, 200, 400, 5_000)) {
                val width = SpineMetrics.width(format, pages)
                val height = SpineMetrics.height(format, pages)
                assertTrue("$format $pages width $width", width in SpineMetrics.MIN_WIDTH..SpineMetrics.MAX_WIDTH)
                assertTrue("$format $pages height $height", height in SpineMetrics.MIN_HEIGHT..SpineMetrics.MAX_HEIGHT)
            }
        }
    }

    @Test
    fun `a longer book has a thicker spine`() {
        assertTrue(SpineMetrics.width(BookFormat.CBZ, 300) > SpineMetrics.width(BookFormat.CBZ, 100))
        assertEquals(SpineMetrics.MAX_WIDTH, SpineMetrics.width(BookFormat.PDF, 10_000), 0.001f)
    }

    @Test
    fun `manga stands shorter than novels and novels shorter than documents`() {
        val manga = SpineMetrics.height(BookFormat.CBZ, 200)
        val novel = SpineMetrics.height(BookFormat.EPUB, 300)
        val document = SpineMetrics.height(BookFormat.PDF, 250)
        assertTrue(manga < novel)
        assertTrue(novel < document)
    }

    @Test
    fun `volume numbers are written the way people write them`() {
        assertEquals("3", volumeNumber(3.0))
        assertEquals("12", volumeNumber(12.0))
        assertEquals("3.5", volumeNumber(3.5))
    }

    @Test
    fun `a new shelf name avoids the ones already taken`() {
        assertEquals("New shelf", uniqueShelfName("New shelf", listOf("Manga")))
        assertEquals("New shelf 2", uniqueShelfName("New shelf", listOf("Manga", "New shelf")))
        assertEquals("New shelf 3", uniqueShelfName("New shelf", listOf("New shelf", "New shelf 2")))
    }
}

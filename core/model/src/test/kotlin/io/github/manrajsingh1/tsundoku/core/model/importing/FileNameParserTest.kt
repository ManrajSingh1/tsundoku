package io.github.manrajsingh1.tsundoku.core.model.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNameParserTest {

    @Test
    fun `a volume marker gives the series and volume`() {
        val parsed = FileNameParser.parse("One Piece v012 (2019) (Digital) [Group].cbz", inferSeries = true)
        assertEquals("One Piece v012", parsed.title)
        assertEquals("One Piece", parsed.series)
        assertEquals(12.0, parsed.volume!!, 0.0)
    }

    @Test
    fun `underscores and a bare trailing number are understood`() {
        val parsed = FileNameParser.parse("Berserk_03.cbz", inferSeries = true)
        assertEquals("Berserk 03", parsed.title)
        assertEquals("Berserk", parsed.series)
        assertEquals(3.0, parsed.volume!!, 0.0)
    }

    @Test
    fun `spelled-out volume and a separator before it`() {
        val parsed = FileNameParser.parse("Vinland Saga - Vol. 7.cbz", inferSeries = true)
        assertEquals("Vinland Saga", parsed.series)
        assertEquals(7.0, parsed.volume!!, 0.0)
    }

    @Test
    fun `a half chapter keeps its fraction`() {
        val parsed = FileNameParser.parse("Dungeon Meshi ch 12.5.cbz", inferSeries = true)
        assertEquals("Dungeon Meshi", parsed.series)
        assertEquals(12.5, parsed.volume!!, 0.0)
    }

    @Test
    fun `a number inside the series name is not the volume`() {
        val parsed = FileNameParser.parse("20th Century Boys v01.cbz", inferSeries = true)
        assertEquals("20th Century Boys", parsed.series)
        assertEquals(1.0, parsed.volume!!, 0.0)
    }

    @Test
    fun `a comic with no number has no series`() {
        val parsed = FileNameParser.parse("Watchmen.cbz", inferSeries = true)
        assertEquals("Watchmen", parsed.title)
        assertNull(parsed.series)
        assertNull(parsed.volume)
    }

    @Test
    fun `a year at the end is not a volume`() {
        val parsed = FileNameParser.parse("Annual 2024.cbz", inferSeries = true)
        assertNull(parsed.series)
        assertNull(parsed.volume)
    }

    @Test
    fun `novels and documents keep their numbers in the title`() {
        val parsed = FileNameParser.parse("Catch 22.epub", inferSeries = false)
        assertEquals("Catch 22", parsed.title)
        assertNull(parsed.series)
        assertNull(parsed.volume)
    }

    @Test
    fun `a name that is nothing but tags falls back to the name itself`() {
        val parsed = FileNameParser.parse("[scan].pdf", inferSeries = false)
        assertEquals("[scan]", parsed.title)
    }
}

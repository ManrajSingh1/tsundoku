package io.github.manrajsingh1.tsundoku.core.model.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ComicInfoParserTest {

    private fun comicInfo(body: String) =
        """<?xml version="1.0" encoding="utf-8"?><ComicInfo>$body</ComicInfo>""".toByteArray()

    @Test
    fun `reads the fields the library uses`() {
        val info = ComicInfoParser.parse(
            comicInfo(
                "<Title>Romance Dawn</Title><Series>One Piece</Series><Number>1</Number>" +
                    "<Writer>Eiichiro Oda</Writer><PageCount>208</PageCount><Manga>YesAndRightToLeft</Manga>",
            ),
        )
        assertNotNull(info)
        assertEquals("Romance Dawn", info!!.title)
        assertEquals("One Piece", info.series)
        assertEquals(1.0, info.number!!, 0.0)
        assertEquals("Eiichiro Oda", info.writer)
        assertEquals(208, info.pageCount)
        assertEquals(true, info.rightToLeft)
    }

    @Test
    fun `manga flag No means left to right`() {
        assertEquals(false, ComicInfoParser.parse(comicInfo("<Manga>No</Manga>"))!!.rightToLeft)
    }

    @Test
    fun `manga flag Yes or Unknown leaves the direction open`() {
        assertNull(ComicInfoParser.parse(comicInfo("<Manga>Yes</Manga>"))!!.rightToLeft)
        assertNull(ComicInfoParser.parse(comicInfo("<Manga>Unknown</Manga>"))!!.rightToLeft)
        assertNull(ComicInfoParser.parse(comicInfo("<Title>x</Title>"))!!.rightToLeft)
    }

    @Test
    fun `blank and malformed values become null`() {
        val info = ComicInfoParser.parse(comicInfo("<Title>  </Title><Number>one</Number><PageCount>-4</PageCount>"))!!
        assertNull(info.title)
        assertNull(info.number)
        assertNull(info.pageCount)
    }

    @Test
    fun `a different root element is not ComicInfo`() {
        assertNull(ComicInfoParser.parse("<html><Title>x</Title></html>".toByteArray()))
    }

    @Test
    fun `broken XML is rejected without throwing`() {
        assertNull(ComicInfoParser.parse("<ComicInfo><Title>unclosed".toByteArray()))
        assertNull(ComicInfoParser.parse(ByteArray(0)))
    }

    @Test
    fun `a document type declaration is refused, so entity tricks cannot run`() {
        val hostile = (
            """<?xml version="1.0"?><!DOCTYPE ComicInfo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>""" +
                "<ComicInfo><Title>&xxe;</Title></ComicInfo>"
            ).toByteArray()
        val info = ComicInfoParser.parse(hostile)
        // Either the whole file is refused, or the entity is left unexpanded. Never its content.
        assertNull(info?.title?.takeIf { it.contains("root:") })
    }

    @Test
    fun `an oversized file is refused before parsing`() {
        val huge = comicInfo("<Title>" + "a".repeat(ComicInfoParser.MAX_BYTES) + "</Title>")
        assertNull(ComicInfoParser.parse(huge))
    }
}

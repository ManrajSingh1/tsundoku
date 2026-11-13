package io.github.manrajsingh1.tsundoku.core.model.importing

import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatSnifferTest {

    private val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(26)
    private val plainZip = zipHeader + "page001.png".toByteArray()
    private val epubZip = zipHeader + "mimetypeapplication/epub+zip".toByteArray()
    private val pdf = "%PDF-1.7\n%âãÏÓ".toByteArray(Charsets.ISO_8859_1)

    @Test
    fun `a PDF is recognised by its first bytes whatever it is called`() {
        assertEquals(BookFormat.PDF, FormatSniffer.sniff(pdf, "holiday.jpg", "image/jpeg"))
    }

    @Test
    fun `an EPUB is recognised by its mimetype entry even with the wrong extension`() {
        assertEquals(BookFormat.EPUB, FormatSniffer.sniff(epubZip, "novel.zip", null))
    }

    @Test
    fun `a zip named cbz is a comic`() {
        assertEquals(BookFormat.CBZ, FormatSniffer.sniff(plainZip, "Akira 01.CBZ", null))
    }

    @Test
    fun `a zip with a comic MIME type is a comic`() {
        assertEquals(BookFormat.CBZ, FormatSniffer.sniff(plainZip, "download", "application/vnd.comicbook+zip"))
    }

    @Test
    fun `any other zip is not a book`() {
        assertNull(FormatSniffer.sniff(plainZip, "backup.zip", "application/zip"))
        assertNull(FormatSniffer.sniff(plainZip, "report.docx", null))
    }

    @Test
    fun `a file whose name says cbz but whose bytes are not a zip is rejected`() {
        assertNull(FormatSniffer.sniff("GIF89a".toByteArray(), "fake.cbz", "application/x-cbz"))
    }

    @Test
    fun `an empty or tiny file is rejected`() {
        assertNull(FormatSniffer.sniff(ByteArray(0), "empty.pdf", "application/pdf"))
        assertNull(FormatSniffer.sniff(byteArrayOf(0x50, 0x4B), "short.cbz", null))
    }
}

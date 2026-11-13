package io.github.manrajsingh1.tsundoku.core.model.importing

import io.github.manrajsingh1.tsundoku.core.model.BookFormat

/**
 * Decides what a file is from its first bytes, using its name only to tell one kind of zip
 * from another. A file's name and MIME type are claims; its bytes are evidence.
 */
object FormatSniffer {

    /** How many leading bytes [sniff] wants. An EPUB declares itself within the first 58. */
    const val HEAD_SIZE = 64

    private val pdfMagic = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val zipMagic = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val epubMarker = "mimetypeapplication/epub+zip".toByteArray(Charsets.US_ASCII)

    fun sniff(head: ByteArray, displayName: String?, mimeType: String?): BookFormat? {
        if (head.startsWith(pdfMagic)) return BookFormat.PDF
        if (!head.startsWith(zipMagic)) return null

        val name = displayName.orEmpty().lowercase()
        // The EPUB spec puts an uncompressed "mimetype" entry first, so its content is
        // readable right after the 30-byte zip header and the 8-byte entry name.
        if (head.contains(epubMarker) || mimeType == "application/epub+zip" || name.endsWith(".epub")) {
            return BookFormat.EPUB
        }
        if (name.endsWith(".cbz") || mimeType in comicMimeTypes) return BookFormat.CBZ
        // Any other zip (an Office document, an APK, a plain archive) is not a book.
        return null
    }

    private val comicMimeTypes = setOf(
        "application/vnd.comicbook+zip",
        "application/x-cbz",
        "application/x-cdisplay",
    )

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        for (start in 0..size - needle.size) {
            var matched = true
            for (i in needle.indices) {
                if (this[start + i] != needle[i]) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }
}

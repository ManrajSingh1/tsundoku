package io.github.manrajsingh1.tsundoku.core.data.importing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Small, valid books built on the device for the import tests, so no binary files are
 * checked into the repository and each test states exactly what its file contains.
 */
object Fixtures {

    fun png(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        canvas.drawCircle(width / 2f, height / 2f, width / 4f, Paint().apply { this.color = Color.WHITE })
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /** A comic archive. Pages are named so that only a natural sort puts page 10 last. */
    fun cbz(file: File, pages: Int = 3, comicInfo: String? = null): File {
        ZipOutputStream(file.outputStream()).use { zip ->
            val names = listOf("page_2.png", "page_10.png", "page_1.png", "page_3.png", "page_4.png").take(pages)
            names.forEachIndexed { index, name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(png(300, 450, Color.rgb(40 + index * 40, 80, 160)))
                zip.closeEntry()
            }
            if (comicInfo != null) {
                zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                zip.write(comicInfo.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    const val MANGA_COMIC_INFO =
        """<?xml version="1.0" encoding="utf-8"?><ComicInfo><Title>The Third Volume</Title>""" +
            "<Series>Test Manga</Series><Number>3</Number><Writer>A. Author</Writer>" +
            "<Manga>YesAndRightToLeft</Manga></ComicInfo>"

    /** A minimal EPUB 3: one chapter, a declared cover image, a title and an author. */
    fun epub(file: File, title: String = "A Test Novel", author: String = "Nora Novelist"): File {
        ZipOutputStream(file.outputStream()).use { zip ->
            // The EPUB container format requires this entry first and uncompressed.
            val mimetype = "application/epub+zip".toByteArray()
            val entry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetype.size.toLong()
                compressedSize = mimetype.size.toLong()
                crc = CRC32().apply { update(mimetype) }.value
            }
            zip.putNextEntry(entry)
            zip.write(mimetype)
            zip.closeEntry()

            fun add(name: String, content: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
            add(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray(),
            )
            add(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="id">urn:uuid:11111111-2222-3333-4444-555555555555</dc:identifier>
    <dc:title>$title</dc:title>
    <dc:creator>$author</dc:creator>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/>
  </manifest>
  <spine>
    <itemref idref="chapter1"/>
  </spine>
</package>""".toByteArray(),
            )
            add(
                "OEBPS/nav.xhtml",
                """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol><li><a href="chapter1.xhtml">Chapter 1</a></li></ol></nav></body></html>""".toByteArray(),
            )
            add(
                "OEBPS/chapter1.xhtml",
                """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter 1</title></head><body><h1>Chapter 1</h1><p>It was a quiet night on the shelf.</p></body></html>""".toByteArray(),
            )
            add("OEBPS/cover.png", png(400, 600, Color.rgb(160, 60, 40)))
        }
        return file
    }

    /** A text-only PDF with the given number of pages and no title of its own. */
    fun pdf(file: File, pages: Int = 5): File {
        val objects = sortedMapOf<Int, String>()
        objects[1] = "<< /Type /Catalog /Pages 2 0 R >>"
        objects[3] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        val kids = ArrayList<String>()
        var number = 3
        for (page in 1..pages) {
            val pageId = number + 1
            val contentId = number + 2
            number += 2
            kids.add("$pageId 0 R")
            val stream = "BT /F1 18 Tf 50 780 Td (Test document, page $page of $pages) Tj ET"
            objects[pageId] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                "/Resources << /Font << /F1 3 0 R >> >> /Contents $contentId 0 R >>"
            objects[contentId] = "<< /Length ${stream.length} >>\nstream\n$stream\nendstream"
        }
        objects[2] = "<< /Type /Pages /Kids [${kids.joinToString(" ")}] /Count $pages >>"

        val out = ByteArrayOutputStream()
        fun write(text: String) = out.write(text.toByteArray(Charsets.ISO_8859_1))
        write("%PDF-1.4\n")
        val offsets = HashMap<Int, Int>()
        for ((id, body) in objects) {
            offsets[id] = out.size()
            write("$id 0 obj\n$body\nendobj\n")
        }
        val xrefAt = out.size()
        write("xref\n0 ${objects.size + 1}\n")
        write("0000000000 65535 f \n")
        for (id in objects.keys) write("%010d 00000 n \n".format(offsets[id]))
        write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefAt\n%%EOF\n")
        file.writeBytes(out.toByteArray())
        return file
    }

    /**
     * A PNG that is only a header, declaring an image far larger than any device could hold
     * in memory. A decoder that trusted the header would try to allocate it.
     */
    fun pngClaimingSize(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val header = ByteArrayOutputStream()
        header.write("IHDR".toByteArray())
        header.write(intBytes(width))
        header.write(intBytes(height))
        header.write(byteArrayOf(8, 6, 0, 0, 0)) // 8 bits per channel, RGBA
        val chunk = header.toByteArray()
        out.write(intBytes(13))
        out.write(chunk)
        out.write(intBytes(CRC32().apply { update(chunk) }.value.toInt()))
        return out.toByteArray()
    }

    private fun intBytes(value: Int) = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )
}

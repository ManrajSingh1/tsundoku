package io.github.manrajsingh1.tsundoku.core.model.importing

import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** The fields of a comic archive's `ComicInfo.xml` that the library uses. */
data class ComicInfo(
    val title: String?,
    val series: String?,
    val number: Double?,
    val writer: String?,
    val pageCount: Int?,
    /**
     * True when the file says it reads right to left (`Manga` = `YesAndRightToLeft`),
     * false when it says it is not manga, null when it does not say.
     */
    val rightToLeft: Boolean?,
)

/**
 * Reads `ComicInfo.xml`, the metadata file comic archives carry. The input comes from a
 * file the user was given by someone else, so it is treated as hostile: it is size-capped
 * and parsed with document types and entity expansion switched off.
 */
object ComicInfoParser {

    const val MAX_BYTES = 1024 * 1024

    fun parse(xml: ByteArray): ComicInfo? {
        if (xml.isEmpty() || xml.size > MAX_BYTES) return null
        val root = try {
            val factory = DocumentBuilderFactory.newInstance()
            // Not every platform's parser knows every switch; each one that is known is set.
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { factory.isXIncludeAware = false }
            factory.isExpandEntityReferences = false
            factory.newDocumentBuilder().parse(ByteArrayInputStream(xml)).documentElement
        } catch (e: Exception) {
            return null
        }
        if (root == null || root.tagName != "ComicInfo") return null

        val manga = root.childText("Manga")
        return ComicInfo(
            title = root.childText("Title"),
            series = root.childText("Series"),
            number = root.childText("Number")?.toDoubleOrNull(),
            writer = root.childText("Writer"),
            pageCount = root.childText("PageCount")?.toIntOrNull()?.takeIf { it > 0 },
            rightToLeft = when {
                manga == null -> null
                manga.equals("YesAndRightToLeft", ignoreCase = true) -> true
                manga.equals("No", ignoreCase = true) -> false
                // "Yes" marks the book as manga without stating a direction; "Unknown" says nothing.
                else -> null
            },
        )
    }

    private fun Element.childText(name: String): String? {
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == name) {
                return node.textContent?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }
}

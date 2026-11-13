package io.github.manrajsingh1.tsundoku.core.model.importing

/** What a file's name alone says about the book. Used when the file carries no metadata. */
data class ParsedName(
    val title: String,
    val series: String?,
    val volume: Double?,
)

object FileNameParser {

    private val extension = Regex("\\.(cbz|epub|pdf|zip)$", RegexOption.IGNORE_CASE)

    // Scanlation and release tags: "[Group]", "(2019)", "(Digital)", "{v2}".
    private val tags = Regex("[\\[({][^\\])}]*[\\])}]")

    // "v012", "Vol. 3", "volume 10", "#4", "ch 12.5", "c003".
    private val volumeMarker = Regex(
        "(?:^|[\\s\\-.,])(?:volume|vol\\.?|v|chapter|ch\\.?|c|#)\\s*(\\d{1,4}(?:\\.\\d+)?)(?=$|[\\s\\-.,])",
        RegexOption.IGNORE_CASE,
    )

    // "Berserk 03": a bare number at the very end. Capped at three digits so a year is not a volume.
    private val trailingNumber = Regex("(?:\\s|-)(\\d{1,3}(?:\\.\\d+)?)$")

    private val spaces = Regex("\\s+")
    private val danglingSeparators = Regex("[\\s\\-.,:]+$")

    /**
     * @param inferSeries look for a series and volume in the name. Right for comics, whose
     * files are nearly always named "Series v01"; wrong for novels and documents, where a
     * trailing number is usually part of the title.
     */
    fun parse(displayName: String, inferSeries: Boolean): ParsedName {
        val cleaned = displayName
            .replace(extension, "")
            .replace('_', ' ')
            .replace(tags, " ")
            .replace(spaces, " ")
            .trim()
        val title = cleaned.ifEmpty { displayName.replace(extension, "").trim().ifEmpty { displayName } }
        if (!inferSeries) return ParsedName(title = title, series = null, volume = null)

        val match = volumeMarker.find(title) ?: trailingNumber.find(title)
        if (match != null) {
            val series = title.substring(0, match.range.first).replace(danglingSeparators, "").trim()
            val volume = match.groupValues[1].toDoubleOrNull()
            if (series.isNotEmpty() && volume != null) {
                return ParsedName(title = title, series = series, volume = volume)
            }
        }
        return ParsedName(title = title, series = null, volume = null)
    }
}

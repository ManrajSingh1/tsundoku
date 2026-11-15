package io.github.manrajsingh1.tsundoku.core.data.backup

import io.github.manrajsingh1.tsundoku.core.model.AppPreferences
import io.github.manrajsingh1.tsundoku.core.model.ColourTheme
import io.github.manrajsingh1.tsundoku.core.model.PageTheme
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.ThemeMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * One book as a backup remembers it. The file itself is not in the backup; [hash] is the
 * fingerprint of its contents, which is how the book is recognised when it is added again,
 * wherever it then lives and whatever it is then called.
 *
 * @param position where the reader was, in the form the book's reader understands.
 * @param fraction how far through the book that is, from 0 to 1.
 */
data class BackupBook(
    val hash: String,
    val title: String,
    val direction: ReadingDirection = ReadingDirection.AUTO,
    val position: String? = null,
    val fraction: Double? = null,
    val progressAt: Long? = null,
)

/** A shelf and its books, in order. */
data class BackupShelf(val name: String, val books: List<BackupBook>)

/** Everything the app knows that is not in the user's own files: shelves in order, and settings. */
data class LibraryBackup(
    val exportedAt: Long,
    val preferences: AppPreferences,
    val epubFontSize: Double,
    val shelves: List<BackupShelf>,
)

/** Reads and writes a backup as JSON: one small file a person could open and understand. */
object BackupCodec {
    const val FORMAT = "tsundoku-backup"
    const val VERSION = 1

    // A backup comes from outside the app, so nothing in one is trusted to be a sane size.
    private const val MAX_SHELVES = 1_000
    private const val MAX_BOOKS = 100_000
    private const val MAX_NAME = 200
    private const val MAX_POSITION = 20_000

    fun encode(backup: LibraryBackup): String {
        val settings = JSONObject()
            .put("themeMode", backup.preferences.themeMode.name)
            .put("colourTheme", backup.preferences.colourTheme.name)
            .put("flatCarousel", backup.preferences.flatCarousel)
            .put("comicReadingDirection", backup.preferences.comicReadingDirection.name)
            .put("epubPageTheme", backup.preferences.epubPageTheme.name)
            .put("epubFontSize", backup.epubFontSize)
        val shelves = JSONArray()
        for (shelf in backup.shelves) {
            val books = JSONArray()
            for (book in shelf.books) {
                val entry = JSONObject()
                    .put("hash", book.hash)
                    .put("title", book.title)
                    .put("direction", book.direction.name)
                if (book.position != null && book.fraction != null) {
                    entry.put("position", book.position)
                    entry.put("fraction", book.fraction)
                    book.progressAt?.let { entry.put("progressAt", it) }
                }
                books.put(entry)
            }
            shelves.put(JSONObject().put("name", shelf.name).put("books", books))
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", backup.exportedAt)
            .put("settings", settings)
            .put("shelves", shelves)
            .toString(2)
    }

    /**
     * Null if [text] is not a backup, or is one from a newer version of the app that this
     * one cannot be sure of reading correctly. Anything unrecognised inside a backup falls
     * back to its default; a book listed twice is kept where it is listed first.
     */
    fun decode(text: String): LibraryBackup? = try {
        val root = JSONObject(text)
        val version = root.optInt("version", 0)
        if (root.optString("format") != FORMAT || version < 1 || version > VERSION) {
            null
        } else {
            val settings = root.optJSONObject("settings") ?: JSONObject()
            LibraryBackup(
                exportedAt = root.optLong("exportedAt", 0L),
                preferences = AppPreferences(
                    themeMode = enumOr(settings.optString("themeMode"), ThemeMode.SYSTEM),
                    colourTheme = enumOr(settings.optString("colourTheme"), ColourTheme.TSUNDOKU),
                    flatCarousel = settings.optBoolean("flatCarousel", false),
                    comicReadingDirection = when (settings.optString("comicReadingDirection")) {
                        ReadingDirection.LEFT_TO_RIGHT.name -> ReadingDirection.LEFT_TO_RIGHT
                        else -> ReadingDirection.RIGHT_TO_LEFT
                    },
                    epubPageTheme = enumOr(settings.optString("epubPageTheme"), PageTheme.SYSTEM),
                ),
                epubFontSize = settings.optDouble("epubFontSize", 1.0).takeIf { it.isFinite() } ?: 1.0,
                shelves = decodeShelves(root.optJSONArray("shelves")),
            )
        }
    } catch (e: JSONException) {
        null
    }

    private fun decodeShelves(array: JSONArray?): List<BackupShelf> {
        if (array == null) return emptyList()
        val shelves = LinkedHashMap<String, MutableList<BackupBook>>()
        val seen = HashSet<String>()
        for (i in 0 until minOf(array.length(), MAX_SHELVES)) {
            val shelf = array.optJSONObject(i) ?: continue
            val name = shelf.optString("name").trim().take(MAX_NAME)
            if (name.isEmpty()) continue
            // Two shelves of one name are one shelf.
            val books = shelves.getOrPut(name) { ArrayList() }
            val entries = shelf.optJSONArray("books") ?: continue
            for (j in 0 until entries.length()) {
                if (seen.size >= MAX_BOOKS) break
                val entry = entries.optJSONObject(j) ?: continue
                val hash = entry.optString("hash").trim()
                if (hash.isEmpty() || hash.length > MAX_NAME || !seen.add(hash)) continue
                val position = if (entry.has("position")) entry.optString("position").takeIf { it.length <= MAX_POSITION } else null
                val fraction = entry.optDouble("fraction", Double.NaN).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
                books += BackupBook(
                    hash = hash,
                    title = entry.optString("title").take(MAX_NAME),
                    direction = enumOr(entry.optString("direction"), ReadingDirection.AUTO),
                    position = position.takeIf { fraction != null },
                    fraction = fraction.takeIf { position != null },
                    progressAt = if (entry.has("progressAt")) entry.optLong("progressAt") else null,
                )
            }
        }
        return shelves.map { (name, books) -> BackupShelf(name, books) }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}

package io.github.manrajsingh1.tsundoku.core.model

enum class BookFormat {
    CBZ,
    EPUB,
    PDF,
}

/**
 * Which way a book's pages turn. [AUTO] means "nothing in the file said", and the reader
 * then follows the app-wide default for that format.
 */
enum class ReadingDirection {
    AUTO,
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
}

data class Book(
    val id: Long,
    /** Where the user's file lives. The app keeps a reference; it never copies the file. */
    val documentUri: String,
    /** SHA-256 of the file's bytes: the same book picked twice is recognised by this. */
    val contentHash: String,
    val format: BookFormat,
    val title: String,
    val author: String?,
    val series: String?,
    val volume: Double?,
    val pageCount: Int?,
    val fileSize: Long,
    /** A downsampled thumbnail in app storage, or null when the file has no usable cover. */
    val coverPath: String?,
    /** ARGB, already dark enough for white spine text to pass 4.5:1. */
    val spineColor: Int,
    val readingDirection: ReadingDirection,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    /** True when the file could not be found the last time the app looked. */
    val isMissing: Boolean,
)

data class Shelf(
    val id: Long,
    val name: String,
    val position: Int,
)

data class ShelfWithBooks(
    val shelf: Shelf,
    val books: List<Book>,
)

/** The shelves created from a book's format the first time one of that format is imported. */
enum class DefaultShelf {
    MANGA,
    NOVELS,
    DOCUMENTS,
    ;

    companion object {
        fun forFormat(format: BookFormat): DefaultShelf = when (format) {
            BookFormat.CBZ -> MANGA
            BookFormat.EPUB -> NOVELS
            BookFormat.PDF -> DOCUMENTS
        }
    }
}

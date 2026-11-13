package io.github.manrajsingh1.tsundoku.core.model

/** What became of one picked file. */
enum class ImportState {
    PENDING,
    IMPORTED,

    /** Its bytes match a book already in the library. */
    DUPLICATE,

    /** Not a CBZ, EPUB or PDF. */
    UNSUPPORTED,
    FAILED,
}

/** A running tally of the import queue, for the progress the library screen shows. */
data class ImportStatus(
    val pending: Int = 0,
    val imported: Int = 0,
    val duplicates: Int = 0,
    val unsupported: Int = 0,
    val failed: Int = 0,
) {
    val finished: Int get() = imported + duplicates + unsupported + failed
    val total: Int get() = pending + finished
    val isRunning: Boolean get() = pending > 0
    val isEmpty: Boolean get() = total == 0
}

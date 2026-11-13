package io.github.manrajsingh1.tsundoku.core.database

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.model.ImportState
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection

@Entity(
    tableName = "books",
    indices = [
        // The same file imported twice is one book.
        Index(value = ["contentHash"], unique = true),
        Index(value = ["documentUri"]),
    ],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentUri: String,
    val contentHash: String,
    val format: BookFormat,
    val title: String,
    val author: String?,
    val series: String?,
    val volume: Double?,
    val pageCount: Int?,
    val fileSize: Long,
    val coverPath: String?,
    val spineColor: Int,
    val readingDirection: ReadingDirection,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val isMissing: Boolean = false,
)

@Entity(tableName = "shelves")
data class ShelfEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
)

/**
 * Which shelf a book sits on, and where. A join table so that a book on several shelves is a
 * later change of one index, not of the schema's shape. In v1 the unique index on [bookId]
 * holds each book to one shelf.
 */
@Entity(
    tableName = "shelf_books",
    primaryKeys = ["shelfId", "bookId"],
    foreignKeys = [
        ForeignKey(
            entity = ShelfEntity::class,
            parentColumns = ["id"],
            childColumns = ["shelfId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["bookId"], unique = true)],
)
data class ShelfBookEntity(
    val shelfId: Long,
    val bookId: Long,
    val position: Int,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: Long,
    /** A page index for comics and PDFs, a serialised Readium locator for EPUBs. */
    val position: String,
    val percent: Double,
    val updatedAt: Long,
)

/**
 * One picked file or folder waiting to be imported, and afterwards what became of it. The
 * queue lives in the database so an import survives the app being closed part-way through.
 */
@Entity(tableName = "import_queue", indices = [Index(value = ["state"])])
data class ImportItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    /** True for a picked folder, which is expanded into one item per file inside it. */
    val isTree: Boolean,
    val state: ImportState = ImportState.PENDING,
    val displayName: String? = null,
    val message: String? = null,
    val createdAt: Long,
)

/** A book together with the shelf it is on and its place there. */
data class ShelvedBook(
    @Embedded val book: BookEntity,
    val shelfId: Long,
    val position: Int,
)

data class ImportStateCount(
    val state: ImportState,
    val count: Int,
)

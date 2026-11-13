package io.github.manrajsingh1.tsundoku.core.data.importing

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

class DocumentInfo(
    val displayName: String,
    val mimeType: String?,
)

/**
 * Reads the user's files where they are, through the system's document access. The app holds
 * no storage permission: it can read exactly the files and folders the user picked.
 */
class DocumentSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun describe(uri: Uri): DocumentInfo {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return DocumentInfo(displayName = File(uri.path.orEmpty()).name, mimeType = null)
        }
        var name: String? = null
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0)
            }
        } catch (e: Exception) {
            // A provider that will not answer is treated like one with no name to give.
        }
        return DocumentInfo(
            displayName = name ?: uri.lastPathSegment ?: uri.toString(),
            mimeType = runCatching { resolver.getType(uri) }.getOrNull(),
        )
    }

    /** Null when the file has gone, or access to it has been withdrawn. */
    fun open(uri: Uri): InputStream? = try {
        resolver.openInputStream(uri)
    } catch (e: Exception) {
        null
    }

    /**
     * Keeps read access across restarts. Android allows an app a limited number of these
     * grants (512 since Android 11), and a picked folder uses one for everything inside it,
     * which is why a large library should be added as a folder.
     */
    fun persistReadPermission(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Not every provider offers a lasting grant. The import can still run now.
        }
    }

    /**
     * Every book inside a picked folder, searching sub-folders too. Bounded in depth and in
     * number so an enormous or looping tree cannot stall the import.
     */
    fun listBooksInTree(tree: Uri): List<Uri> {
        val found = ArrayList<Uri>()
        val pending = ArrayDeque<Pair<String, Int>>()
        pending.addLast(DocumentsContract.getTreeDocumentId(tree) to 0)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        while (pending.isNotEmpty() && found.size < MAX_FILES) {
            val (documentId, depth) = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            try {
                resolver.query(children, columns, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext() && found.size < MAX_FILES) {
                        val id = cursor.getString(0) ?: continue
                        val mimeType = cursor.getString(1)
                        val name = cursor.getString(2).orEmpty().lowercase()
                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < MAX_DEPTH) pending.addLast(id to depth + 1)
                        } else if (bookExtensions.any { name.endsWith(it) }) {
                            found.add(DocumentsContract.buildDocumentUriUsingTree(tree, id))
                        }
                    }
                }
            } catch (e: Exception) {
                // One unreadable folder does not stop the rest of the tree being searched.
            }
        }
        return found
    }

    private companion object {
        const val MAX_FILES = 5_000
        const val MAX_DEPTH = 8
        val bookExtensions = listOf(".cbz", ".epub", ".pdf")
    }
}

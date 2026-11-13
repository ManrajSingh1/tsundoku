package io.github.manrajsingh1.tsundoku.core.data.importing

import androidx.core.net.toUri
import io.github.manrajsingh1.tsundoku.core.database.ImportItemEntity
import io.github.manrajsingh1.tsundoku.core.database.ImportQueueDao
import io.github.manrajsingh1.tsundoku.core.model.ImportState
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Works through the import queue one item at a time until nothing is pending. Each item's
 * outcome is written back to the queue as it finishes, so progress is visible while the
 * import runs and nothing is redone if the app is closed part-way through.
 */
class ImportRunner @Inject constructor(
    private val queue: ImportQueueDao,
    private val documents: DocumentSource,
    private val importer: BookImporter,
) {

    suspend fun drain() {
        while (true) {
            val item = queue.nextPending() ?: return
            if (item.isTree) {
                expandFolder(item)
            } else {
                importFile(item)
            }
        }
    }

    /** Replaces a picked folder with one queue item for each book found inside it. */
    private suspend fun expandFolder(item: ImportItemEntity) {
        val books = try {
            documents.listBooksInTree(item.uri.toUri())
        } catch (e: Exception) {
            emptyList()
        }
        if (books.isNotEmpty()) {
            queue.insertAll(
                books.map { ImportItemEntity(uri = it.toString(), isTree = false, createdAt = item.createdAt) },
            )
        }
        queue.delete(item.id)
    }

    private suspend fun importFile(item: ImportItemEntity) {
        val outcome = try {
            importer.import(item.uri.toUri())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // One bad file must not stop the rest of the batch.
            ImportOutcome(ImportState.FAILED, displayName = null, message = e.message ?: e.javaClass.simpleName)
        }
        queue.finish(item.id, outcome.state, outcome.displayName, outcome.message)
    }
}

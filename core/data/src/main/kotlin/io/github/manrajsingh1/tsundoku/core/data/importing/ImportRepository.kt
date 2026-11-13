package io.github.manrajsingh1.tsundoku.core.data.importing

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.manrajsingh1.tsundoku.core.database.ImportItemEntity
import io.github.manrajsingh1.tsundoku.core.database.ImportQueueDao
import io.github.manrajsingh1.tsundoku.core.model.ImportState
import io.github.manrajsingh1.tsundoku.core.model.ImportStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Adds picked files and folders to the library, in the background. */
interface ImportRepository {

    suspend fun importDocuments(uris: List<Uri>)

    /** Imports every book in a picked folder and the folders inside it. */
    suspend fun importFolder(tree: Uri)

    /** The tally of the current import, or of the last one if none is running. */
    fun observeStatus(): Flow<ImportStatus>

    suspend fun dismissFinished()
}

class OfflineImportRepository @Inject constructor(
    private val queue: ImportQueueDao,
    private val documents: DocumentSource,
    private val scheduler: ImportScheduler,
) : ImportRepository {

    override suspend fun importDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach(documents::persistReadPermission)
        enqueue(uris, isTree = false)
    }

    override suspend fun importFolder(tree: Uri) {
        documents.persistReadPermission(tree)
        enqueue(listOf(tree), isTree = true)
    }

    private suspend fun enqueue(uris: List<Uri>, isTree: Boolean) {
        // A new batch starts a new tally, unless it joins an import that is still running.
        if (queue.nextPending() == null) queue.clearFinished()
        val now = System.currentTimeMillis()
        queue.insertAll(uris.map { ImportItemEntity(uri = it.toString(), isTree = isTree, createdAt = now) })
        scheduler.schedule()
    }

    override fun observeStatus(): Flow<ImportStatus> = queue.observeCounts().map { counts ->
        val byState = counts.associate { it.state to it.count }
        ImportStatus(
            pending = byState[ImportState.PENDING] ?: 0,
            imported = byState[ImportState.IMPORTED] ?: 0,
            duplicates = byState[ImportState.DUPLICATE] ?: 0,
            unsupported = byState[ImportState.UNSUPPORTED] ?: 0,
            failed = byState[ImportState.FAILED] ?: 0,
        )
    }

    override suspend fun dismissFinished() = queue.clearFinished()
}

/** Starts the background work that drains the import queue. */
interface ImportScheduler {
    fun schedule()
}

class WorkManagerImportScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImportScheduler {

    override fun schedule() {
        // One import at a time. Work asked for while one is running is run after it, and
        // finds whatever the first run left in the queue.
        WorkManager.getInstance(context).enqueueUniqueWork(
            ImportWorker.UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ImportWorker>().build(),
        )
    }
}

/**
 * Runs the import off the main thread and beyond the life of the screen that started it.
 * Its collaborators come from Hilt through an entry point, which keeps WorkManager's own
 * default set-up untouched.
 */
class ImportWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun importRunner(): ImportRunner
    }

    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
            .importRunner()
            .drain()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "import"
    }
}

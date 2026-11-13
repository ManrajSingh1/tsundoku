package io.github.manrajsingh1.tsundoku.feature.importing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.manrajsingh1.tsundoku.core.model.ImportStatus

/** The ways into the library, each opening the system's own picker. */
class ImportPickers(
    /** Picked files are shelved by their format. */
    val addFiles: () -> Unit,
    /** Picked files go on the given shelf, whatever their format. */
    val addFilesToShelf: (shelfId: Long) -> Unit,
    val addFolder: () -> Unit,
)

/**
 * Opens the system file picker, so the app never asks for a storage permission: it is
 * handed access to exactly what the user picks and nothing else.
 */
@Composable
fun rememberImportPickers(
    onDocumentsPicked: (uris: List<Uri>, shelfId: Long?) -> Unit,
    onFolderPicked: (Uri) -> Unit,
): ImportPickers {
    // The shelf the picker was opened for. Saved, because the app can be stopped and
    // restarted while the system picker is in front of it.
    var targetShelfId by rememberSaveable { mutableStateOf<Long?>(null) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onDocumentsPicked(uris, targetShelfId)
        targetShelfId = null
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) onFolderPicked(tree)
    }
    return remember(files, folder) {
        ImportPickers(
            addFiles = {
                targetShelfId = null
                files.launch(BOOK_MIME_TYPES)
            },
            addFilesToShelf = { shelfId ->
                targetShelfId = shelfId
                files.launch(BOOK_MIME_TYPES)
            },
            addFolder = { folder.launch(null) },
        )
    }
}

// File managers disagree about what a .cbz is: some call it a zip, some a generic binary.
// The picker has to offer all of those; the importer then checks each file's actual bytes.
private val BOOK_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/epub+zip",
    "application/vnd.comicbook+zip",
    "application/x-cbz",
    "application/zip",
    "application/octet-stream",
)

/** Progress while an import runs, then a one-line account of what happened. */
@Composable
fun ImportStatusBar(
    status: ImportStatus,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status.isEmpty) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // Announced by TalkBack as it changes, without stealing focus.
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (status.isRunning) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(
                        R.string.feature_import_running,
                        (status.finished + 1).coerceAtMost(status.total),
                        status.total,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { status.finished / status.total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary(status),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_import_dismiss)) }
            }
        }
    }
}

@Composable
private fun summary(status: ImportStatus): String {
    val parts = ArrayList<String>(4)
    parts.add(
        if (status.imported > 0) {
            pluralStringResource(R.plurals.feature_import_added, status.imported, status.imported)
        } else {
            stringResource(R.string.feature_import_nothing_added)
        },
    )
    if (status.duplicates > 0) {
        parts.add(pluralStringResource(R.plurals.feature_import_duplicates, status.duplicates, status.duplicates))
    }
    if (status.unsupported > 0) {
        parts.add(pluralStringResource(R.plurals.feature_import_unsupported, status.unsupported, status.unsupported))
    }
    if (status.failed > 0) {
        parts.add(pluralStringResource(R.plurals.feature_import_failed, status.failed, status.failed))
    }
    return parts.joinToString(". ") + "."
}

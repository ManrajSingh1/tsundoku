package io.github.manrajsingh1.tsundoku.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The library and the open shelf, laid out for whatever window the app has been given: one
 * at a time on a phone, side by side on a tablet or an unfolded foldable. The window can
 * change size at any moment (rotation, split screen, a fold opening), and nothing here is
 * lost when it does.
 *
 * @param hasVerticalFold true when the window is split left and right by a hinge.
 * @param progress how far through each started book the reader is, from 0 to 1.
 * @param onReadBook opens a book in its reader, from the start if `restart` is true.
 * @param importStatus drawn above the shelves. A slot, so this feature does not depend on
 * the import feature.
 */
@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    hasVerticalFold: Boolean,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onAddFilesToShelf: (shelfId: Long) -> Unit,
    modifier: Modifier = Modifier,
    progress: Map<Long, Double> = emptyMap(),
    onReadBook: (bookId: Long, restart: Boolean) -> Unit = { _, _ -> },
    importStatus: @Composable () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var selectedBookId by rememberSaveable { mutableStateOf<Long?>(null) }
    var openBookId by rememberSaveable { mutableStateOf<Long?>(null) }
    val newShelfName = stringResource(R.string.feature_library_new_shelf)

    val actions = remember(viewModel, onAddFiles, onAddFolder, onAddFilesToShelf, newShelfName) {
        LibraryActions(
            onAddFiles = onAddFiles,
            onAddFolder = onAddFolder,
            onAddFilesToShelf = onAddFilesToShelf,
            onOpenBook = { openBookId = it },
            onSetEditing = { on ->
                editing = on
                selectedBookId = null
            },
            onSelectBook = { selectedBookId = it },
            onMoveBook = viewModel::moveBook,
            onMoveBookToNextShelf = viewModel::moveBookToNextShelf,
            onRemoveBook = viewModel::removeBook,
            onAddShelf = { viewModel.addShelf(newShelfName) },
            onRenameShelf = viewModel::renameShelf,
            onDeleteShelf = viewModel::deleteShelf,
            onSortShelf = viewModel::sortShelf,
            onRestoreShelfOrder = viewModel::restoreShelfOrder,
        )
    }

    val shelves = (uiState as? LibraryUiState.Loaded)?.shelves.orEmpty()
    // Looked up afresh each time: if the open book is removed, the pane simply closes.
    val open = remember(shelves, openBookId) { locateBook(shelves, openBookId) }
    val stateHolder = rememberSaveableStateHolder()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val panes = paneLayoutFor(maxWidth.value, maxHeight.value, hasVerticalFold)
        val showLibrary = panes.twoPane || open == null
        val showShelf = panes.twoPane || open != null

        // Back closes the open shelf when it covers the library, and leaves edit mode.
        BackHandler(enabled = !panes.twoPane && open != null) { openBookId = null }
        BackHandler(enabled = editing && showLibrary) { actions.onSetEditing(false) }

        Row(Modifier.fillMaxSize()) {
            if (showLibrary) {
                Box(Modifier.weight(if (panes.twoPane) panes.listFraction else 1f).fillMaxHeight()) {
                    // Scroll positions are kept while the open shelf covers the library.
                    stateHolder.SaveableStateProvider("library") {
                        LibraryScreen(
                            uiState = uiState,
                            editing = editing,
                            selectedBookId = selectedBookId,
                            actions = actions,
                            messages = viewModel.messages,
                            importStatus = importStatus,
                        )
                    }
                }
            }
            if (showShelf) {
                Box(Modifier.weight(if (panes.twoPane) 1f - panes.listFraction else 1f).fillMaxHeight()) {
                    OpenShelfPane(
                        place = open,
                        onShowBook = { openBookId = it },
                        onBack = if (panes.twoPane) null else ({ openBookId = null }),
                        progress = progress,
                        onReadBook = { book, restart -> onReadBook(book.id, restart) },
                    )
                }
            }
        }
    }
}

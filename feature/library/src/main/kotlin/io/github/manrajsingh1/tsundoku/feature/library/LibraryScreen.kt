package io.github.manrajsingh1.tsundoku.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.Shelf
import io.github.manrajsingh1.tsundoku.core.model.ShelfWithBooks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/** Everything the library screen can ask for. One object, so it is passed down as one. */
@Stable
class LibraryActions(
    val onAddFiles: () -> Unit,
    val onAddFolder: () -> Unit,
    val onAddFilesToShelf: (shelfId: Long) -> Unit,
    val onOpenBook: (bookId: Long) -> Unit,
    val onSetEditing: (Boolean) -> Unit,
    /** The book picked out in edit mode, or null for none. */
    val onSelectBook: (bookId: Long?) -> Unit,
    val onMoveBook: (bookId: Long, delta: Int) -> Unit,
    val onMoveBookToNextShelf: (bookId: Long) -> Unit,
    val onRemoveBook: (Book) -> Unit,
    val onAddShelf: () -> Unit,
    val onRenameShelf: (shelfId: Long, name: String) -> Unit,
    val onDeleteShelf: (Shelf) -> Unit,
    /** Puts a shelf's books in A to Z order. */
    val onSortShelf: (Shelf) -> Unit = {},
    /** Puts a shelf back in an earlier order of book ids: the undo for a sort. */
    val onRestoreShelfOrder: (shelfId: Long, bookIds: List<Long>) -> Unit = { _, _ -> },
    val onOpenSettings: () -> Unit = {},
)

/**
 * The library: every shelf as a row of spines standing on a plank. In edit mode a tapped
 * spine is selected and the bar at the bottom moves or removes it.
 *
 * Following One UI, the top of the screen is for looking and the bottom for doing: the
 * title and shelves sit above, and Add, Done and the edit actions sit within thumb reach.
 *
 * @param importStatus drawn above the shelves. A slot, so this feature does not depend on
 * the import feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    editing: Boolean,
    selectedBookId: Long?,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
    messages: Flow<LibraryMessage> = emptyFlow(),
    importStatus: @Composable () -> Unit = {},
) {
    val shelves = (uiState as? LibraryUiState.Loaded)?.shelves.orEmpty()
    val selected = remember(shelves, selectedBookId) { locateBook(shelves, selectedBookId) }
    val showEmptyState = uiState is LibraryUiState.Loaded && uiState.isEmpty && !editing

    // Asked about before they happen: neither can be undone from inside the app.
    var removalAskedFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var shelfDeletionAskedFor by rememberSaveable { mutableStateOf<Long?>(null) }

    val snackbars = remember { SnackbarHostState() }
    LibraryMessages(messages, snackbars, actions.onRestoreShelfOrder)

    val focusManager = LocalFocusManager.current
    val finishEditing = {
        // Leaving a name field commits it, so a rename in progress is not lost.
        focusManager.clearFocus()
        actions.onSetEditing(false)
    }

    Scaffold(
        // The screen shrinks above the keyboard, so a shelf name being typed stays in view.
        modifier = modifier.imePadding(),
        topBar = {
            // One line, always. The tall One UI header was tried first and gave a fifth of
            // the screen to the app's name; the shelves are what the screen is for.
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (editing) R.string.feature_library_edit_title else R.string.feature_library_title,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (!editing) {
                        LibraryMenu(
                            canEdit = shelves.isNotEmpty(),
                            onEdit = { actions.onSetEditing(true) },
                            onAddFiles = actions.onAddFiles,
                            onAddFolder = actions.onAddFolder,
                            onOpenSettings = actions.onOpenSettings,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (editing) {
                EditBar(
                    selected = selected,
                    shelfCount = shelves.size,
                    onMove = { delta -> selected?.let { actions.onMoveBook(it.book.id, delta) } },
                    onNextShelf = { selected?.let { actions.onMoveBookToNextShelf(it.book.id) } },
                    onRemove = { removalAskedFor = selected?.book?.id },
                    onDone = finishEditing,
                )
            }
        },
        floatingActionButton = {
            if (!editing && !showEmptyState && uiState is LibraryUiState.Loaded) {
                ExtendedFloatingActionButton(
                    onClick = actions.onAddFiles,
                    // Material hides an extended button's text from screen readers and reads
                    // the icon instead, so the icon carries the same words.
                    icon = {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.feature_library_add_books))
                    },
                    text = { Text(stringResource(R.string.feature_library_add_books)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        when {
            uiState !is LibraryUiState.Loaded -> Box(Modifier.fillMaxSize().padding(padding))
            showEmptyState -> EmptyLibrary(
                onAddFiles = actions.onAddFiles,
                onAddFolder = actions.onAddFolder,
                importStatus = importStatus,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> ShelfList(
                shelves = shelves,
                editing = editing,
                selected = selected,
                actions = actions,
                onAskRemoveBook = { removalAskedFor = it.id },
                onAskDeleteShelf = { shelfDeletionAskedFor = it.id },
                importStatus = importStatus,
                padding = padding,
            )
        }
    }

    locateBook(shelves, removalAskedFor)?.book?.let { book ->
        AlertDialog(
            onDismissRequest = { removalAskedFor = null },
            title = { Text(stringResource(R.string.feature_library_remove_title)) },
            text = { Text(stringResource(R.string.feature_library_remove_body, book.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removalAskedFor = null
                        actions.onRemoveBook(book)
                    },
                ) { Text(stringResource(R.string.feature_library_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removalAskedFor = null }) {
                    Text(stringResource(R.string.feature_library_cancel))
                }
            },
        )
    }

    shelves.firstOrNull { it.shelf.id == shelfDeletionAskedFor }?.let { entry ->
        val movesTo = shelves.firstOrNull { it.shelf.id != entry.shelf.id }?.shelf?.name.orEmpty()
        val count = entry.books.size
        AlertDialog(
            onDismissRequest = { shelfDeletionAskedFor = null },
            title = { Text(stringResource(R.string.feature_library_delete_shelf_title, entry.shelf.name)) },
            text = {
                Text(
                    if (count == 0) {
                        stringResource(R.string.feature_library_delete_shelf_body_empty)
                    } else {
                        pluralStringResource(R.plurals.feature_library_delete_shelf_body, count, count, movesTo)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        shelfDeletionAskedFor = null
                        actions.onDeleteShelf(entry.shelf)
                    },
                ) { Text(stringResource(R.string.feature_library_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { shelfDeletionAskedFor = null }) {
                    Text(stringResource(R.string.feature_library_cancel))
                }
            },
        )
    }
}

/**
 * Tells the user what just happened. Removals are shown; moves are only spoken, because
 * anyone looking at the screen has just watched the book move.
 */
@Composable
private fun LibraryMessages(
    messages: Flow<LibraryMessage>,
    snackbars: SnackbarHostState,
    onRestoreShelfOrder: (shelfId: Long, bookIds: List<Long>) -> Unit,
) {
    val resources = LocalResources.current
    val view = LocalView.current
    LaunchedEffect(messages, snackbars, resources) {
        messages.collect { message ->
            when (message) {
                is LibraryMessage.BookMoved -> view.announceForAccessibility(
                    resources.getString(R.string.feature_library_announce_moved, message.position, message.count),
                )
                is LibraryMessage.BookMovedToShelf -> view.announceForAccessibility(
                    resources.getString(R.string.feature_library_announce_moved_shelf, message.shelfName),
                )
                is LibraryMessage.BookRemoved -> launch {
                    snackbars.showSnackbar(resources.getString(R.string.feature_library_removed, message.title))
                }
                // A sort throws away an order that may have been arranged by hand, so it
                // can be taken back for as long as the message is up.
                is LibraryMessage.ShelfSorted -> launch {
                    val result = snackbars.showSnackbar(
                        message = resources.getString(R.string.feature_library_shelf_sorted, message.name),
                        actionLabel = resources.getString(R.string.feature_library_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onRestoreShelfOrder(message.shelfId, message.previousOrder)
                    }
                }
                is LibraryMessage.ShelfDeleted -> launch {
                    snackbars.showSnackbar(
                        if (message.bookCount == 0) {
                            resources.getString(R.string.feature_library_shelf_deleted_empty, message.name)
                        } else {
                            resources.getQuantityString(
                                R.plurals.feature_library_shelf_deleted,
                                message.bookCount,
                                message.name,
                                message.bookCount,
                                message.movedTo,
                            )
                        },
                    )
                }
            }
        }
    }
}

/** The More button at the far right of the app bar, where One UI puts it. */
@Composable
private fun LibraryMenu(
    canEdit: Boolean,
    onEdit: () -> Unit,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.feature_library_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (canEdit) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_library_edit_shelves)) },
                    onClick = {
                        open = false
                        onEdit()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_library_add_files)) },
                onClick = {
                    open = false
                    onAddFiles()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_library_add_folder)) },
                onClick = {
                    open = false
                    onAddFolder()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_library_settings)) },
                onClick = {
                    open = false
                    onOpenSettings()
                },
            )
        }
    }
}

@Composable
private fun EmptyLibrary(
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    importStatus: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        importStatus()
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.feature_library_empty_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.feature_library_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAddFolder) { Text(stringResource(R.string.feature_library_add_folder)) }
                OutlinedButton(onClick = onAddFiles) { Text(stringResource(R.string.feature_library_add_files)) }
            }
        }
    }
}

@Composable
private fun ShelfList(
    shelves: List<ShelfWithBooks>,
    editing: Boolean,
    selected: BookPlace?,
    actions: LibraryActions,
    onAskRemoveBook: (Book) -> Unit,
    onAskDeleteShelf: (Shelf) -> Unit,
    importStatus: @Composable () -> Unit,
    padding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val layoutDirection = LocalLayoutDirection.current

    // A book sent to the next shelf may land off screen; follow it there.
    val selectedShelfId = selected?.shelf?.shelf?.id
    val selectedShelfIndex = shelves.indexOfFirst { it.shelf.id == selectedShelfId }
    LaunchedEffect(selectedShelfIndex) {
        if (selectedShelfIndex >= 0) {
            val item = selectedShelfIndex + SHELVES_START_AT
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == item }) listState.animateScrollToItem(item)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(SHELVES_TEST_TAG),
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(layoutDirection),
            // Room for the last shelf to scroll clear of the Add button floating over it.
            bottom = padding.calculateBottomPadding() + if (editing) 0.dp else 96.dp,
        ),
    ) {
        item(key = "import-status", contentType = "status") { importStatus() }
        for (entry in shelves) {
            item(key = "shelf-${entry.shelf.id}", contentType = "shelf") {
                ShelfSection(
                    entry = entry,
                    editing = editing,
                    selectedBookId = selected?.book?.id,
                    canDelete = shelves.size > 1,
                    hasOtherShelves = shelves.size > 1,
                    textMeasurer = textMeasurer,
                    actions = actions,
                    onAskRemoveBook = onAskRemoveBook,
                    onAskDeleteShelf = { onAskDeleteShelf(entry.shelf) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (editing) {
            item(key = "add-shelf", contentType = "add-shelf") {
                AddShelfButton(onClick = actions.onAddShelf, modifier = Modifier.animateItem())
            }
        }
    }
}

/** Finds the list of shelves in UI tests. */
internal const val SHELVES_TEST_TAG = "shelves"

/** The shelves come after the import status in the list. */
private const val SHELVES_START_AT = 1

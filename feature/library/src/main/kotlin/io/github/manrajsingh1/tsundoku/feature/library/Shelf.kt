package io.github.manrajsingh1.tsundoku.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.ShelfWithBooks
import io.github.manrajsingh1.tsundoku.core.ui.theme.bookcase

/**
 * One shelf: its name and count, a strip of spines that scrolls sideways when there are
 * more than fit, and the plank they stand on.
 *
 * @param canDelete false for the last shelf left, which has to stay.
 */
@Composable
internal fun ShelfSection(
    entry: ShelfWithBooks,
    editing: Boolean,
    selectedBookId: Long?,
    canDelete: Boolean,
    hasOtherShelves: Boolean,
    textMeasurer: TextMeasurer,
    actions: LibraryActions,
    onAskRemoveBook: (Book) -> Unit,
    onAskDeleteShelf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (editing) {
            ShelfEditHeader(
                name = entry.shelf.name,
                shelfId = entry.shelf.id,
                canDelete = canDelete,
                onRename = { actions.onRenameShelf(entry.shelf.id, it) },
                onDelete = onAskDeleteShelf,
            )
        } else {
            ShelfHeader(name = entry.shelf.name, count = entry.books.size)
        }
        ShelfStrip(
            entry = entry,
            editing = editing,
            selectedBookId = selectedBookId,
            hasOtherShelves = hasOtherShelves,
            textMeasurer = textMeasurer,
            actions = actions,
            onAskRemoveBook = onAskRemoveBook,
        )
        Plank()
    }
}

@Composable
private fun ShelfHeader(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
            // Read as one heading: "Manga, 3 books".
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = pluralStringResource(R.plurals.feature_library_shelf_count, count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShelfEditHeader(
    name: String,
    shelfId: Long,
    canDelete: Boolean,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    // What is being typed. It becomes the shelf's name when the keyboard's Done is pressed
    // or the field is left; an empty field goes back to the name the shelf had.
    var text by rememberSaveable(shelfId, name) { mutableStateOf(name) }
    var hadFocus by rememberSaveable(shelfId) { mutableStateOf(false) }
    val commit = {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            text = name
        } else if (trimmed != name) {
            onRename(trimmed)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(MAX_SHELF_NAME_LENGTH) },
            label = { Text(stringResource(R.string.feature_library_shelf_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { state ->
                    if (hadFocus && !state.isFocused) commit()
                    hadFocus = state.isFocused
                },
        )
        IconButton(onClick = onDelete, enabled = canDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.feature_library_delete_shelf, name),
            )
        }
    }
}

private const val MAX_SHELF_NAME_LENGTH = 60

@Composable
private fun ShelfStrip(
    entry: ShelfWithBooks,
    editing: Boolean,
    selectedBookId: Long?,
    hasOtherShelves: Boolean,
    textMeasurer: TextMeasurer,
    actions: LibraryActions,
    onAskRemoveBook: (Book) -> Unit,
) {
    val scale = spineScale()
    val books = entry.books
    val listState = rememberLazyListState()
    val selectedIndex = if (selectedBookId == null) -1 else books.indexOfFirst { it.id == selectedBookId }

    // Keep the selected book in view as it is moved along, or arrives from another shelf.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            val layout = listState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
            val inView = item != null &&
                item.offset >= 0 &&
                item.offset + item.size <= layout.viewportEndOffset - layout.afterContentPadding
            if (!inView) listState.animateScrollToItem(selectedIndex)
        }
    }

    val openLabel = stringResource(R.string.feature_library_action_open)
    val selectLabel = stringResource(R.string.feature_library_action_select)
    val deselectLabel = stringResource(R.string.feature_library_action_deselect)
    val moveLeftLabel = stringResource(R.string.feature_library_move_left)
    val moveRightLabel = stringResource(R.string.feature_library_move_right)
    val nextShelfLabel = stringResource(R.string.feature_library_a11y_next_shelf)
    val removeLabel = stringResource(R.string.feature_library_a11y_remove)

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.bookcase.shelfBack)
            .height((SpineMetrics.MAX_HEIGHT * scale).dp + SpineLift + 12.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        itemsIndexed(books, key = { _, book -> book.id }, contentType = { _, _ -> "spine" }) { index, book ->
            val selected = book.id == selectedBookId
            // The same things the edit bar does, offered on the spine itself, so a screen
            // reader user can rearrange a shelf without leaving the book they are on.
            val accessibilityActions = buildList {
                if (index > 0) {
                    add(CustomAccessibilityAction(moveLeftLabel) { actions.onMoveBook(book.id, -1); true })
                }
                if (index < books.lastIndex) {
                    add(CustomAccessibilityAction(moveRightLabel) { actions.onMoveBook(book.id, 1); true })
                }
                if (hasOtherShelves) {
                    add(CustomAccessibilityAction(nextShelfLabel) { actions.onMoveBookToNextShelf(book.id); true })
                }
                add(CustomAccessibilityAction(removeLabel) { onAskRemoveBook(book); true })
            }
            Spine(
                book = book,
                label = spineLabel(book),
                clickLabel = when {
                    !editing -> openLabel
                    selected -> deselectLabel
                    else -> selectLabel
                },
                selected = if (editing) selected else null,
                accessibilityActions = accessibilityActions,
                textMeasurer = textMeasurer,
                onClick = {
                    when {
                        !editing -> actions.onOpenBook(book.id)
                        selected -> actions.onSelectBook(null)
                        else -> actions.onSelectBook(book.id)
                    }
                },
                modifier = Modifier.animateItem(),
            )
        }
        if (editing) {
            item(key = "add-slot", contentType = "slot") {
                AddSlot(
                    label = stringResource(R.string.feature_library_add_to_shelf, entry.shelf.name),
                    height = (SpineMetrics.MIN_HEIGHT * scale).dp,
                    onClick = { actions.onAddFilesToShelf(entry.shelf.id) },
                    modifier = Modifier.animateItem().padding(start = 10.dp),
                )
            }
        } else if (books.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                Text(
                    text = stringResource(R.string.feature_library_empty_shelf),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

/** A dashed, book-sized gap at the end of a shelf: tap it to add files to this shelf. */
@Composable
private fun AddSlot(label: String, height: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(width = 48.dp, height = height)
            .clip(RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .dashedOutline(outline),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A dashed, full-width button under the last shelf. */
@Composable
internal fun AddShelfButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .dashedOutline(outline, cornerRadius = 12.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.feature_library_add_shelf),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun Modifier.dashedOutline(color: Color, cornerRadius: Dp = 4.dp): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    val dash = 6.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

/** The board the books stand on, with a soft shadow beneath it. */
@Composable
private fun Plank() {
    val colors = MaterialTheme.bookcase
    Box(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .drawBehind {
                val top = 6.dp.toPx()
                val front = 8.dp.toPx()
                drawRect(color = colors.plankTop, size = Size(size.width, top))
                drawRect(color = colors.plankFront, topLeft = Offset(0f, top), size = Size(size.width, front))
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(colors.plankShadow, Color.Transparent),
                        startY = top + front,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, top + front),
                    size = Size(size.width, size.height - top - front),
                )
            },
    )
}

/** Everything a screen reader says for a book: "The Third Volume, volume 3, CBZ". */
@Composable
internal fun spineLabel(book: Book): String {
    val parts = ArrayList<String>(4)
    parts.add(book.title)
    book.volume?.let { parts.add(stringResource(R.string.feature_library_volume_spoken, volumeNumber(it))) }
    parts.add(book.format.name)
    if (book.isMissing) parts.add(stringResource(R.string.feature_library_missing))
    return parts.joinToString(", ")
}

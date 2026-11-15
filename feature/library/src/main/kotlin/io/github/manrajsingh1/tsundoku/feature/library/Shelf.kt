package io.github.manrajsingh1.tsundoku.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
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
import io.github.manrajsingh1.tsundoku.core.model.ShelfOrder
import io.github.manrajsingh1.tsundoku.core.model.ShelfWithBooks
import io.github.manrajsingh1.tsundoku.core.ui.theme.bookcase
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest

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
                canSort = remember(entry.books) { !ShelfOrder.isAlphabetical(entry.books) },
                onSort = { actions.onSortShelf(entry.shelf) },
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
    canSort: Boolean,
    onSort: () -> Unit,
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
        // Greyed out once the shelf is in order, which is also how to tell that it is.
        val sortLabel = stringResource(R.string.feature_library_sort_shelf, name)
        TextButton(
            onClick = onSort,
            enabled = canSort,
            modifier = Modifier.padding(start = 4.dp).semantics { contentDescription = sortLabel },
        ) {
            Text(stringResource(R.string.feature_library_sort_shelf_short))
        }
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

    // Outside edit mode a finger can run along the spines: each hops up as the finger reaches
    // it, and the one under the finger when it lifts off is opened. None of this state is
    // read here, only in the gesture and in each spine, so the strip does not recompose.
    val liftedId = remember { mutableStateOf<Long?>(null) }
    val edgeSpeed = remember { mutableFloatStateOf(0f) }
    val fingerX = remember { FloatArray(1) }
    val currentBooks by rememberUpdatedState(books)
    val openBook by rememberUpdatedState(actions.onOpenBook)
    val haptics = LocalHapticFeedback.current
    val reach = with(LocalDensity.current) { RiffleReach.toPx() }
    val liftSpine: (index: Int?, tick: Boolean) -> Unit = remember(haptics) {
        { index, tick ->
            val id = index?.let { currentBooks.getOrNull(it)?.id }
            if (id != liftedId.value) {
                liftedId.value = id
                if (tick && id != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    // Holding the finger near either end slides a long shelf along underneath it.
    LaunchedEffect(listState) {
        snapshotFlow { edgeSpeed.floatValue }.collectLatest { speed ->
            if (speed == 0f) return@collectLatest
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                listState.scrollBy(speed * (now - last) / 1_000_000_000f)
                last = now
                liftSpine(listState.spineAt(fingerX[0], currentBooks.size, reach), true)
            }
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
            .height((SpineMetrics.MAX_HEIGHT * scale).dp + SpineLift + 8.dp)
            .then(
                if (editing) {
                    Modifier
                } else {
                    Modifier.riffle(
                        listState = listState,
                        bookCount = books.size,
                        reach = reach,
                        onLift = liftSpine,
                        onEdge = { speed, x ->
                            fingerX[0] = x
                            edgeSpeed.floatValue = speed
                        },
                        onOpen = { index -> currentBooks.getOrNull(index)?.let { openBook(it.id) } },
                    )
                },
            ),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        itemsIndexed(books, key = { _, book -> book.id }, contentType = { _, _ -> "spine" }) { index, book ->
            val selected = book.id == selectedBookId
            // The same things the edit bar does, offered on the spine itself, so a screen
            // reader user can rearrange a shelf without leaving the book they are on.
            val accessibilityActions = buildList {
                if (index > 0) add(accessibilityAction(moveLeftLabel) { actions.onMoveBook(book.id, -1) })
                if (index < books.lastIndex) add(accessibilityAction(moveRightLabel) { actions.onMoveBook(book.id, 1) })
                if (hasOtherShelves) add(accessibilityAction(nextShelfLabel) { actions.onMoveBookToNextShelf(book.id) })
                add(accessibilityAction(removeLabel) { onAskRemoveBook(book) })
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
                lifted = { liftedId.value == book.id },
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

/** How far from a spine a sliding finger may be and still count as on it. */
private val RiffleReach = 48.dp

/**
 * The book nearest to [x] (in the strip's own pixels) among those on screen, or null if
 * none is within [reach].
 */
private fun LazyListState.spineAt(x: Float, bookCount: Int, reach: Float): Int? {
    val info = layoutInfo
    // Item offsets are measured from where the content starts, after the padding.
    val position = x - info.beforeContentPadding
    var nearest: Int? = null
    var nearestDistance = Float.MAX_VALUE
    for (item in info.visibleItemsInfo) {
        if (item.index >= bookCount) continue
        val distance = when {
            position < item.offset -> item.offset - position
            position > item.offset + item.size -> position - (item.offset + item.size)
            else -> 0f
        }
        if (distance < nearestDistance) {
            nearest = item.index
            nearestDistance = distance
        }
    }
    return if (nearestDistance <= reach) nearest else null
}

/**
 * Running a finger along the shelf. A touch lifts the book under it; once the finger has
 * moved sideways the lift follows it from book to book, and the book it is on when it
 * leaves the glass is opened. A finger that drifts well above or below the shelf lets the
 * book drop and opens nothing. Moving up or down first is left alone: that scrolls the page.
 *
 * Taps are not handled here. Each spine is its own button, which is what a screen reader
 * and a keyboard use.
 *
 * The gesture is watched on the way down to the spines, ahead of the row's own scrolling,
 * so the row still scrolls for a screen reader but not under a sliding finger. A long shelf
 * is reached by holding the finger near either end.
 *
 * @param onLift the book to lift (an index, or null for none) and whether to tick.
 * @param onEdge how fast the shelf should slide, in pixels a second, and where the finger is.
 */
private fun Modifier.riffle(
    listState: LazyListState,
    bookCount: Int,
    reach: Float,
    onLift: (index: Int?, tick: Boolean) -> Unit,
    onEdge: (speed: Float, x: Float) -> Unit,
    onOpen: (index: Int) -> Unit,
): Modifier = pointerInput(listState, bookCount, reach) {
    val slop = viewConfiguration.touchSlop
    val edgeZone = 56.dp.toPx()
    val maxSpeed = 900.dp.toPx()
    val letGo = 40.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onLift(listState.spineAt(down.position.x, bookCount, 0f), false)
        var riffling = false
        var lifted: Int? = null
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (!riffling) {
                    if (abs(dy) > slop && abs(dy) >= abs(dx)) {
                        lifted = null
                        break
                    }
                    if (abs(dx) > slop) riffling = true
                }
                if (riffling) {
                    change.consume()
                    val x = change.position.x
                    val y = change.position.y
                    val onShelf = y > -letGo && y < size.height + letGo
                    lifted = if (onShelf) listState.spineAt(x, bookCount, reach) else null
                    onLift(lifted, true)
                    val speed = when {
                        !onShelf -> 0f
                        x < edgeZone -> -maxSpeed * ((edgeZone - x) / edgeZone).coerceIn(0f, 1f)
                        x > size.width - edgeZone -> maxSpeed * ((x - (size.width - edgeZone)) / edgeZone).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    onEdge(speed, x)
                }
            }
        } finally {
            onEdge(0f, 0f)
        }
        if (riffling) lifted?.let(onOpen)
        onLift(null, false)
    }
}

/** A screen reader action that always reports it was carried out. */
private fun accessibilityAction(label: String, action: () -> Unit) = CustomAccessibilityAction(label) {
    action()
    true
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
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

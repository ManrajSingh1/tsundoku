package io.github.manrajsingh1.tsundoku.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.ReadingProgress
import io.github.manrajsingh1.tsundoku.core.model.ReadingState
import kotlin.math.abs

/**
 * The open shelf: its books as a carousel of covers, the tapped book facing front, and a
 * panel below that names the centred book and opens it.
 *
 * @param place the open book and its shelf, or null when nothing has been opened yet (only
 * seen when the pane sits beside the library).
 * @param onBack null when the pane sits beside the library and there is nothing to go back to.
 * @param progress how far through each started book the reader is, from 0 to 1.
 * @param onReadBook opens a book in its reader; `restart` is true for a finished book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpenShelfPane(
    place: BookPlace?,
    onShowBook: (bookId: Long) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    progress: Map<Long, Double> = emptyMap(),
    onReadBook: (book: Book, restart: Boolean) -> Unit = { _, _ -> },
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = { Text(place?.shelf?.shelf?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.feature_library_back),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        if (place == null) {
            NothingOpen(Modifier.fillMaxSize().padding(padding))
        } else {
            val books = place.shelf.books
            val shelfId = place.shelf.shelf.id
            // The opening plays once for each shelf that is opened, not again on rotation.
            var openedShelfId by rememberSaveable { mutableStateOf<Long?>(null) }
            val state = remember(shelfId, reducedMotion) {
                CarouselState(place.index, opened = openedShelfId == shelfId, instant = reducedMotion)
            }
            SideEffect { state.updateCount(books.size) }
            LaunchedEffect(state) {
                state.playOpen()
                openedShelfId = shelfId
            }
            // The library, the arrows and edits elsewhere can all change which book is open;
            // the carousel follows. Its own drags report back through onShowBook when they
            // settle, by which time it is already there and this does nothing.
            val scope = rememberCoroutineScope()
            LaunchedEffect(state, place.index) {
                if (abs(state.position - place.index) > 0.01f) {
                    state.launchIn(scope) { moveTo(place.index) }
                }
            }
            val centre by remember(state) { derivedStateOf { state.centre } }
            val book = books[centre.coerceIn(0, books.lastIndex)]
            val fraction = progress[book.id]
            val read = { onReadBook(book, ReadingProgress.state(fraction) == ReadingState.FINISHED) }

            Column(Modifier.fillMaxSize().padding(padding)) {
                Carousel(
                    books = books,
                    state = state,
                    flat = reducedMotion,
                    onSettled = { index -> books.getOrNull(index)?.let { onShowBook(it.id) } },
                    onActivateCentre = { if (!book.isMissing) read() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                InfoPanel(
                    book = book,
                    position = centre + 1,
                    count = books.size,
                    fraction = fraction,
                    onRead = read,
                    onPrevious = books.getOrNull(centre - 1)?.let { neighbour -> { onShowBook(neighbour.id) } },
                    onNext = books.getOrNull(centre + 1)?.let { neighbour -> { onShowBook(neighbour.id) } },
                )
            }
        }
    }
}

@Composable
private fun NothingOpen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.feature_library_nothing_open_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.feature_library_nothing_open_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 360.dp),
        )
    }
}

/**
 * The title with the arrows to its neighbours beside it, one quiet line about the book, and
 * the button that opens it.
 */
@Composable
private fun InfoPanel(
    book: Book,
    position: Int,
    count: Int,
    fraction: Double?,
    onRead: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.feature_library_previous_book),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    // Spoken when the centred book changes, since focus stays where it was.
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = byline(book),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.feature_library_book_position, position, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.feature_library_next_book),
                )
            }
        }
        ReadButton(
            fraction = fraction,
            enabled = !book.isMissing,
            onClick = onRead,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
    }
}

/**
 * One wide pill: Start reading, Continue reading or Read again. A thin line along its bottom
 * edge fills as the book is read, with the percentage on the right.
 */
@Composable
private fun ReadButton(fraction: Double?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val state = ReadingProgress.state(fraction)
    val percent = ReadingProgress.percent(fraction)
    val label = stringResource(
        when (state) {
            ReadingState.NOT_STARTED -> R.string.feature_library_start_reading
            ReadingState.IN_PROGRESS -> R.string.feature_library_continue_reading
            ReadingState.FINISHED -> R.string.feature_library_read_again
        },
    )
    val spokenProgress = stringResource(R.string.feature_library_percent_read, percent)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                if (state != ReadingState.NOT_STARTED) stateDescription = spokenProgress
            },
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Row(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state != ReadingState.NOT_STARTED) {
                    Text(
                        text = stringResource(R.string.feature_library_percent, percent),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (state != ReadingState.NOT_STARTED) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(percent / 100f)
                        .height(3.dp)
                        .background(LocalContentColor.current.copy(alpha = 0.7f)),
                )
            }
        }
    }
}

/** "A. Author · vol. 3 · CBZ": whichever of those the book has. */
@Composable
private fun byline(book: Book): String {
    val parts = ArrayList<String>(4)
    book.author?.let(parts::add)
    book.volume?.let { parts.add(stringResource(R.string.feature_library_volume_short, volumeNumber(it))) }
    parts.add(book.format.name)
    if (book.isMissing) parts.add(stringResource(R.string.feature_library_missing))
    return parts.joinToString(" · ")
}

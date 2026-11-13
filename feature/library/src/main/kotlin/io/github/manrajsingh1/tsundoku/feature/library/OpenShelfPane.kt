package io.github.manrajsingh1.tsundoku.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.ui.theme.bookcase
import java.io.File

/**
 * The open shelf: the book that was tapped, face out, with the books either side one step
 * away. This is the place the 3D carousel takes in Phase 3; for now it shows one cover at a
 * time, which is enough to prove the two panes and the way back.
 *
 * @param place the open book and its shelf, or null when nothing has been opened yet (only
 * seen when the pane sits beside the library).
 * @param onBack null when the pane sits beside the library and there is nothing to go back to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpenShelfPane(
    place: BookPlace?,
    onShowBook: (bookId: Long) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
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
            Column(Modifier.fillMaxSize().padding(padding)) {
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // The cover grows with the pane but keeps its 2:3 shape and never fills
                    // a tablet from edge to edge.
                    val width = minOf(maxWidth * 0.7f, maxHeight * (2f / 3f), 360.dp)
                    Cover(place.book, Modifier.size(width = width, height = width * 1.5f))
                }
                InfoPanel(
                    book = place.book,
                    position = place.index + 1,
                    count = books.size,
                    onPrevious = books.getOrNull(place.index - 1)?.let { neighbour -> { onShowBook(neighbour.id) } },
                    onNext = books.getOrNull(place.index + 1)?.let { neighbour -> { onShowBook(neighbour.id) } },
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

@Composable
private fun Cover(book: Book, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            // The spine colour shows while the cover loads, and stays if there is none.
            .background(Color(book.spineColor)),
        contentAlignment = Alignment.Center,
    ) {
        val path = book.coverPath
        if (path != null) {
            AsyncImage(
                model = File(path),
                // Decorative: the panel below names the book.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.bookcase.spineText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** The title, one quiet line about the book, and the arrows to its neighbours. */
@Composable
private fun InfoPanel(
    book: Book,
    position: Int,
    count: Int,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                // Spoken when the arrows change the book, since focus stays on the arrow.
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
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

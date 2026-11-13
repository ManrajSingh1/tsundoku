package io.github.manrajsingh1.tsundoku.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.ShelfWithBooks
import java.io.File

/**
 * The library as a plain list, shelf by shelf. This is the Phase 1 stand-in that proves
 * import works end to end; the bookshelf of spines replaces it in Phase 2.
 *
 * @param importStatus drawn above the shelves. A slot, so this feature does not depend on
 * the import feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    modifier: Modifier = Modifier,
    importStatus: @Composable () -> Unit = {},
) {
    // One UI's extended app bar: tall with a large title at rest, condensing as the list scrolls.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.feature_library_title)) },
                actions = {
                    TextButton(onClick = onAddFiles) { Text(stringResource(R.string.feature_library_add_files)) }
                    TextButton(onClick = onAddFolder) { Text(stringResource(R.string.feature_library_add_folder)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when (uiState) {
            LibraryUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding))
            is LibraryUiState.Loaded -> if (uiState.isEmpty) {
                EmptyLibrary(onAddFiles, onAddFolder, importStatus, Modifier.fillMaxSize().padding(padding))
            } else {
                ShelfList(uiState.shelves, importStatus, padding)
            }
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
    importStatus: @Composable () -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "import-status", contentType = "status") { importStatus() }
        for (entry in shelves) {
            if (entry.books.isEmpty()) continue
            item(key = "shelf-${entry.shelf.id}", contentType = "shelf") {
                ShelfHeader(name = entry.shelf.name, count = entry.books.size)
            }
            items(entry.books, key = { "book-${it.id}" }, contentType = { "book" }) { book ->
                BookRow(book)
            }
        }
    }
}

@Composable
private fun ShelfHeader(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = pluralStringResource(R.plurals.feature_library_shelf_count, count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookRow(book: Book) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            // One stop for TalkBack per book, read as a sentence, not five fragments.
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(book)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val byline = listOfNotNull(seriesLine(book), book.author).joinToString(" · ")
            if (byline.isNotEmpty()) {
                // No line limit: at large text sizes these wrap instead of losing their ends.
                Text(
                    text = byline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = detailLine(book),
                style = MaterialTheme.typography.bodySmall,
                color = if (book.isMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Cover(book: Book) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 84.dp)
            .clip(shape)
            // The spine colour shows while the thumbnail loads, and stays if there is none.
            .background(Color(book.spineColor)),
    ) {
        val path = book.coverPath
        if (path != null) {
            AsyncImage(
                model = File(path),
                // Decorative: the row's text already names the book.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun seriesLine(book: Book): String? {
    val series = book.series ?: return null
    val volume = book.volume ?: return series
    val number = if (volume % 1.0 == 0.0) volume.toInt().toString() else volume.toString()
    return stringResource(R.string.feature_library_series_volume, series, number)
}

@Composable
private fun detailLine(book: Book): String {
    val parts = ArrayList<String>(4)
    if (book.isMissing) parts.add(stringResource(R.string.feature_library_missing))
    parts.add(book.format.name)
    book.pageCount?.let { parts.add(pluralStringResource(R.plurals.feature_library_pages, it, it)) }
    if (book.readingDirection == ReadingDirection.RIGHT_TO_LEFT) {
        parts.add(stringResource(R.string.feature_library_right_to_left))
    }
    return parts.joinToString(" · ")
}

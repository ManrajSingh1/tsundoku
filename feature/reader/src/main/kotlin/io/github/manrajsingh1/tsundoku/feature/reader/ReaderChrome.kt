package io.github.manrajsingh1.tsundoku.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.model.ReadingProgress

/** What sits under the bars: the comic itself, or what to say while there is no page yet. */
@Composable
internal fun ReaderContent(viewModel: ReaderViewModel, onClose: () -> Unit, onPageTapped: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (val current = state) {
            ReaderUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val opening = stringResource(R.string.feature_reader_opening)
                CircularProgressIndicator(Modifier.semantics { contentDescription = opening })
            }

            is ReaderUiState.Failed -> FailedToOpen(onClose)

            is ReaderUiState.Ready -> if (current.book.format == BookFormat.CBZ) {
                val rightToLeft by viewModel.rightToLeft.collectAsStateWithLifecycle()
                // Changing direction lays the pager out afresh, on the page being read.
                key(rightToLeft) {
                    ComicReader(
                        pageCount = current.pageCount,
                        startPage = remember { ReadingProgress.pageOf(viewModel.position, current.pageCount) },
                        rightToLeft = rightToLeft,
                        pageRequests = viewModel.pageRequests,
                        loadPage = viewModel::comicPage,
                        onPage = viewModel::onComicPage,
                        onToggleControls = onPageTapped,
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedToOpen(onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.feature_reader_failed_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.feature_reader_failed_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp).widthIn(max = 420.dp),
        )
        Button(onClick = onClose) { Text(stringResource(R.string.feature_reader_failed_close)) }
    }
}

/** The book's title and the way back to the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(viewModel: ReaderViewModel, onClose: () -> Unit) {
    val visible by viewModel.controlsVisible.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!visible) return
    val title = when (val current = state) {
        is ReaderUiState.Ready -> current.book.title
        is ReaderUiState.Failed -> current.title.orEmpty()
        ReaderUiState.Loading -> ""
    }
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.feature_reader_close),
                )
            }
        },
    )
}

/**
 * Where the reader is in the book, and the one or two settings that belong to its format,
 * as a single slim pill that floats over the foot of the page.
 */
@Composable
internal fun ReaderBottomBar(viewModel: ReaderViewModel) {
    val visible by viewModel.controlsVisible.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val ready = state as? ReaderUiState.Ready
    if (!visible || ready == null) return

    Box(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.heightIn(min = 48.dp).padding(start = 20.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (ready.book.format) {
                    BookFormat.CBZ -> ComicControls(viewModel, location)
                    BookFormat.PDF -> PdfControls(location)
                    BookFormat.EPUB -> EpubControls(viewModel, location)
                }
            }
        }
    }
}

/** "12 / 180" to the eye, "Page 12 of 180" to a screen reader. */
@Composable
private fun PageLabel(page: Int, pageCount: Int) {
    val spoken = stringResource(R.string.feature_reader_page_of, page + 1, pageCount)
    Text(
        text = stringResource(R.string.feature_reader_page_short, page + 1, pageCount),
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        modifier = Modifier.semantics { contentDescription = spoken },
    )
}

@Composable
private fun PercentLabel(fraction: Double) {
    val percent = ReadingProgress.percent(fraction)
    val spoken = stringResource(R.string.feature_reader_percent, percent)
    Text(
        text = stringResource(R.string.feature_reader_percent_short, percent),
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        modifier = Modifier.semantics { contentDescription = spoken },
    )
}

@Composable
private fun RowScope.ThinProgress(fraction: Double) {
    LinearProgressIndicator(
        progress = { fraction.toFloat() },
        modifier = Modifier.weight(1f).padding(horizontal = 14.dp).height(4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.ComicControls(viewModel: ReaderViewModel, location: ReaderLocation) {
    val rightToLeft by viewModel.rightToLeft.collectAsStateWithLifecycle()
    // While the slider is held it shows the page it would go to; the pager moves on release.
    var held by remember { mutableStateOf<Float?>(null) }
    val page = held?.let { ReaderMath.sliderPage(it, location.pageCount) } ?: (location.page ?: 0)

    PageLabel(page, location.pageCount)
    if (location.pageCount > 1) {
        val sliderName = stringResource(R.string.feature_reader_page_slider)
        // The slider runs the way the book does: in manga the first page is on the right.
        CompositionLocalProvider(
            LocalLayoutDirection provides if (rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            Slider(
                value = held ?: page.toFloat(),
                onValueChange = { held = it },
                onValueChangeFinished = {
                    held?.let { viewModel.seekToPage(ReaderMath.sliderPage(it, location.pageCount)) }
                    held = null
                },
                valueRange = 0f..(location.pageCount - 1).toFloat(),
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp).semantics { contentDescription = sliderName },
                thumb = {
                    Box(Modifier.size(16.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(4.dp),
                        thumbTrackGapSize = 0.dp,
                        drawStopIndicator = null,
                    )
                },
            )
        }
    } else {
        Spacer(Modifier.weight(1f))
    }
    // An arrow pointing the way the pages run. Tapping it turns the book round, for this
    // volume and the rest of its series.
    val spoken = stringResource(
        if (rightToLeft) R.string.feature_reader_direction_rtl_spoken else R.string.feature_reader_direction_ltr_spoken,
    )
    IconButton(
        onClick = { viewModel.setRightToLeft(!rightToLeft) },
        modifier = Modifier.semantics { contentDescription = spoken },
    ) {
        Text(text = if (rightToLeft) "\u2190" else "\u2192", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun RowScope.PdfControls(location: ReaderLocation) {
    val page = location.page
    if (page != null && location.pageCount > 0) PageLabel(page, location.pageCount) else PercentLabel(location.fraction)
    ThinProgress(location.fraction)
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun RowScope.EpubControls(viewModel: ReaderViewModel, location: ReaderLocation) {
    PercentLabel(location.fraction)
    ThinProgress(location.fraction)
    val smaller = stringResource(R.string.feature_reader_smaller_text)
    val larger = stringResource(R.string.feature_reader_larger_text)
    IconButton(
        onClick = { viewModel.changeFontSize(-1) },
        modifier = Modifier.semantics { contentDescription = smaller },
    ) { Text(stringResource(R.string.feature_reader_smaller_text_label), style = MaterialTheme.typography.labelLarge) }
    IconButton(
        onClick = { viewModel.changeFontSize(1) },
        modifier = Modifier.semantics { contentDescription = larger },
    ) { Text(stringResource(R.string.feature_reader_larger_text_label), style = MaterialTheme.typography.titleMedium) }
}

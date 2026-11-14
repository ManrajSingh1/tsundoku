package io.github.manrajsingh1.tsundoku.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * A comic, one page to the screen. Swipe or tap an edge to turn the page, tap the middle for
 * the controls, pinch or double-tap to zoom. Manga pages run right to left: the pager is laid
 * out in reverse, so the next page is to the left and a swipe to the right brings it in.
 *
 * @param loadPage decodes a page no larger than the given size, or returns null if it cannot.
 * @param pageRequests pages asked for from the controls.
 */
@Composable
internal fun ComicReader(
    pageCount: Int,
    startPage: Int,
    rightToLeft: Boolean,
    pageRequests: Flow<Int>,
    loadPage: suspend (index: Int, maxWidth: Int, maxHeight: Int) -> Bitmap?,
    onPage: (Int) -> Unit,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = startPage.coerceIn(0, maxOf(pageCount - 1, 0))) { pageCount }
    val scope = rememberCoroutineScope()
    val reportPage by rememberUpdatedState(onPage)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { reportPage(it) }
    }
    LaunchedEffect(pagerState, pageRequests) {
        pageRequests.collect { pagerState.scrollToPage(it.coerceIn(0, maxOf(pageCount - 1, 0))) }
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = rightToLeft,
        modifier = modifier.fillMaxSize().background(Color.Black),
        key = { it },
    ) { index ->
        ComicPage(
            index = index,
            pageCount = pageCount,
            loadPage = loadPage,
            onTap = { x, width ->
                when (ReaderMath.tapZone(x, width, rightToLeft)) {
                    TapZone.CONTROLS -> onToggleControls()
                    TapZone.NEXT -> if (index < pageCount - 1) scope.launch { pagerState.animateScrollToPage(index + 1) }
                    TapZone.PREVIOUS -> if (index > 0) scope.launch { pagerState.animateScrollToPage(index - 1) }
                }
            },
        )
    }
}

@Composable
private fun ComicPage(
    index: Int,
    pageCount: Int,
    loadPage: suspend (index: Int, maxWidth: Int, maxHeight: Int) -> Bitmap?,
    onTap: (x: Float, width: Int) -> Unit,
) {
    var page by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    val zoom = remember { ZoomState() }
    val tapped by rememberUpdatedState(onTap)

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        LaunchedEffect(index, width, height) {
            // Half as large again as the screen, so a page stays sharp part-way into a zoom
            // without ever being held at the size of the scan.
            val decoded = loadPage(index, width * 3 / 2, height * 3 / 2)
            if (decoded == null) {
                failed = page == null
            } else {
                page = decoded.asImageBitmap()
                failed = false
            }
        }

        val shown = page
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zoom) {
                    detectTapGestures(
                        onDoubleTap = { zoom.toggle() },
                        onTap = { tapped(it.x, size.width) },
                    )
                }
                .pointerInput(zoom) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            // One finger on an unzoomed page is left alone: it is the pager's
                            // swipe. Two fingers, or any drag on a zoomed page, move the page.
                            if (fingers >= 2 || zoom.scale > 1f) {
                                zoom.transform(event.calculateZoom(), event.calculatePan(), size)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                shown != null -> Image(
                    bitmap = shown,
                    contentDescription = stringResource(R.string.feature_reader_page_of, index + 1, pageCount),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    },
                )
                failed -> Text(
                    text = stringResource(R.string.feature_reader_page_failed, index + 1),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
                else -> CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/** How far one page is zoomed and where it has been dragged to. A new page starts unzoomed. */
@Stable
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    fun transform(zoomBy: Float, panBy: Offset, size: IntSize) {
        scale = (scale * zoomBy).coerceIn(1f, MAX_SCALE)
        // The page may be dragged only as far as its own edges.
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        offset = Offset(
            (offset.x + panBy.x).coerceIn(-maxX, maxX),
            (offset.y + panBy.y).coerceIn(-maxY, maxY),
        )
    }

    fun toggle() {
        if (scale > 1f) {
            scale = 1f
            offset = Offset.Zero
        } else {
            scale = DOUBLE_TAP_SCALE
        }
    }

    private companion object {
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
    }
}

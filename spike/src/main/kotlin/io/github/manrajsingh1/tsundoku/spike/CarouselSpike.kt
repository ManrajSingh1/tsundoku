package io.github.manrajsingh1.tsundoku.spike

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Reference values from the prototype (brief, "Reference values from the prototype").
private val CoverWidth = 188.dp
private val CoverHeight = 282.dp
private val FirstStep = 118.dp
private val FurtherStep = 50.dp
private val DepthPush = 60.dp
private val Perspective = 1000.dp
private val ShelfStep = 48.dp
private const val OpenScale = 0.62f
private const val DegreesToRadians = 0.017453292f
private const val OpenMillis = 750
private const val TestMillis = 20_000L

private val Walnut = Color(0xFF2B1D14)
private val Amber = Color(0xFFF2C14E)
private val Cream = Color(0xFFF3E9DC)
private val PageEdge = Color(0xFFE6DAC3)

/** Everything worth changing on the device without a rebuild. */
@Immutable
data class Tuning(
    val bookCount: Int = 50,
    /** false = the flat fallback the brief asks for if 3D cannot hold the frame rate. */
    val threeD: Boolean = true,
    /** Books drawn on each side of the centred one. */
    val windowRadius: Int = 3,
    val angle: Float = 58f,
    val cameraDistance: Float = 14f,
)

@Immutable
private class Metrics(
    val firstStep: Float,
    val furtherStep: Float,
    val depthPush: Float,
    val perspective: Float,
    val shelfStep: Float,
    val coverWidth: Float,
)

@Stable
class CarouselState(val count: Int) {
    /** Which book is centred, as a fraction: 2.5 is halfway between book 2 and book 3. */
    var position by mutableFloatStateOf(0f)
        private set

    /** 0 = spines on the shelf, 1 = covers in the carousel. */
    var open by mutableFloatStateOf(0f)
        private set

    val centre: Int get() = position.roundToInt().coerceIn(0, count - 1)

    private var job: Job? = null

    fun dragBy(books: Float) {
        position = (position + books).coerceIn(0f, (count - 1).toFloat())
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun launchIn(scope: CoroutineScope, block: suspend CarouselState.() -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }

    /** Follows the fling a little, then snaps to the nearest book. */
    suspend fun settle(velocity: Float) {
        val target = (position + velocity * 0.18f).roundToInt().coerceIn(0, count - 1).toFloat()
        animate(position, target, velocity, spring(dampingRatio = 0.85f, stiffness = 200f)) { value, _ ->
            position = value
        }
    }

    suspend fun playOpen() {
        open = 0f
        animate(
            0f,
            1f,
            animationSpec = tween(durationMillis = OpenMillis, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
        ) { value, _ -> open = value }
    }

    /** A repeatable workload: sweeps five books at a time, end to end, for the given time. */
    suspend fun autoSwipe(millis: Long) {
        val start = System.nanoTime()
        var forward = true
        while ((System.nanoTime() - start) / 1_000_000 < millis) {
            if (position >= count - 1.5f) forward = false
            if (position <= 0.5f) forward = true
            val target = (position.roundToInt() + if (forward) 5 else -5).coerceIn(0, count - 1).toFloat()
            animate(position, target, animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)) { value, _ ->
                position = value
            }
            delay(120)
        }
    }
}

// The pose of one book. These run for every drawn book on every frame, so they take and
// return plain floats and allocate nothing.

/** Rotation about the vertical axis. Positive turns the right edge away from the viewer. */
private fun bookAngle(d: Float, open: Float, tuning: Tuning): Float {
    if (!tuning.threeD) return 0f
    val settled = -tuning.angle * d.coerceIn(-1f, 1f)
    return lerp(90f, settled, open)
}

/** A graphics layer has no real depth, so "pushed back" is drawn as "smaller". */
private fun bookScale(d: Float, open: Float, m: Metrics): Float {
    val push = m.depthPush * min(abs(d), 3f)
    return lerp(OpenScale, m.perspective / (m.perspective + push), open)
}

private fun bookShift(d: Float, open: Float, angle: Float, scale: Float, m: Metrics): Float {
    val distance = abs(d)
    val carousel = sign(d) * (m.firstStep * min(distance, 1f) + m.furtherStep * max(distance - 1f, 0f))
    val shelf = d * m.shelfStep
    // Each book turns and scales about its hinge: the left edge while its spine shows, the
    // right edge while its page edge shows. Left alone, that swings the cover away from its
    // slot (a left neighbour would carry its spine off the edge of a phone) and makes the
    // book jump when the hinge swaps sides. Shifting by how far the cover's centre has moved
    // keeps every cover centred on its slot and the swap seamless.
    val hingeFromCentre = if (angle >= 0f) -m.coverWidth / 2f else m.coverWidth / 2f
    val foreshortening = scale * cos(angle * DegreesToRadians)
    return lerp(shelf, carousel, open) - hingeFromCentre * (1f - foreshortening)
}

private fun bookShade(d: Float, open: Float): Float = min(0.22f * abs(d), 0.55f) * open

@Composable
fun SpikeApp(frameStats: FrameStats) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Amber, onPrimary = Walnut)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Walnut, contentColor = Cream) {
            CarouselSpikeScreen(frameStats)
        }
    }
}

@Composable
private fun CarouselSpikeScreen(frameStats: FrameStats) {
    var tuning by remember { mutableStateOf(Tuning()) }
    var showTuning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FrameSummary?>(null) }
    val density = LocalDensity.current
    val covers = remember(density) {
        with(density) { CoverPool(CoverWidth.roundToPx(), CoverHeight.roundToPx()) }
    }
    val books = remember(tuning.bookCount) { sampleBooks(tuning.bookCount) }
    val state = remember(tuning.bookCount) { CarouselState(tuning.bookCount) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state) { state.playOpen() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        StatsReadout(frameStats.summary, result)
        Carousel(books, covers, state, tuning, Modifier.weight(1f).fillMaxWidth())
        InfoPanel(books, state)
        Controls(
            tuning = tuning,
            showTuning = showTuning,
            onTuning = {
                tuning = it
                result = null
            },
            onToggleTuning = { showTuning = !showTuning },
            onRunTest = {
                result = null
                state.launchIn(scope) {
                    frameStats.reset()
                    autoSwipe(TestMillis)
                    delay(300)
                    frameStats.publishNow()
                    delay(200)
                    result = frameStats.summary
                }
            },
            onReplayOpen = { scope.launch { state.playOpen() } },
            onResetStats = {
                result = null
                frameStats.reset()
            },
        )
    }
}

@Composable
private fun StatsReadout(summary: FrameSummary, result: FrameSummary?) {
    val mono = FontFamily.Monospace
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            String.format(Locale.US, "%.0f Hz   budget %.1f ms   %d frames", summary.refreshHz, summary.budgetMs, summary.frames),
            fontFamily = mono,
            fontSize = 12.sp,
        )
        Text(
            String.format(Locale.US, "p50 %.1f   p90 %.1f   p99 %.1f   worst %.1f ms", summary.p50Ms, summary.p90Ms, summary.p99Ms, summary.worstMs),
            fontFamily = mono,
            fontSize = 12.sp,
        )
        Text(
            String.format(Locale.US, "late %.1f%%   dropped %.1f%%", summary.latePercent, summary.droppedPercent),
            fontFamily = mono,
            fontSize = 12.sp,
        )
        if (result != null) {
            Text(
                String.format(
                    Locale.US,
                    "RESULT %s   dropped %.1f%%   p99 %.1f ms of %.1f",
                    if (result.passes) "PASS" else "FAIL",
                    result.droppedPercent,
                    result.p99Ms,
                    result.budgetMs,
                ),
                fontFamily = mono,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (result.passes) Amber else Color(0xFFFF8A80),
            )
        }
    }
}

@Composable
private fun Carousel(
    books: List<Book>,
    covers: CoverPool,
    state: CarouselState,
    tuning: Tuning,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val m = remember(density) {
        with(density) {
            Metrics(
                firstStep = FirstStep.toPx(),
                furtherStep = FurtherStep.toPx(),
                depthPush = DepthPush.toPx(),
                perspective = Perspective.toPx(),
                shelfStep = ShelfStep.toPx(),
                coverWidth = CoverWidth.toPx(),
            )
        }
    }
    val scope = rememberCoroutineScope()
    // Changes only when a different book becomes the centred one, so a swipe recomposes
    // this list about once per book passed, never once per frame.
    val centre by remember(state) { derivedStateOf { state.centre } }
    val dragState = rememberDraggableState { delta -> state.dragBy(-delta / m.firstStep) }

    Box(
        modifier = modifier.draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            onDragStarted = { state.stop() },
            onDragStopped = { velocity -> state.launchIn(scope) { settle(-velocity / m.firstStep) } },
        ),
        contentAlignment = Alignment.Center,
    ) {
        val first = max(centre - tuning.windowRadius, 0)
        val last = min(centre + tuning.windowRadius, books.lastIndex)
        // Farthest first, so nearer books are drawn over them.
        for (i in (first..last).sortedByDescending { abs(it - centre) }) {
            key(i) { BookBox(books[i], covers[books[i].coverIndex], state, tuning, m) }
        }
    }
}

/**
 * One book as three flat faces: cover, spine (hinged on the cover's left edge) and page edge
 * (hinged on its right edge). Compose has no 3D scene, so each face is its own layer and the
 * faces meet because they share a hinge as pivot.
 *
 * Every per-frame value is read inside a layer or draw block. Swiping therefore re-runs only
 * the draw phase; nothing here recomposes or re-measures while the carousel moves.
 */
@Composable
private fun BoxScope.BookBox(book: Book, cover: ImageBitmap, state: CarouselState, tuning: Tuning, m: Metrics) {
    val index = book.index
    val thickness = book.spineWidth

    Box(Modifier.align(Alignment.Center).size(CoverWidth, CoverHeight)) {
        // Spine.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = -thickness)
                .size(thickness, CoverHeight)
                .graphicsLayer {
                    val d = index - state.position
                    val open = state.open
                    val angle = bookAngle(d, open, tuning)
                    val scale = bookScale(d, open, m)
                    transformOrigin = TransformOrigin(1f, 0.5f)
                    rotationY = angle - 90f
                    scaleX = scale
                    scaleY = scale
                    translationX = bookShift(d, open, angle, scale, m)
                    cameraDistance = tuning.cameraDistance
                    alpha = if (angle > 0.5f) 1f else 0f
                }
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Black, alpha = bookShade(index - state.position, state.open))
                }
                .background(book.spineColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = book.title,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.requiredWidth(CoverHeight - 24.dp).rotate(90f),
            )
        }

        // Page edge.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = thickness)
                .size(thickness, CoverHeight)
                .graphicsLayer {
                    val d = index - state.position
                    val open = state.open
                    val angle = bookAngle(d, open, tuning)
                    val scale = bookScale(d, open, m)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationY = angle + 90f
                    scaleX = scale
                    scaleY = scale
                    translationX = bookShift(d, open, angle, scale, m)
                    cameraDistance = tuning.cameraDistance
                    alpha = if (angle < -0.5f) 1f else 0f
                }
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Black, alpha = bookShade(index - state.position, state.open))
                }
                .background(PageEdge),
        )

        // Cover.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val d = index - state.position
                    val open = state.open
                    val angle = bookAngle(d, open, tuning)
                    val scale = bookScale(d, open, m)
                    transformOrigin = if (angle >= 0f) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                    rotationY = angle
                    scaleX = scale
                    scaleY = scale
                    translationX = bookShift(d, open, angle, scale, m)
                    cameraDistance = tuning.cameraDistance
                    alpha = if (abs(angle) < 89.5f) 1f else 0f
                }
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Black, alpha = bookShade(index - state.position, state.open))
                },
        ) {
            Image(
                bitmap = cover,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds,
            )
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    text = book.title,
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontSize = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = book.author, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun InfoPanel(books: List<Book>, state: CarouselState) {
    val centre by remember(state) { derivedStateOf { state.centre } }
    val book = books[min(centre, books.lastIndex)]
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = book.title,
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${book.author} · ${book.format} · Book ${centre + 1} of ${books.size}",
            fontSize = 13.sp,
            color = Cream.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}

@Composable
private fun Controls(
    tuning: Tuning,
    showTuning: Boolean,
    onTuning: (Tuning) -> Unit,
    onToggleTuning: () -> Unit,
    onRunTest: () -> Unit,
    onReplayOpen: () -> Unit,
    onResetStats: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onRunTest) { Text("Run 20 s test") }
            OutlinedButton(onClick = onReplayOpen) { Text("Replay open") }
        }
        // Two short rows rather than one long one: on a phone the fourth button fell off the
        // right edge, where nobody would find it.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onResetStats) { Text("Reset stats") }
            OutlinedButton(onClick = onToggleTuning) { Text(if (showTuning) "Hide tuning" else "Tuning") }
        }
        if (showTuning) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Choice("3D", tuning.threeD) { onTuning(tuning.copy(threeD = true)) }
                Choice("Flat", !tuning.threeD) { onTuning(tuning.copy(threeD = false)) }
                for (count in listOf(7, 50, 200)) {
                    Choice("$count books", tuning.bookCount == count) { onTuning(tuning.copy(bookCount = count)) }
                }
                for (radius in listOf(2, 3, 5)) {
                    Choice("draw ±$radius", tuning.windowRadius == radius) { onTuning(tuning.copy(windowRadius = radius)) }
                }
            }
            Text(String.format(Locale.US, "Neighbour angle %.0f°", tuning.angle), fontSize = 12.sp)
            Slider(
                value = tuning.angle,
                onValueChange = { onTuning(tuning.copy(angle = it)) },
                valueRange = 0f..80f,
            )
            Text(String.format(Locale.US, "Camera distance %.1f", tuning.cameraDistance), fontSize = 12.sp)
            Slider(
                value = tuning.cameraDistance,
                onValueChange = { onTuning(tuning.copy(cameraDistance = it)) },
                valueRange = 4f..40f,
            )
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        FilledTonalButton(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

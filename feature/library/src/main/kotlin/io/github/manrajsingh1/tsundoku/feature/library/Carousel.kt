package io.github.manrajsingh1.tsundoku.feature.library

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.ui.theme.bookcase
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Reference values from the prototype (brief, "Reference values from the prototype"), in dp
// before the carousel is scaled to its pane.
private const val COVER_WIDTH = 188f
private const val COVER_HEIGHT = 282f
private const val FIRST_STEP = 118f
private const val FURTHER_STEP = 50f
private const val DEPTH_PUSH = 60f
private const val PERSPECTIVE = 1000f
private const val SHELF_STEP = 48f
private const val CAMERA_DISTANCE = 14f
private const val OPEN_MILLIS = 750

/** Books drawn on each side of the centred one. The rest are not composed at all. */
private const val WINDOW_RADIUS = 3

private val PageEdge = Color(0xFFE6DAC3)

/** The carousel's distances in pixels, for the pane it is drawn in. */
internal class CarouselMetrics(
    val firstStep: Float,
    val furtherStep: Float,
    val depthPush: Float,
    val perspective: Float,
    val shelfStep: Float,
    val coverWidth: Float,
    val coverHeight: Float,
)

/**
 * Where each book stands. `d` is how many books a book is from the centre (negative to the
 * left) and `open` runs from 0 (spines on the shelf) to 1 (covers in the carousel).
 *
 * These run for every drawn book on every frame, so they take and return plain floats and
 * allocate nothing.
 */
internal object CarouselPose {
    const val OPEN_SCALE = 0.62f
    const val NEIGHBOUR_ANGLE = 58f
    private const val DEGREES_TO_RADIANS = 0.017453292f

    private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

    /** Rotation about the vertical axis. Positive turns the right edge away from the viewer. */
    fun angle(d: Float, open: Float, flat: Boolean): Float {
        if (flat) return 0f
        return lerp(90f, -NEIGHBOUR_ANGLE * d.coerceIn(-1f, 1f), open)
    }

    /** A graphics layer has no real depth, so "pushed back" is drawn as "smaller". */
    fun scale(d: Float, open: Float, m: CarouselMetrics): Float {
        val push = m.depthPush * min(abs(d), 3f)
        return lerp(OPEN_SCALE, m.perspective / (m.perspective + push), open)
    }

    /** How far the cover's centre is from the pane's centre once the carousel is open. */
    fun slot(d: Float, m: CarouselMetrics): Float {
        val distance = abs(d)
        return sign(d) * (m.firstStep * min(distance, 1f) + m.furtherStep * max(distance - 1f, 0f))
    }

    /** Which edge the book turns about: the left while its spine shows, else the right. */
    fun hingeFromCentre(angle: Float, m: CarouselMetrics): Float =
        if (angle >= 0f) -m.coverWidth / 2f else m.coverWidth / 2f

    /**
     * The sideways move that puts a book in its place. Each book turns and scales about its
     * hinge; left alone, that swings the cover away from its slot and makes it jump when the
     * hinge swaps sides. Shifting by how far the cover's centre has moved keeps every cover
     * centred on its slot and the swap seamless.
     */
    fun shift(d: Float, open: Float, angle: Float, scale: Float, m: CarouselMetrics): Float {
        val foreshortening = scale * cos(angle * DEGREES_TO_RADIANS)
        return lerp(d * m.shelfStep, slot(d, m), open) - hingeFromCentre(angle, m) * (1f - foreshortening)
    }

    /** Side covers darken with distance: 22% a place, never more than 55%. */
    fun shade(d: Float, open: Float): Float = min(0.22f * abs(d), 0.55f) * open

    /** Where a drag let go at [velocity] books a second comes to rest: it follows the fling a little. */
    fun settleTarget(position: Float, velocity: Float, count: Int): Int =
        (position + velocity * 0.18f).roundToInt().coerceIn(0, max(count - 1, 0))

    /**
     * How many books a tap asks to move by: none on the centred cover, one on the book
     * beside it, and one more for each book further out, to at most three.
     */
    fun tapSteps(offsetFromCentre: Float, m: CarouselMetrics): Int {
        val distance = abs(offsetFromCentre)
        if (distance <= m.coverWidth / 2f) return 0
        val beyondFirst = distance - m.firstStep - m.furtherStep / 2f
        val steps = if (beyondFirst > 0f) 2 + (beyondFirst / m.furtherStep).toInt() else 1
        return if (offsetFromCentre < 0f) -min(steps, 3) else min(steps, 3)
    }
}

/**
 * Which book is centred and how far the carousel has opened. Read only inside layer and draw
 * blocks while it moves, so a swipe redraws without recomposing.
 */
@Stable
internal class CarouselState(initialIndex: Int, opened: Boolean, private val instant: Boolean) {

    var count by mutableIntStateOf(1)
        private set

    /** Which book is centred, as a fraction: 2.5 is halfway between book 2 and book 3. */
    var position by mutableFloatStateOf(initialIndex.toFloat())
        private set

    /** 0 = spines on the shelf, 1 = covers in the carousel. */
    var open by mutableFloatStateOf(if (opened || instant) 1f else 0f)
        private set

    val centre: Int get() = position.roundToInt().coerceIn(0, max(count - 1, 0))

    private var job: Job? = null

    fun updateCount(books: Int) {
        count = max(books, 1)
        position = position.coerceIn(0f, (count - 1).toFloat())
    }

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

    /** Glides to a book and stops on it. With animations removed, it is simply there. */
    suspend fun moveTo(index: Int, velocity: Float = 0f) {
        val target = index.coerceIn(0, count - 1).toFloat()
        if (instant) {
            position = target
            return
        }
        animate(position, target, velocity, spring(dampingRatio = 0.85f, stiffness = 200f)) { value, _ ->
            position = value
        }
    }

    suspend fun playOpen() {
        if (instant) {
            open = 1f
            return
        }
        animate(
            initialValue = open,
            targetValue = 1f,
            animationSpec = tween(durationMillis = OPEN_MILLIS, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
        ) { value, _ -> open = value }
    }
}

/**
 * True when the carousel should stay flat and still: the user has removed animations in
 * the system's accessibility settings, or the device is one Android itself calls low on memory.
 */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        scale == 0f || lowRam
    }
}

/**
 * A shelf's books as a row of 3D covers. The centred book faces front; its neighbours turn
 * towards it and stand back, showing spines on the left and page edges on the right.
 *
 * It is drawn for the eye only. Screen readers get the same browsing from the panel below:
 * the title, the arrows and the read button.
 *
 * @param flat no turning: covers overlap like cards. Used when motion is reduced.
 * @param onSettled called with the centred book's index when a drag or a tap comes to rest.
 * @param onActivateCentre called when the centred cover itself is tapped.
 */
@Composable
internal fun Carousel(
    books: List<Book>,
    state: CarouselState,
    flat: Boolean,
    onSettled: (index: Int) -> Unit,
    onActivateCentre: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val settled by rememberUpdatedState(onSettled)
    val activate by rememberUpdatedState(onActivateCentre)
    // Changes only when a different book becomes the centred one, so a swipe recomposes
    // this list about once per book passed, never once per frame.
    val centre by remember(state) { derivedStateOf { state.centre } }

    BoxWithConstraints(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        // The prototype's sizes fit a phone held upright. Scale them to this pane.
        val fit = min(maxWidth.value / 400f, maxHeight.value / (COVER_HEIGHT * 1.1f)).coerceIn(0.45f, 1.6f)
        val density = LocalDensity.current
        val m = remember(density, fit) {
            with(density) {
                CarouselMetrics(
                    firstStep = (FIRST_STEP * fit).dp.toPx(),
                    furtherStep = (FURTHER_STEP * fit).dp.toPx(),
                    depthPush = (DEPTH_PUSH * fit).dp.toPx(),
                    perspective = (PERSPECTIVE * fit).dp.toPx(),
                    shelfStep = (SHELF_STEP * fit).dp.toPx(),
                    coverWidth = (COVER_WIDTH * fit).dp.toPx(),
                    coverHeight = (COVER_HEIGHT * fit).dp.toPx(),
                )
            }
        }
        val dragState = rememberDraggableState { delta -> state.dragBy(-delta / m.firstStep) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { state.stop() },
                    onDragStopped = { velocity ->
                        val booksPerSecond = -velocity / m.firstStep
                        val target = CarouselPose.settleTarget(state.position, booksPerSecond, state.count)
                        state.launchIn(scope) {
                            moveTo(target, booksPerSecond)
                            settled(target)
                        }
                    },
                )
                .pointerInput(state, m) {
                    detectTapGestures { tap ->
                        val steps = CarouselPose.tapSteps(tap.x - size.width / 2f, m)
                        val onCover = abs(tap.y - size.height / 2f) <= m.coverHeight / 2f
                        if (steps == 0) {
                            if (onCover) activate()
                        } else {
                            val target = (state.centre + steps).coerceIn(0, state.count - 1)
                            state.launchIn(scope) {
                                moveTo(target)
                                settled(target)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val first = max(centre - WINDOW_RADIUS, 0)
            val last = min(centre + WINDOW_RADIUS, books.lastIndex)
            // Farthest first, so nearer books are drawn over them.
            for (i in (first..last).sortedByDescending { abs(it - centre) }) {
                key(books[i].id) { BookBox(books[i], i, state, flat, m, fit) }
            }
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
private fun BoxScope.BookBox(book: Book, index: Int, state: CarouselState, flat: Boolean, m: CarouselMetrics, fit: Float) {
    val coverWidth: Dp = (COVER_WIDTH * fit).dp
    val coverHeight: Dp = (COVER_HEIGHT * fit).dp
    val thickness: Dp = (SpineMetrics.width(book.format, book.pageCount) * fit).dp
    val camera = CAMERA_DISTANCE * fit
    val spineColor = Color(book.spineColor)

    Box(Modifier.align(Alignment.Center).size(coverWidth, coverHeight)) {
        // Spine.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = -thickness)
                .size(thickness, coverHeight)
                .graphicsLayer {
                    val d = index - state.position
                    val open = state.open
                    val angle = CarouselPose.angle(d, open, flat)
                    val scale = CarouselPose.scale(d, open, m)
                    transformOrigin = TransformOrigin(1f, 0.5f)
                    rotationY = angle - 90f
                    scaleX = scale
                    scaleY = scale
                    translationX = CarouselPose.shift(d, open, angle, scale, m)
                    cameraDistance = camera
                    alpha = if (angle > 0.5f) 1f else 0f
                }
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Black, alpha = CarouselPose.shade(index - state.position, state.open))
                }
                .background(spineColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = book.title,
                color = MaterialTheme.bookcase.spineText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.requiredWidth(coverHeight - 24.dp).rotate(90f),
            )
        }

        // Page edge.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = thickness)
                .size(thickness, coverHeight)
                .graphicsLayer {
                    val d = index - state.position
                    val open = state.open
                    val angle = CarouselPose.angle(d, open, flat)
                    val scale = CarouselPose.scale(d, open, m)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationY = angle + 90f
                    scaleX = scale
                    scaleY = scale
                    translationX = CarouselPose.shift(d, open, angle, scale, m)
                    cameraDistance = camera
                    alpha = if (angle < -0.5f) 1f else 0f
                }
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Black, alpha = CarouselPose.shade(index - state.position, state.open))
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
                    val angle = CarouselPose.angle(d, open, flat)
                    val scale = CarouselPose.scale(d, open, m)
                    transformOrigin = if (angle >= 0f) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                    rotationY = angle
                    scaleX = scale
                    scaleY = scale
                    translationX = CarouselPose.shift(d, open, angle, scale, m)
                    cameraDistance = camera
                    alpha = if (abs(angle) < 89.5f) 1f else 0f
                }
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Black, alpha = CarouselPose.shade(index - state.position, state.open))
                }
                // The spine colour shows while the cover loads, and stays if there is none.
                .background(spineColor),
            contentAlignment = Alignment.Center,
        ) {
            val path = book.coverPath
            if (path != null) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.bookcase.spineText,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

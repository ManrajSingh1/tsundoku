package io.github.manrajsingh1.tsundoku.feature.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.ui.theme.bookcase

/** How far a selected spine rises off the plank. */
internal val SpineLift = 16.dp

/**
 * Spines grow with the system text size, but only by half as much again: beyond that a
 * shelf would show two or three books. The title is cut short on the spine instead, and
 * read in full by a screen reader.
 */
@Composable
internal fun spineScale(): Float = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)

/**
 * One book, spine out. Drawn in a single pass, text included, so a shelf of a hundred books
 * costs a hundred draw calls and no more.
 *
 * @param selected null outside edit mode, where a spine is not a thing that can be selected.
 * @param label everything a screen reader says for this book.
 * @param lifted true while a finger sliding along the shelf is on this book.
 */
@Composable
internal fun Spine(
    book: Book,
    label: String,
    clickLabel: String,
    selected: Boolean?,
    accessibilityActions: List<CustomAccessibilityAction>,
    textMeasurer: TextMeasurer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lifted: () -> Boolean = { false },
) {
    val scale = spineScale()
    val density = LocalDensity.current
    // Read through a derived state, so a finger running along the shelf recomposes only the
    // spine it reaches and the one it leaves.
    val isLifted by remember(lifted) { derivedStateOf(lifted) }
    val lift by animateDpAsState(
        targetValue = if (selected == true || isLifted) SpineLift else 0.dp,
        // A little bounce: the book hops up under the finger and settles back behind it.
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "spine lift",
    )
    val spineColor = Color(book.spineColor)
    val textColor = MaterialTheme.bookcase.spineText
    val outline = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(2.dp)

    // Sizes in dp turned into sp by hand, so the text grows with the spine and not twice over.
    val styles = remember(scale, density) {
        with(density) {
            SpineTextStyles(
                title = TextStyle(fontSize = (11.dp * scale).toSp(), fontWeight = FontWeight.Medium),
                volume = TextStyle(fontSize = (10.dp * scale).toSp(), fontWeight = FontWeight.Bold),
                tag = TextStyle(fontSize = (7.dp * scale).toSp(), fontWeight = FontWeight.Medium),
            )
        }
    }
    val volume = if (book.format == BookFormat.CBZ) book.volume?.let(::volumeNumber) else null

    Box(
        modifier = modifier
            .offset { IntOffset(0, -lift.roundToPx()) }
            .size(
                width = (SpineMetrics.width(book.format, book.pageCount) * scale).dp,
                height = (SpineMetrics.height(book.format, book.pageCount) * scale).dp,
            )
            // Selection is a lift and an outline, never a colour change alone.
            .then(if (selected == true) Modifier.border(2.dp, outline, shape) else Modifier)
            .clip(shape)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                if (selected != null) this.selected = selected
                if (accessibilityActions.isNotEmpty()) customActions = accessibilityActions
            }
            .drawWithCache {
                val width = size.width
                val height = size.height
                val edge = 7.dp.toPx() * scale
                val gap = 5.dp.toPx() * scale
                val rule = 1.dp.toPx()
                val across = Constraints(maxWidth = (width - 2.dp.toPx()).toInt().coerceAtLeast(0))

                val tagLayout = textMeasurer.measure(
                    text = book.format.name,
                    style = styles.tag,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    maxLines = 1,
                    constraints = across,
                )
                val volumeLayout = volume?.let {
                    textMeasurer.measure(
                        text = it,
                        style = styles.volume,
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                        maxLines = 1,
                        constraints = across,
                    )
                }
                val volumeTop = edge + rule + gap
                val titleTop = volumeTop + (volumeLayout?.let { it.size.height + gap } ?: 0f)
                val tagTop = height - edge - tagLayout.size.height
                val titleLength = (tagTop - gap - titleTop).toInt().coerceAtLeast(0)
                val titleLayout = textMeasurer.measure(
                    text = book.title,
                    style = styles.title,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = titleLength),
                )
                // A little light down the left and shade down the right, so it reads as round.
                val band = width * 0.25f
                val light = Brush.horizontalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    startX = 0f,
                    endX = band,
                )
                val shade = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.20f)),
                    startX = width - band,
                    endX = width,
                )

                onDrawBehind {
                    drawRect(spineColor)
                    drawRect(brush = light, size = Size(band, height))
                    drawRect(brush = shade, topLeft = Offset(width - band, 0f), size = Size(band, height))
                    drawRect(
                        color = textColor,
                        topLeft = Offset(width * 0.2f, edge),
                        size = Size(width * 0.6f, rule),
                        alpha = 0.7f,
                    )
                    if (volumeLayout != null) {
                        drawText(
                            textLayoutResult = volumeLayout,
                            color = textColor,
                            topLeft = Offset((width - volumeLayout.size.width) / 2f, volumeTop),
                        )
                    }
                    // The title runs down the spine, to be read with the head tilted right.
                    withTransform({
                        translate(left = (width + titleLayout.size.height) / 2f, top = titleTop)
                        rotate(degrees = 90f, pivot = Offset.Zero)
                    }) {
                        drawText(textLayoutResult = titleLayout, color = textColor)
                    }
                    drawText(
                        textLayoutResult = tagLayout,
                        color = textColor,
                        topLeft = Offset((width - tagLayout.size.width) / 2f, tagTop),
                    )
                }
            },
    )
}

private class SpineTextStyles(
    val title: TextStyle,
    val volume: TextStyle,
    val tag: TextStyle,
)

package io.github.manrajsingh1.tsundoku.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Edit mode's controls, at the bottom of the screen where a thumb reaches them. Spines are
 * too narrow to carry buttons of their own, so everything that can be done to the selected
 * book is done from here.
 */
@Composable
internal fun EditBar(
    selected: BookPlace?,
    shelfCount: Int,
    onMove: (delta: Int) -> Unit,
    onNextShelf: () -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    if (selected == null) {
                        Text(
                            text = stringResource(R.string.feature_library_edit_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = selected.book.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.feature_library_position,
                                selected.index + 1,
                                selected.shelf.books.size,
                                selected.shelf.shelf.name,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onDone) { Text(stringResource(R.string.feature_library_done)) }
            }
            // Always present, so the shelves do not jump when a book is selected.
            val canMoveLeft = selected != null && selected.index > 0
            val canMoveRight = selected != null && selected.index < selected.shelf.books.lastIndex
            val canChangeShelf = selected != null && shelfCount > 1
            val moveLeft: @Composable RowScope.(Boolean) -> Unit = { beside ->
                EditAction(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    label = stringResource(R.string.feature_library_move_left),
                    enabled = canMoveLeft,
                    iconBesideLabel = beside,
                    onClick = { onMove(-1) },
                )
            }
            val moveRight: @Composable RowScope.(Boolean) -> Unit = { beside ->
                EditAction(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    label = stringResource(R.string.feature_library_move_right),
                    enabled = canMoveRight,
                    iconBesideLabel = beside,
                    onClick = { onMove(1) },
                )
            }
            val nextShelf: @Composable RowScope.(Boolean) -> Unit = { beside ->
                EditAction(
                    icon = Icons.Filled.KeyboardArrowDown,
                    label = stringResource(R.string.feature_library_next_shelf),
                    enabled = canChangeShelf,
                    iconBesideLabel = beside,
                    onClick = onNextShelf,
                )
            }
            val remove: @Composable RowScope.(Boolean) -> Unit = { beside ->
                EditAction(
                    icon = Icons.Filled.Close,
                    label = stringResource(R.string.feature_library_remove),
                    enabled = selected != null,
                    iconBesideLabel = beside,
                    onClick = onRemove,
                )
            }
            if (LocalDensity.current.fontScale > LARGE_TEXT_SCALE) {
                // Four labels side by side would break mid-word at large text sizes, so
                // they go two to a row with the icon beside each.
                ActionRow {
                    moveLeft(true)
                    moveRight(true)
                }
                ActionRow {
                    nextShelf(true)
                    remove(true)
                }
            } else {
                ActionRow {
                    moveLeft(false)
                    moveRight(false)
                    nextShelf(false)
                    remove(false)
                }
            }
        }
    }
}

private const val LARGE_TEXT_SCALE = 1.3f

@Composable
private fun ActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun RowScope.EditAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    iconBesideLabel: Boolean,
    onClick: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    val modifier = Modifier
        .weight(1f)
        .heightIn(min = 56.dp)
        .clip(RoundedCornerShape(12.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 2.dp, vertical = 6.dp)
    if (iconBesideLabel) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

package io.github.manrajsingh1.tsundoku.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.manrajsingh1.tsundoku.core.model.AppPreferences
import io.github.manrajsingh1.tsundoku.core.model.PageTheme
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.ThemeMode

/**
 * Every setting on one scrolling page, in the order they are met in the app: how it looks,
 * the library, reading. Each takes effect as it is changed; there is nothing to save.
 *
 * @param canSetThemeMode false where the app can only follow the system (before Android 12).
 * @param canUseDynamicColor false where there are no wallpaper colours to take.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    preferences: AppPreferences,
    version: String,
    canSetThemeMode: Boolean,
    canUseDynamicColor: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onFlatCarousel: (Boolean) -> Unit,
    onComicsRightToLeft: (Boolean) -> Unit,
    onEpubPageTheme: (PageTheme) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.feature_settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.feature_settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // On a tablet the rows stop at a readable width instead of running edge to edge.
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(bottom = 24.dp)) {
                if (canSetThemeMode || canUseDynamicColor) {
                    SectionTitle(stringResource(R.string.feature_settings_appearance))
                }
                if (canSetThemeMode) {
                    ChoiceGroup(
                        title = stringResource(R.string.feature_settings_theme),
                        options = listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.feature_settings_theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.feature_settings_theme_light),
                            ThemeMode.DARK to stringResource(R.string.feature_settings_theme_dark),
                        ),
                        selected = preferences.themeMode,
                        onSelect = onThemeMode,
                    )
                }
                if (canUseDynamicColor) {
                    SwitchRow(
                        title = stringResource(R.string.feature_settings_dynamic_color),
                        body = stringResource(R.string.feature_settings_dynamic_color_body),
                        checked = preferences.dynamicColor,
                        onChange = onDynamicColor,
                    )
                }

                SectionTitle(stringResource(R.string.feature_settings_library))
                SwitchRow(
                    title = stringResource(R.string.feature_settings_flat_carousel),
                    body = stringResource(R.string.feature_settings_flat_carousel_body),
                    checked = preferences.flatCarousel,
                    onChange = onFlatCarousel,
                )

                SectionTitle(stringResource(R.string.feature_settings_reading))
                SwitchRow(
                    title = stringResource(R.string.feature_settings_comics_rtl),
                    body = stringResource(R.string.feature_settings_comics_rtl_body),
                    checked = preferences.comicReadingDirection == ReadingDirection.RIGHT_TO_LEFT,
                    onChange = onComicsRightToLeft,
                )
                ChoiceGroup(
                    title = stringResource(R.string.feature_settings_page_theme),
                    options = listOf(
                        PageTheme.SYSTEM to stringResource(R.string.feature_settings_page_theme_system),
                        PageTheme.LIGHT to stringResource(R.string.feature_settings_page_theme_light),
                        PageTheme.SEPIA to stringResource(R.string.feature_settings_page_theme_sepia),
                        PageTheme.DARK to stringResource(R.string.feature_settings_page_theme_dark),
                    ),
                    selected = preferences.epubPageTheme,
                    onSelect = onEpubPageTheme,
                )

                SectionTitle(stringResource(R.string.feature_settings_about))
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.feature_settings_version, version),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.feature_settings_privacy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** A whole row that is the switch: tap anywhere on it, and a screen reader hears one thing. */
@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** One choice from a few, all in view: fewer taps than a menu and nothing hidden. */
@Composable
private fun <T> ChoiceGroup(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp),
    )
    Column(Modifier.selectableGroup()) {
        for ((value, label) in options) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) })
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

package io.github.manrajsingh1.tsundoku.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.manrajsingh1.tsundoku.core.data.SettingsRepository
import io.github.manrajsingh1.tsundoku.core.model.AppPreferences
import io.github.manrajsingh1.tsundoku.core.model.PageTheme
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    // Starts from what is stored, not from the defaults, so no switch is seen to flip as
    // the screen opens.
    val preferences: StateFlow<AppPreferences> = settings.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = settings.preferencesNow(),
    )

    /** False before Android 12, where the app can only follow the system's theme. */
    val canSetThemeMode: Boolean = SettingsRepository.canSetThemeMode

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setFlatCarousel(enabled: Boolean) {
        viewModelScope.launch { settings.setFlatCarousel(enabled) }
    }

    fun setComicsRightToLeft(rightToLeft: Boolean) {
        val direction = if (rightToLeft) ReadingDirection.RIGHT_TO_LEFT else ReadingDirection.LEFT_TO_RIGHT
        viewModelScope.launch { settings.setComicReadingDirection(direction) }
    }

    fun setEpubPageTheme(theme: PageTheme) {
        viewModelScope.launch { settings.setEpubPageTheme(theme) }
    }
}

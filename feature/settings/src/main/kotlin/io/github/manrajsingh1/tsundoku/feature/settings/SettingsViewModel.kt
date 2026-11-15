package io.github.manrajsingh1.tsundoku.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.manrajsingh1.tsundoku.core.data.SettingsRepository
import io.github.manrajsingh1.tsundoku.core.data.backup.BackupRepository
import io.github.manrajsingh1.tsundoku.core.data.backup.RestoreResult
import io.github.manrajsingh1.tsundoku.core.model.AppPreferences
import io.github.manrajsingh1.tsundoku.core.model.ColourTheme
import io.github.manrajsingh1.tsundoku.core.model.PageTheme
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Something to tell the user once, after a backup or a restore. */
sealed interface SettingsMessage {
    data class BackupSaved(val shelves: Int, val books: Int) : SettingsMessage
    data object BackupNotSaved : SettingsMessage

    /** @param waiting books the backup knows that are not in the library yet. */
    data class Restored(val placed: Int, val waiting: Int) : SettingsMessage
    data object NotABackup : SettingsMessage
    data object BackupUnreadable : SettingsMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val backups: BackupRepository,
) : ViewModel() {

    private val messageChannel = Channel<SettingsMessage>(Channel.BUFFERED)

    /** What a backup or a restore came to, to be said once. */
    val messages: Flow<SettingsMessage> = messageChannel.receiveAsFlow()

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

    fun setColourTheme(theme: ColourTheme) {
        viewModelScope.launch { settings.setColourTheme(theme) }
    }

    /** Writes a backup to the file the user chose. */
    fun saveBackup(uri: Uri) {
        viewModelScope.launch {
            val saved = backups.save(uri)
            messageChannel.send(
                if (saved == null) SettingsMessage.BackupNotSaved else SettingsMessage.BackupSaved(saved.shelves, saved.books),
            )
        }
    }

    /** Makes the library match the backup in the file the user chose. */
    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            messageChannel.send(
                when (val result = backups.restore(uri)) {
                    is RestoreResult.Restored -> SettingsMessage.Restored(result.placed, result.waiting)
                    RestoreResult.NotABackup -> SettingsMessage.NotABackup
                    RestoreResult.Unreadable -> SettingsMessage.BackupUnreadable
                },
            )
        }
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

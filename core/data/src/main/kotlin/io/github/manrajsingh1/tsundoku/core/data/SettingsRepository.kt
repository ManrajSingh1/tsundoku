package io.github.manrajsingh1.tsundoku.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The user's preferences. Small, so they live in DataStore, not the database. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * How a comic reads when its file does not say. Right to left unless the user changes
     * it: the app is built around manga, which is read that way.
     */
    val comicReadingDirection: Flow<ReadingDirection> = context.settingsStore.data.map { preferences ->
        when (preferences[COMIC_DIRECTION]) {
            ReadingDirection.LEFT_TO_RIGHT.name -> ReadingDirection.LEFT_TO_RIGHT
            else -> ReadingDirection.RIGHT_TO_LEFT
        }
    }

    suspend fun setComicReadingDirection(direction: ReadingDirection) {
        require(direction != ReadingDirection.AUTO) { "The default itself cannot be 'follow the default'." }
        context.settingsStore.edit { it[COMIC_DIRECTION] = direction.name }
    }

    private companion object {
        val COMIC_DIRECTION = stringPreferencesKey("comic_reading_direction")
    }
}

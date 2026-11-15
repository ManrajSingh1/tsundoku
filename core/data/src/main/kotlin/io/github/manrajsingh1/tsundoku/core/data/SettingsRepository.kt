package io.github.manrajsingh1.tsundoku.core.data

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.manrajsingh1.tsundoku.core.model.AppPreferences
import io.github.manrajsingh1.tsundoku.core.model.ColourTheme
import io.github.manrajsingh1.tsundoku.core.model.PageTheme
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

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

    /** Everything chosen in Settings, as one value that changes when any of it does. */
    val preferences: Flow<AppPreferences> = context.settingsStore.data.map { stored ->
        AppPreferences(
            themeMode = enumOr(stored[THEME_MODE], ThemeMode.SYSTEM),
            // Before there were themes to choose from there was one switch, for wallpaper
            // colours. A phone that had it on keeps them.
            colourTheme = enumOr(
                stored[COLOUR_THEME],
                if (stored[DYNAMIC_COLOR] == true) ColourTheme.WALLPAPER else ColourTheme.TSUNDOKU,
            ),
            flatCarousel = stored[FLAT_CAROUSEL] ?: false,
            comicReadingDirection = when (stored[COMIC_DIRECTION]) {
                ReadingDirection.LEFT_TO_RIGHT.name -> ReadingDirection.LEFT_TO_RIGHT
                else -> ReadingDirection.RIGHT_TO_LEFT
            },
            epubPageTheme = enumOr(stored[EPUB_PAGE_THEME], PageTheme.SYSTEM),
        )
    }.distinctUntilChanged()

    /**
     * The preferences as they are right now, for the first frame of a screen: starting in
     * the app's own colours and switching to the wallpaper's a moment later would flash.
     * The file is a few hundred bytes and is read once.
     */
    fun preferencesNow(): AppPreferences = runBlocking { preferences.first() }

    /**
     * Light, dark, or whichever the system is in. Android itself remembers this for the app
     * and applies it to every screen before it is drawn, so there is nothing to do at
     * start-up. Before Android 12 there is no such switch and the app follows the system.
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[THEME_MODE] = mode.name }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
                when (mode) {
                    // "Auto" here means no choice of the app's own: follow the system.
                    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                },
            )
        }
    }

    suspend fun setColourTheme(theme: ColourTheme) {
        context.settingsStore.edit { it[COLOUR_THEME] = theme.name }
    }

    suspend fun setFlatCarousel(enabled: Boolean) {
        context.settingsStore.edit { it[FLAT_CAROUSEL] = enabled }
    }

    suspend fun setEpubPageTheme(theme: PageTheme) {
        context.settingsStore.edit { it[EPUB_PAGE_THEME] = theme.name }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    /** The text size in EPUBs, where 1.0 is the size the book asks for. */
    val epubFontSize: Flow<Double> = context.settingsStore.data.map { preferences ->
        (preferences[EPUB_FONT_SIZE] ?: 1.0).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
    }

    suspend fun setEpubFontSize(size: Double) {
        context.settingsStore.edit { it[EPUB_FONT_SIZE] = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE) }
    }

    companion object {
        const val MIN_FONT_SIZE = 0.6
        const val MAX_FONT_SIZE = 3.0
        private val EPUB_FONT_SIZE = doublePreferencesKey("epub_font_size")
        private val COMIC_DIRECTION = stringPreferencesKey("comic_reading_direction")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val COLOUR_THEME = stringPreferencesKey("colour_theme")
        private val FLAT_CAROUSEL = booleanPreferencesKey("flat_carousel")
        private val EPUB_PAGE_THEME = stringPreferencesKey("epub_page_theme")

        /** True where the app can be made light or dark by itself: Android 12 and later. */
        val canSetThemeMode: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}

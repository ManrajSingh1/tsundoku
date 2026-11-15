package io.github.manrajsingh1.tsundoku.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.manrajsingh1.tsundoku.core.model.AppPreferences
import io.github.manrajsingh1.tsundoku.core.model.ColourTheme
import io.github.manrajsingh1.tsundoku.core.model.PageTheme
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private val settings = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())

    /** Settings are one file for the whole process, so each test puts it back as it found it. */
    @After
    fun restoreDefaults() = runBlocking {
        settings.setThemeMode(ThemeMode.SYSTEM)
        settings.setColourTheme(ColourTheme.TSUNDOKU)
        settings.setFlatCarousel(false)
        settings.setComicReadingDirection(ReadingDirection.RIGHT_TO_LEFT)
        settings.setEpubPageTheme(PageTheme.SYSTEM)
    }

    @Test
    fun aFreshInstallHasTheDefaults() = runBlocking {
        restoreDefaults()

        assertEquals(AppPreferences(), settings.preferences.first())
        assertEquals(AppPreferences(), settings.preferencesNow())
    }

    @Test
    fun everyChoiceIsRememberedAndReadBackTogether() = runBlocking {
        // Choosing a theme also asks Android to apply it; that must not fail.
        settings.setThemeMode(ThemeMode.DARK)
        settings.setColourTheme(ColourTheme.NORD)
        settings.setFlatCarousel(true)
        settings.setComicReadingDirection(ReadingDirection.LEFT_TO_RIGHT)
        settings.setEpubPageTheme(PageTheme.SEPIA)

        assertEquals(
            AppPreferences(
                themeMode = ThemeMode.DARK,
                colourTheme = ColourTheme.NORD,
                flatCarousel = true,
                comicReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
                epubPageTheme = PageTheme.SEPIA,
            ),
            settings.preferences.first(),
        )
        // The older, single-purpose reading of the same choice agrees.
        assertEquals(ReadingDirection.LEFT_TO_RIGHT, settings.comicReadingDirection.first())
    }
}

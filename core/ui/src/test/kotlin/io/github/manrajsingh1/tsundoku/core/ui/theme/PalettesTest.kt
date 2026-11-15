package io.github.manrajsingh1.tsundoku.core.ui.theme

import androidx.compose.ui.graphics.Color
import io.github.manrajsingh1.tsundoku.core.model.ColourTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {

    private val named = ColourTheme.entries - setOf(ColourTheme.TSUNDOKU, ColourTheme.WALLPAPER)

    /** Readable text, less the hair's breadth that rounding a colour to 8 bits can cost. */
    private val readable = 4.45f

    private fun eachSide(check: (name: String, palette: Palette, dark: Boolean) -> Unit) {
        for (theme in named) {
            for (dark in listOf(false, true)) {
                check("$theme ${if (dark) "dark" else "light"}", requireNotNull(paletteFor(theme, dark)), dark)
            }
        }
    }

    @Test
    fun everyNamedThemeHasALightAndADarkSide() {
        for (theme in named) {
            val light = paletteFor(theme, dark = false)
            val dark = paletteFor(theme, dark = true)
            assertNotNull(theme.name, light)
            assertNotNull(theme.name, dark)
            assertTrue("$theme light is light", Color(light!!.background).let { contrast(it, Color.Black) > contrast(it, Color.White) })
            assertTrue("$theme dark is dark", Color(dark!!.background).let { contrast(it, Color.White) > contrast(it, Color.Black) })
        }
    }

    @Test
    fun theAppsOwnColoursAndTheWallpapersAreNotPalettes() {
        assertNull(paletteFor(ColourTheme.TSUNDOKU, dark = true))
        assertNull(paletteFor(ColourTheme.WALLPAPER, dark = false))
        assertNull(pageColours(ColourTheme.TSUNDOKU, darkTheme = false))
    }

    @Test
    fun textAndAccentsCanBeReadOnTheBackgroundInEveryTheme() = eachSide { name, palette, dark ->
        val scheme = palette.toColorScheme(dark)
        val background = scheme.background
        assertTrue("$name text", contrast(scheme.onSurface, background) >= readable)
        assertTrue("$name quiet text", contrast(scheme.onSurfaceVariant, background) >= readable)
        assertTrue("$name primary", contrast(scheme.primary, background) >= readable)
        assertTrue("$name secondary", contrast(scheme.secondary, background) >= readable)
        assertTrue("$name tertiary", contrast(scheme.tertiary, background) >= readable)
        assertTrue("$name error", contrast(scheme.error, background) >= readable)
        assertTrue("$name outline", contrast(scheme.outline, background) >= 2.95f)
    }

    @Test
    fun whatSitsOnAnAccentCanBeReadOnIt() = eachSide { name, palette, dark ->
        val scheme = palette.toColorScheme(dark)
        assertTrue("$name on primary", contrast(scheme.onPrimary, scheme.primary) >= readable)
        assertTrue("$name on secondary", contrast(scheme.onSecondary, scheme.secondary) >= readable)
        assertTrue("$name on tertiary", contrast(scheme.onTertiary, scheme.tertiary) >= readable)
        assertTrue("$name on a selected row", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 3f)
    }

    @Test
    fun aColourAlreadyReadableIsLeftExactlyAsPublished() {
        val mauve = Color(0xFFCBA6F7)
        val base = Color(0xFF1E1E2E)
        assertEquals(mauve, legible(mauve, against = base, towards = Color.White, ratio = 4.5f))
    }

    @Test
    fun aFaintColourIsMovedOnlyAsFarAsItNeeds() {
        val background = Color(0xFFFDF6E3)
        val text = Color(0xFF5C6A72)
        val green = Color(0xFF8DA101)
        val fixed = legible(green, against = background, towards = text, ratio = 4.5f)
        assertTrue(contrast(fixed, background) >= readable)
        // Still nearer the green it started as than the text it was moved towards.
        assertTrue(contrast(fixed, background) < contrast(text, background))
    }

    @Test
    fun aThemesPagesTakeItsBackgroundAndText() {
        val colours = pageColours(ColourTheme.NORD, darkTheme = true)
        assertEquals(0xFF2E3440.toInt() to 0xFFECEFF4.toInt(), colours)
    }

    @Test
    fun aSwatchShowsTheBackgroundAndThreeAccents() {
        assertEquals(4, themeSwatch(ColourTheme.GRUVBOX, darkTheme = false).size)
        assertEquals(4, themeSwatch(ColourTheme.TSUNDOKU, darkTheme = true).size)
        assertTrue(themeSwatch(ColourTheme.WALLPAPER, darkTheme = true).isEmpty())
    }
}

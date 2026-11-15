package io.github.manrajsingh1.tsundoku.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.github.manrajsingh1.tsundoku.core.model.ColourTheme

/**
 * One side (light or dark) of a published colour theme, reduced to the ten colours the app
 * needs. Everything Material asks for is worked out from these in [toColorScheme].
 *
 * @param surface1 a surface raised one step from the background: cards, bars.
 * @param surface2 raised a step further.
 * @param muted secondary text.
 */
internal data class Palette(
    val background: Long,
    val surface1: Long,
    val surface2: Long,
    val outline: Long,
    val text: Long,
    val muted: Long,
    val primary: Long,
    val secondary: Long,
    val tertiary: Long,
    val error: Long,
)

/** The palette for a named theme, or null for the app's own colours and the wallpaper's. */
internal fun paletteFor(theme: ColourTheme, dark: Boolean): Palette? = when (theme) {
    ColourTheme.TSUNDOKU, ColourTheme.WALLPAPER -> null
    ColourTheme.CATPPUCCIN -> if (dark) CatppuccinMocha else CatppuccinLatte
    ColourTheme.DRACULA -> if (dark) Dracula else Alucard
    ColourTheme.EVERFOREST -> if (dark) EverforestDark else EverforestLight
    ColourTheme.GRUVBOX -> if (dark) GruvboxDark else GruvboxLight
    ColourTheme.NORD -> if (dark) NordDark else NordLight
    ColourTheme.ROSE_PINE -> if (dark) RosePine else RosePineDawn
    ColourTheme.SOLARIZED -> if (dark) SolarizedDark else SolarizedLight
    ColourTheme.TOKYO_NIGHT -> if (dark) TokyoNight else TokyoNightDay
}

// Each theme's own names for its colours are given beside them.

private val CatppuccinLatte = Palette(
    background = 0xFFEFF1F5, // base
    surface1 = 0xFFE6E9EF, // mantle
    surface2 = 0xFFCCD0DA, // surface0
    outline = 0xFF8C8FA1, // overlay1
    text = 0xFF4C4F69,
    muted = 0xFF6C6F85, // subtext0
    primary = 0xFF8839EF, // mauve
    secondary = 0xFF1E66F5, // blue
    tertiary = 0xFF179299, // teal
    error = 0xFFD20F39, // red
)

private val CatppuccinMocha = Palette(
    background = 0xFF1E1E2E, // base
    surface1 = 0xFF313244, // surface0
    surface2 = 0xFF45475A, // surface1
    outline = 0xFF7F849C, // overlay1
    text = 0xFFCDD6F4,
    muted = 0xFFA6ADC8, // subtext0
    primary = 0xFFCBA6F7, // mauve
    secondary = 0xFF89B4FA, // blue
    tertiary = 0xFF94E2D5, // teal
    error = 0xFFF38BA8, // red
)

private val Dracula = Palette(
    background = 0xFF282A36,
    surface1 = 0xFF343746,
    surface2 = 0xFF44475A, // current line
    outline = 0xFF6272A4, // comment
    text = 0xFFF8F8F2, // foreground
    muted = 0xFFBFC1CF,
    primary = 0xFFBD93F9, // purple
    secondary = 0xFFFF79C6, // pink
    tertiary = 0xFF8BE9FD, // cyan
    error = 0xFFFF5555, // red
)

/** Dracula's light companion. */
private val Alucard = Palette(
    background = 0xFFFFFBEB,
    surface1 = 0xFFF2EEDF,
    surface2 = 0xFFE2DED0,
    outline = 0xFF6C664B, // comment
    text = 0xFF1F1F1F, // foreground
    muted = 0xFF62615C,
    primary = 0xFF644AC9, // purple
    secondary = 0xFFA3144D, // pink
    tertiary = 0xFF036A96, // cyan
    error = 0xFFCB3A2A, // red
)

private val EverforestDark = Palette(
    background = 0xFF2D353B, // bg0
    surface1 = 0xFF343F44, // bg1
    surface2 = 0xFF3D484D, // bg2
    outline = 0xFF7A8478, // grey0
    text = 0xFFD3C6AA, // fg
    muted = 0xFF9DA9A0, // grey2
    primary = 0xFFA7C080, // green
    secondary = 0xFF7FBBB3, // blue
    tertiary = 0xFFDBBC7F, // yellow
    error = 0xFFE67E80, // red
)

private val EverforestLight = Palette(
    background = 0xFFFDF6E3, // bg0
    surface1 = 0xFFF4F0D9, // bg1
    surface2 = 0xFFE6E2CC, // bg3
    outline = 0xFF939F91, // grey1
    text = 0xFF5C6A72, // fg
    muted = 0xFF829181, // grey2
    primary = 0xFF8DA101, // green
    secondary = 0xFF3A94C5, // blue
    tertiary = 0xFFDFA000, // yellow
    error = 0xFFF85552, // red
)

private val GruvboxDark = Palette(
    background = 0xFF282828, // bg0
    surface1 = 0xFF3C3836, // bg1
    surface2 = 0xFF504945, // bg2
    outline = 0xFF928374, // gray
    text = 0xFFEBDBB2, // fg1
    muted = 0xFFBDAE93, // fg3
    primary = 0xFFFABD2F, // yellow
    secondary = 0xFF8EC07C, // aqua
    tertiary = 0xFF83A598, // blue
    error = 0xFFFB4934, // red
)

private val GruvboxLight = Palette(
    background = 0xFFFBF1C7, // bg0
    surface1 = 0xFFEBDBB2, // bg1
    surface2 = 0xFFD5C4A1, // bg2
    outline = 0xFF928374, // gray
    text = 0xFF3C3836, // fg1
    muted = 0xFF665C54, // fg3
    primary = 0xFFB57614, // yellow
    secondary = 0xFF427B58, // aqua
    tertiary = 0xFF076678, // blue
    error = 0xFF9D0006, // red
)

private val NordDark = Palette(
    background = 0xFF2E3440, // nord0
    surface1 = 0xFF3B4252, // nord1
    surface2 = 0xFF434C5E, // nord2
    outline = 0xFF4C566A, // nord3
    text = 0xFFECEFF4, // nord6
    muted = 0xFFD8DEE9, // nord4
    primary = 0xFF88C0D0, // nord8
    secondary = 0xFF81A1C1, // nord9
    tertiary = 0xFFA3BE8C, // nord14
    error = 0xFFBF616A, // nord11
)

private val NordLight = Palette(
    background = 0xFFECEFF4, // nord6
    surface1 = 0xFFE5E9F0, // nord5
    surface2 = 0xFFD8DEE9, // nord4
    outline = 0xFF4C566A, // nord3
    text = 0xFF2E3440, // nord0
    muted = 0xFF434C5E, // nord2
    primary = 0xFF5E81AC, // nord10
    secondary = 0xFF81A1C1, // nord9
    tertiary = 0xFF8FBCBB, // nord7
    error = 0xFFBF616A, // nord11
)

private val RosePine = Palette(
    background = 0xFF191724, // base
    surface1 = 0xFF1F1D2E, // surface
    surface2 = 0xFF26233A, // overlay
    outline = 0xFF6E6A86, // muted
    text = 0xFFE0DEF4,
    muted = 0xFF908CAA, // subtle
    primary = 0xFFC4A7E7, // iris
    secondary = 0xFFEBBCBA, // rose
    tertiary = 0xFF9CCFD8, // foam
    error = 0xFFEB6F92, // love
)

private val RosePineDawn = Palette(
    background = 0xFFFAF4ED, // base
    surface1 = 0xFFFFFAF3, // surface
    surface2 = 0xFFF2E9E1, // overlay
    outline = 0xFF9893A5, // muted
    text = 0xFF575279,
    muted = 0xFF797593, // subtle
    primary = 0xFF907AA9, // iris
    secondary = 0xFFD7827E, // rose
    tertiary = 0xFF56949F, // foam
    error = 0xFFB4637A, // love
)

private val SolarizedDark = Palette(
    background = 0xFF002B36, // base03
    surface1 = 0xFF073642, // base02
    surface2 = 0xFF0D4250,
    outline = 0xFF586E75, // base01
    text = 0xFF93A1A1, // base1
    muted = 0xFF839496, // base0
    primary = 0xFF268BD2, // blue
    secondary = 0xFF2AA198, // cyan
    tertiary = 0xFFB58900, // yellow
    error = 0xFFDC322F, // red
)

private val SolarizedLight = Palette(
    background = 0xFFFDF6E3, // base3
    surface1 = 0xFFEEE8D5, // base2
    surface2 = 0xFFE4DDC8,
    outline = 0xFF93A1A1, // base1
    text = 0xFF586E75, // base01
    muted = 0xFF657B83, // base00
    primary = 0xFF268BD2, // blue
    secondary = 0xFF2AA198, // cyan
    tertiary = 0xFFB58900, // yellow
    error = 0xFFDC322F, // red
)

private val TokyoNight = Palette(
    background = 0xFF1A1B26, // bg
    surface1 = 0xFF24283B,
    surface2 = 0xFF292E42, // bg_highlight
    outline = 0xFF565F89, // comment
    text = 0xFFC0CAF5, // fg
    muted = 0xFFA9B1D6, // fg_dark
    primary = 0xFF7AA2F7, // blue
    secondary = 0xFFBB9AF7, // magenta
    tertiary = 0xFF7DCFFF, // cyan
    error = 0xFFF7768E, // red
)

private val TokyoNightDay = Palette(
    background = 0xFFE1E2E7, // bg
    surface1 = 0xFFD0D5E3,
    surface2 = 0xFFC4C8DA, // bg_highlight
    outline = 0xFF848CB5, // comment
    text = 0xFF3760BF, // fg
    muted = 0xFF6172B0, // fg_dark
    primary = 0xFF2E7DE9, // blue
    secondary = 0xFF9854F1, // magenta
    tertiary = 0xFF007197, // cyan
    error = 0xFFF52A65, // red
)

/** The contrast ratio between two opaque colours, from 1 (the same) to 21 (black on white). */
internal fun contrast(a: Color, b: Color): Float {
    val first = a.luminance() + 0.05f
    val second = b.luminance() + 0.05f
    return if (first > second) first / second else second / first
}

/**
 * [colour], moved towards [towards] only as far as it takes to reach [ratio] against
 * [against]. Themes made for code editors use accents as highlights; an app also uses them
 * as text on the background, where some published shades are too faint to read.
 */
internal fun legible(colour: Color, against: Color, towards: Color, ratio: Float): Color {
    var step = 0
    var candidate = colour
    while (contrast(candidate, against) < ratio && step < 20) {
        step++
        candidate = lerp(colour, towards, step / 20f)
    }
    return candidate
}

/** Every colour Material asks for, worked out from a palette's ten. */
internal fun Palette.toColorScheme(dark: Boolean): ColorScheme {
    val background = Color(this.background)
    val text = Color(this.text)
    val raised = Color(surface1)
    val raisedMore = Color(surface2)

    fun accent(value: Long) = legible(Color(value), background, text, 4.5f)

    /** Whichever of the background and the text shows up better on [fill]. */
    fun on(fill: Color) = if (contrast(background, fill) >= contrast(text, fill)) background else text

    /** A quiet wash of [fill], for chips and selected rows, that text still reads on. */
    fun container(fill: Color) = lerp(background, fill, 0.24f)

    val primary = accent(this.primary)
    val secondary = accent(this.secondary)
    val tertiary = accent(this.tertiary)
    val error = accent(this.error)
    val outline = legible(Color(this.outline), background, text, 3f)

    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary,
        onPrimary = on(primary),
        primaryContainer = container(primary),
        onPrimaryContainer = text,
        inversePrimary = container(primary),
        secondary = secondary,
        onSecondary = on(secondary),
        secondaryContainer = container(secondary),
        onSecondaryContainer = text,
        tertiary = tertiary,
        onTertiary = on(tertiary),
        tertiaryContainer = container(tertiary),
        onTertiaryContainer = text,
        error = error,
        onError = on(error),
        errorContainer = container(error),
        onErrorContainer = text,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = raisedMore,
        onSurfaceVariant = legible(Color(muted), background, text, 4.5f),
        surfaceTint = primary,
        inverseSurface = text,
        inverseOnSurface = background,
        outline = outline,
        outlineVariant = lerp(background, outline, 0.5f),
        scrim = Color.Black,
        surfaceBright = raisedMore,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = lerp(background, raised, 0.4f),
        surfaceContainer = lerp(background, raised, 0.7f),
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raisedMore,
    )
}

/** Shelves built from a theme's own colours: a recessed back and a plank that stands out from it. */
internal fun Palette.toBookcase(): BookcaseColors {
    val background = Color(this.background)
    val text = Color(this.text)
    val dark = background.luminance() < 0.5f
    return BookcaseColors(
        shelfBack = Color(surface1),
        plankTop = lerp(background, text, 0.34f),
        plankFront = lerp(background, text, 0.22f),
        plankShadow = if (dark) Color(0x80000000) else Color(0x33000000),
        spineText = Color.White,
    )
}

package io.github.manrajsingh1.tsundoku.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

// The app's own palette: walnut shelves and lamplight. Used when the user has turned the
// system's wallpaper colours off, and on the few devices that do not offer them.
private val Walnut = Color(0xFF2B1D14)
private val WalnutLight = Color(0xFF4A3426)
private val Amber = Color(0xFFF2C14E)
private val AmberDeep = Color(0xFF7A5A00)
private val Cream = Color(0xFFF3E9DC)
private val Paper = Color(0xFFFFF8F0)

private val LightColors = lightColorScheme(
    primary = AmberDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDF9B),
    onPrimaryContainer = Color(0xFF261A00),
    inversePrimary = Amber,
    secondary = WalnutLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3DFCB),
    onSecondaryContainer = Walnut,
    tertiary = Color(0xFF5E6135),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3E6AE),
    onTertiaryContainer = Color(0xFF1B1D00),
    background = Paper,
    onBackground = Walnut,
    surface = Paper,
    onSurface = Walnut,
    surfaceVariant = Color(0xFFEFE0CF),
    onSurfaceVariant = Color(0xFF4F4539),
    surfaceTint = AmberDeep,
    inverseSurface = Color(0xFF362F28),
    inverseOnSurface = Color(0xFFFCEFE2),
    outline = Color(0xFF817567),
    outlineVariant = Color(0xFFD3C4B4),
    surfaceBright = Paper,
    surfaceDim = Color(0xFFEADFD2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF1E6),
    surfaceContainer = Color(0xFFF5EBDF),
    surfaceContainerHigh = Color(0xFFEFE5D9),
    surfaceContainerHighest = Color(0xFFE9DFD3),
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF402D00),
    primaryContainer = Color(0xFF5C4200),
    onPrimaryContainer = Color(0xFFFFDF9B),
    inversePrimary = AmberDeep,
    secondary = Color(0xFFD9C2AC),
    onSecondary = Walnut,
    secondaryContainer = Color(0xFF574335),
    onSecondaryContainer = Color(0xFFF6DDC7),
    tertiary = Color(0xFFC7CA94),
    onTertiary = Color(0xFF30330B),
    tertiaryContainer = Color(0xFF464920),
    onTertiaryContainer = Color(0xFFE3E6AE),
    background = Color(0xFF1A120C),
    onBackground = Cream,
    surface = Color(0xFF1A120C),
    onSurface = Cream,
    surfaceVariant = Color(0xFF4F4539),
    onSurfaceVariant = Color(0xFFD3C4B4),
    surfaceTint = Amber,
    inverseSurface = Cream,
    inverseOnSurface = Color(0xFF362F28),
    outline = Color(0xFF9C8E80),
    outlineVariant = Color(0xFF4F4539),
    surfaceBright = Color(0xFF43372E),
    surfaceDim = Color(0xFF1A120C),
    surfaceContainerLowest = Color(0xFF140D08),
    surfaceContainerLow = Color(0xFF221A13),
    surfaceContainer = Color(0xFF271E17),
    surfaceContainerHigh = Color(0xFF322821),
    surfaceContainerHighest = Color(0xFF3D332B),
)

/**
 * The colours of the bookcase itself, which Material's roles have no names for. They stay
 * wood whatever the rest of the theme does: the shelves are the app's identity.
 */
@Immutable
data class BookcaseColors(
    /** The back wall of a shelf, behind the spines. */
    val shelfBack: Color,
    val plankTop: Color,
    val plankFront: Color,
    val plankShadow: Color,
    /** Text on a spine. Every spine colour is darkened at import until this passes 4.5:1. */
    val spineText: Color,
)

private val LightBookcase = BookcaseColors(
    shelfBack = Color(0xFFEADBC8),
    plankTop = Color(0xFFB08968),
    plankFront = Color(0xFF8C6A4C),
    plankShadow = Color(0x33000000),
    spineText = Color.White,
)

private val DarkBookcase = BookcaseColors(
    shelfBack = Color(0xFF241810),
    plankTop = Color(0xFF6B4A33),
    plankFront = Color(0xFF4A3426),
    plankShadow = Color(0x80000000),
    spineText = Color.White,
)

private val LocalBookcaseColors = staticCompositionLocalOf { DarkBookcase }

/** The bookcase colours for the current theme: `MaterialTheme.bookcase.plankTop`. */
val MaterialTheme.bookcase: BookcaseColors
    @Composable
    @ReadOnlyComposable
    get() = LocalBookcaseColors.current

/**
 * The app's theme. Text uses the system font, so a font the user picks in One UI's settings
 * is respected everywhere; only the largest titles switch to the system serif, for the
 * bookish look.
 *
 * @param dynamicColor take the colours from the wallpaper. Off unless asked for: walnut and
 * amber are the default, and wallpaper colours are to be offered as a setting.
 */
@Composable
fun TsundokuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalBookcaseColors provides if (darkTheme) DarkBookcase else LightBookcase) {
        MaterialTheme(colorScheme = colorScheme, typography = TsundokuTypography, content = content)
    }
}

private val Base = Typography()

private val TsundokuTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = FontFamily.Serif),
    displayMedium = Base.displayMedium.copy(fontFamily = FontFamily.Serif),
    displaySmall = Base.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = Base.headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = Base.headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = Base.headlineSmall.copy(fontFamily = FontFamily.Serif),
)

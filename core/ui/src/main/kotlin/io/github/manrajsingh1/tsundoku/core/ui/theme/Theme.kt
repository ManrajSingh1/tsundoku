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
    secondary = WalnutLight,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Walnut,
    surface = Paper,
    onSurface = Walnut,
    surfaceVariant = Color(0xFFEFE0CF),
    onSurfaceVariant = Color(0xFF4F4539),
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF402D00),
    primaryContainer = Color(0xFF5C4200),
    onPrimaryContainer = Color(0xFFFFDF9B),
    secondary = Color(0xFFD9C2AC),
    onSecondary = Walnut,
    background = Color(0xFF1A120C),
    onBackground = Cream,
    surface = Color(0xFF1A120C),
    onSurface = Cream,
    surfaceVariant = Color(0xFF4F4539),
    onSurfaceVariant = Color(0xFFD3C4B4),
)

/**
 * The app's theme. Text uses the system font, so a font the user picks in One UI's settings
 * is respected everywhere; only the largest titles switch to the system serif, for the
 * bookish look.
 */
@Composable
fun TsundokuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
    MaterialTheme(colorScheme = colorScheme, typography = TsundokuTypography, content = content)
}

private val Base = Typography()

private val TsundokuTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = FontFamily.Serif),
    displayMedium = Base.displayMedium.copy(fontFamily = FontFamily.Serif),
    displaySmall = Base.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = Base.headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = Base.headlineMedium.copy(fontFamily = FontFamily.Serif),
)

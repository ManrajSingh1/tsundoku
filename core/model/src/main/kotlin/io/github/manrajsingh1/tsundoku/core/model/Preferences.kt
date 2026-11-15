package io.github.manrajsingh1.tsundoku.core.model

/** Whether the app is light or dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The colours the app wears. Each has a light and a dark side; which is shown is [ThemeMode]'s
 * business. [TSUNDOKU] is the app's own walnut and amber, and [WALLPAPER] takes its colours
 * from the phone's wallpaper (Android 12 and later; elsewhere it is the same as [TSUNDOKU]).
 */
enum class ColourTheme {
    TSUNDOKU,
    WALLPAPER,
    CATPPUCCIN,
    DRACULA,
    EVERFOREST,
    GRUVBOX,
    NORD,
    ROSE_PINE,
    SOLARIZED,
    TOKYO_NIGHT,
}

/** The colour of the page in an EPUB. [SYSTEM] follows the app, taking its colours
 * from the chosen [ColourTheme]. */
enum class PageTheme { SYSTEM, LIGHT, SEPIA, DARK }

/**
 * What the user has chosen in Settings. The defaults are the app as it is first installed.
 *
 * @param flatCarousel show an open shelf as flat, still covers instead of the 3D carousel.
 * @param comicReadingDirection how a comic reads when neither its file nor the user says.
 */
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colourTheme: ColourTheme = ColourTheme.TSUNDOKU,
    val flatCarousel: Boolean = false,
    val comicReadingDirection: ReadingDirection = ReadingDirection.RIGHT_TO_LEFT,
    val epubPageTheme: PageTheme = PageTheme.SYSTEM,
)

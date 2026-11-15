package io.github.manrajsingh1.tsundoku.core.model

/** Whether the app is light or dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The colour of the page in an EPUB. [SYSTEM] follows the app: light by day, dark by night. */
enum class PageTheme { SYSTEM, LIGHT, SEPIA, DARK }

/**
 * What the user has chosen in Settings. The defaults are the app as it is first installed.
 *
 * @param dynamicColor take colours from the wallpaper instead of the app's walnut and amber.
 * @param flatCarousel show an open shelf as flat, still covers instead of the 3D carousel.
 * @param comicReadingDirection how a comic reads when neither its file nor the user says.
 */
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val flatCarousel: Boolean = false,
    val comicReadingDirection: ReadingDirection = ReadingDirection.RIGHT_TO_LEFT,
    val epubPageTheme: PageTheme = PageTheme.SYSTEM,
)

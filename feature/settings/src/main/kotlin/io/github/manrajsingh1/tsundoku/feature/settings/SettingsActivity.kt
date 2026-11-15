package io.github.manrajsingh1.tsundoku.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.manrajsingh1.tsundoku.core.ui.theme.TsundokuTheme

/** The settings screen. Every choice is saved and takes effect as it is made. */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            TsundokuTheme(dynamicColor = preferences.dynamicColor) {
                SettingsScreen(
                    preferences = preferences,
                    version = version,
                    canSetThemeMode = viewModel.canSetThemeMode,
                    canUseDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onThemeMode = viewModel::setThemeMode,
                    onDynamicColor = viewModel::setDynamicColor,
                    onFlatCarousel = viewModel::setFlatCarousel,
                    onComicsRightToLeft = viewModel::setComicsRightToLeft,
                    onEpubPageTheme = viewModel::setEpubPageTheme,
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }
}

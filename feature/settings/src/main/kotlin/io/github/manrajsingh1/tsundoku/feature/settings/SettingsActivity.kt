package io.github.manrajsingh1.tsundoku.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.manrajsingh1.tsundoku.core.ui.theme.TsundokuTheme
import java.time.LocalDate

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
            // The system's own file pickers: the backup goes wherever the user keeps things.
            val saveBackup = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri ->
                if (uri != null) viewModel.saveBackup(uri)
            }
            val restoreBackup = rememberLauncherForActivityResult(OpenDocument()) { uri ->
                if (uri != null) viewModel.restoreBackup(uri)
            }
            TsundokuTheme(colourTheme = preferences.colourTheme) {
                SettingsScreen(
                    preferences = preferences,
                    version = version,
                    canSetThemeMode = viewModel.canSetThemeMode,
                    canUseWallpaperColours = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    messages = viewModel.messages,
                    onThemeMode = viewModel::setThemeMode,
                    onColourTheme = viewModel::setColourTheme,
                    onFlatCarousel = viewModel::setFlatCarousel,
                    onComicsRightToLeft = viewModel::setComicsRightToLeft,
                    onEpubPageTheme = viewModel::setEpubPageTheme,
                    onSaveBackup = { saveBackup.launch("tsundoku-backup-${LocalDate.now()}.json") },
                    // Some file apps do not know a .json file by its proper type.
                    onRestoreBackup = {
                        restoreBackup.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                    },
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }
}

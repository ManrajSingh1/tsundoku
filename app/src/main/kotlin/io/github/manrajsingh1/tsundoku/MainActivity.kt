package io.github.manrajsingh1.tsundoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import dagger.hilt.android.AndroidEntryPoint
import io.github.manrajsingh1.tsundoku.core.data.SettingsRepository
import io.github.manrajsingh1.tsundoku.core.ui.theme.TsundokuTheme
import io.github.manrajsingh1.tsundoku.feature.importing.ImportStatusBar
import io.github.manrajsingh1.tsundoku.feature.importing.ImportViewModel
import io.github.manrajsingh1.tsundoku.feature.importing.rememberImportPickers
import io.github.manrajsingh1.tsundoku.feature.library.LibraryRoute
import io.github.manrajsingh1.tsundoku.feature.library.LibraryViewModel
import io.github.manrajsingh1.tsundoku.feature.library.ReadingProgressViewModel
import io.github.manrajsingh1.tsundoku.feature.reader.ReaderActivity
import io.github.manrajsingh1.tsundoku.feature.settings.SettingsActivity
import javax.inject.Inject
import kotlinx.coroutines.flow.map

/**
 * The app's home. It only wires features together: the library draws the shelves,
 * the import feature supplies the pickers and the progress, and neither knows of the other.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val libraryViewModel: LibraryViewModel by viewModels()
    private val importViewModel: ImportViewModel by viewModels()
    private val progressViewModel: ReadingProgressViewModel by viewModels()

    @Inject
    lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val preferencesAtStart = settings.preferencesNow()
        setContent {
            val preferences by settings.preferences.collectAsStateWithLifecycle(initialValue = preferencesAtStart)
            TsundokuTheme(colourTheme = preferences.colourTheme) {
                // True while an unfolded foldable's hinge runs down the middle of the window.
                val hasVerticalFold by remember {
                    WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this).map { layout ->
                        layout.displayFeatures.any {
                            it is FoldingFeature && it.orientation == FoldingFeature.Orientation.VERTICAL
                        }
                    }
                }.collectAsStateWithLifecycle(initialValue = false)

                val importStatus by importViewModel.status.collectAsStateWithLifecycle()
                val progress by progressViewModel.progress.collectAsStateWithLifecycle()
                val pickers = rememberImportPickers(
                    onDocumentsPicked = importViewModel::onDocumentsPicked,
                    onFolderPicked = importViewModel::onFolderPicked,
                )
                LibraryRoute(
                    viewModel = libraryViewModel,
                    hasVerticalFold = hasVerticalFold,
                    onAddFiles = pickers.addFiles,
                    onAddFolder = pickers.addFolder,
                    onAddFilesToShelf = pickers.addFilesToShelf,
                    progress = progress,
                    // The library stays as it is underneath, so closing the book returns to
                    // the same shelf with the same book facing front.
                    onReadBook = { bookId, restart -> startActivity(ReaderActivity.intent(this, bookId, restart)) },
                    flatCarousel = preferences.flatCarousel,
                    onOpenSettings = { startActivity(SettingsActivity.intent(this)) },
                    importStatus = {
                        ImportStatusBar(status = importStatus, onDismiss = importViewModel::onDismissResult)
                    },
                )
            }
        }
    }
}

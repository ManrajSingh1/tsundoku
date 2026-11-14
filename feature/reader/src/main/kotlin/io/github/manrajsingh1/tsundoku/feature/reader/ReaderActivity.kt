package io.github.manrajsingh1.tsundoku.feature.reader

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.ui.theme.TsundokuTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

/**
 * Reads one book. Comics are drawn by the app's own pager; EPUBs and PDFs by Readium's
 * navigators, which are fragments. The bars over the page belong to the app in every case.
 *
 * The library stays underneath, untouched, so closing the book returns to the shelf and the
 * book it was opened from.
 */
@OptIn(ExperimentalReadiumApi::class)
@AndroidEntryPoint
class ReaderActivity : AppCompatActivity() {

    private val viewModel: ReaderViewModel by viewModels()
    private var navigatorJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        // Saved fragment state is deliberately dropped. A navigator cannot be restored before
        // its book has been opened again, which takes a moment; the page is rebuilt from the
        // saved reading position instead.
        super.onCreate(null)
        setContentView(R.layout.feature_reader_activity)

        val content = findViewById<ComposeView>(R.id.feature_reader_content)
        content.setContent {
            TsundokuTheme { ReaderContent(viewModel, onClose = ::finish, onPageTapped = ::onPageTapped) }
        }
        findViewById<ComposeView>(R.id.feature_reader_top_bar).setContent {
            TsundokuTheme { ReaderTopBar(viewModel, onClose = ::finish) }
        }
        findViewById<ComposeView>(R.id.feature_reader_bottom_bar).setContent {
            TsundokuTheme { ReaderBottomBar(viewModel) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        if (state is ReaderUiState.Ready && state.book.format != BookFormat.CBZ) {
                            // The navigator takes over; the loading view must not sit under it
                            // answering to screen readers.
                            content.visibility = View.GONE
                            attachNavigator(state)
                        }
                    }
                }
                launch { viewModel.controlsVisible.collect { showSystemBars(it) } }
            }
        }
    }

    /**
     * A tap on the middle of a page hides or shows the bars. With a screen reader exploring
     * by touch they stay, so the way out of the book is never hidden from it.
     */
    private fun onPageTapped() {
        val accessibility = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (accessibility?.isTouchExplorationEnabled == true) viewModel.showControls() else viewModel.toggleControls()
    }

    private fun showSystemBars(show: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (show) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun attachNavigator(ready: ReaderUiState.Ready) {
        if (supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) != null) return
        val start = viewModel.position?.let { saved ->
            runCatching { Locator.fromJSON(JSONObject(saved)) }.getOrNull()
        }
        when (ready.book.format) {
            BookFormat.EPUB -> attachEpub(ready, start)
            BookFormat.PDF -> attachPdf(ready, start)
            BookFormat.CBZ -> Unit
        }
    }

    private fun attachEpub(ready: ReaderUiState.Ready, start: Locator?) {
        val factory = EpubNavigatorFactory(ready.publication)
        supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
            initialLocator = start,
            initialPreferences = epubPreferences(viewModel.fontSize.value),
        )
        supportFragmentManager.beginTransaction()
            .replace(R.id.feature_reader_navigator, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            .commitNow()
        val navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment

        navigator.addInputListener(
            object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    val rightToLeft = navigator.settings.value.readingProgression.name == "RTL"
                    when (ReaderMath.tapZone(event.point.x, navigator.view?.width ?: 0, rightToLeft)) {
                        TapZone.NEXT -> navigator.goForward(animated = true)
                        TapZone.PREVIOUS -> navigator.goBackward(animated = true)
                        TapZone.CONTROLS -> onPageTapped()
                    }
                    return true
                }
            },
        )
        navigatorJob?.cancel()
        navigatorJob = lifecycleScope.launch {
            launch { follow(navigator.currentLocator) }
            launch { viewModel.fontSize.collect { navigator.submitPreferences(epubPreferences(it)) } }
        }
    }

    private fun attachPdf(ready: ReaderUiState.Ready, start: Locator?) {
        val factory = PdfNavigatorFactory(ready.publication, PdfiumEngineProvider())
        supportFragmentManager.fragmentFactory = factory.createFragmentFactory(initialLocator = start)
        supportFragmentManager.beginTransaction()
            .replace(R.id.feature_reader_navigator, PdfNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            .commitNow()
        val navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as PdfNavigatorFragment<*, *>

        // Pages turn by swiping, as in the engine comparison; a tap only brings up the bars.
        navigator.addInputListener(
            object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    onPageTapped()
                    return true
                }
            },
        )
        navigatorJob?.cancel()
        navigatorJob = lifecycleScope.launch { follow(navigator.currentLocator) }
    }

    private suspend fun follow(locators: StateFlow<Locator>) {
        locators.collect { locator ->
            viewModel.onLocator(
                locatorJson = locator.toJSON().toString(),
                positionNumber = locator.locations.position,
                totalProgression = locator.locations.totalProgression,
            )
        }
    }

    /** EPUB pages follow the system's light or dark theme, at the reader's chosen text size. */
    private fun epubPreferences(fontSize: Double): EpubPreferences {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return EpubPreferences(fontSize = fontSize, theme = if (night) Theme.DARK else Theme.LIGHT)
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_RESTART = "restart"
        private const val NAVIGATOR_TAG = "navigator"

        /** @param restart true to open at the first page, forgetting where the reader was. */
        fun intent(context: Context, bookId: Long, restart: Boolean): Intent =
            Intent(context, ReaderActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
                .putExtra(EXTRA_RESTART, restart)
    }
}

package io.github.manrajsingh1.tsundoku.spike

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.pdf.PdfDocument
import androidx.pdf.viewer.fragment.PdfViewerFragment
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Opens one PDF in either engine under comparison, with the same frame read-out as the
 * carousel spike, so scrolling in both can be judged with numbers and not only by eye.
 *
 * What the load times mean differs by engine, and the screen says which is which:
 * androidx.pdf reports when the document has loaded; Readium's PDFium adapter gives no such
 * signal, so its time stops when the viewer is attached, before the first page is drawn.
 */
@OptIn(ExperimentalReadiumApi::class)
class PdfSpikeActivity : AppCompatActivity() {

    private lateinit var frameStats: FrameStats
    private lateinit var status: TextView
    private var containerId = View.NO_ID

    private var document: Uri? = null
    private var loadLine = "Pick a PDF, then choose an engine."
    private var openStartedAt = 0L
    private var publication: Publication? = null

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            ticker.postDelayed(this, 500)
        }
    }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            document = picked
            loadLine = "Picked ${displayName(picked)}. Choose an engine."
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        // The viewer fragments are always built by hand, never restored from saved state.
        super.onCreate(null)
        frameStats = FrameStats(this)
        setContentView(buildLayout())

        // CI opens this screen with a sample file it pushed, because it cannot work a file picker.
        val requested = intent.getStringExtra(EXTRA_ENGINE)
        if (requested != null) {
            val sample = File(getExternalFilesDir(null), SAMPLE_NAME)
            if (sample.exists()) {
                document = Uri.fromFile(sample)
                status.post { if (requested == "readium") openWithReadium() else openWithAndroidx() }
            } else {
                loadLine = "No $SAMPLE_NAME in ${sample.parent}"
            }
        }
        render()
    }

    override fun onStart() {
        super.onStart()
        frameStats.start()
        ticker.post(tick)
    }

    override fun onStop() {
        ticker.removeCallbacks(tick)
        frameStats.stop()
        super.onStop()
    }

    override fun onDestroy() {
        publication?.close()
        publication = null
        super.onDestroy()
    }

    private fun buildLayout(): View {
        val pad = (12 * resources.displayMetrics.density).toInt()
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
        val pickRow = LinearLayout(this).apply {
            setPadding(pad, 0, pad, 0)
            addView(button("Pick PDF") { picker.launch(arrayOf("application/pdf")) })
            addView(button("Reset stats") { frameStats.reset() })
        }
        val engineRow = LinearLayout(this).apply {
            setPadding(pad, 0, pad, 0)
            addView(button("Open in androidx.pdf") { openWithAndroidx() })
            addView(button("Open in Readium") { openWithReadium() })
        }
        status = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val container = FrameLayout(this).apply { id = View.generateViewId() }
        containerId = container.id

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(pickRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(engineRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(container, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        return root
    }

    private fun openWithAndroidx() {
        val target = document ?: return
        beginOpen("androidx.pdf: loading")
        try {
            val fragment = TimedPdfViewerFragment()
            supportFragmentManager.beginTransaction()
                .replace(containerId, fragment, VIEWER_TAG)
                .commitNow()
            fragment.documentUri = target
        } catch (e: Exception) {
            fail("androidx.pdf", e)
        }
        render()
    }

    internal fun onAndroidxLoaded() {
        loadLine = "androidx.pdf: document loaded in ${elapsed()} ms"
        render()
    }

    internal fun onAndroidxFailed(error: Throwable) = fail("androidx.pdf", error)

    private fun openWithReadium() {
        val target = document ?: return
        beginOpen("Readium PDFium: opening")
        render()
        lifecycleScope.launch {
            try {
                val opened = openPublication(target)
                val parsedIn = elapsed()
                publication?.close()
                publication = opened
                val factory = PdfNavigatorFactory(opened, PdfiumEngineProvider())
                supportFragmentManager.fragmentFactory = factory.createFragmentFactory()
                supportFragmentManager.beginTransaction()
                    .replace(containerId, PdfNavigatorFragment::class.java, Bundle(), VIEWER_TAG)
                    .commitNow()
                loadLine = "Readium PDFium: parsed in $parsedIn ms, viewer attached in ${elapsed()} ms"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("Readium PDFium", e)
            }
            render()
        }
    }

    private suspend fun openPublication(target: Uri): Publication {
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(contentResolver, httpClient)
        val opener = PublicationOpener(
            publicationParser = DefaultPublicationParser(
                this,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = PdfiumDocumentFactory(this),
            ),
        )
        val url = target.toAbsoluteUrl() ?: error("Not an absolute URL: $target")
        val asset = assetRetriever.retrieve(url).getOrElse { error("Could not read the file: $it") }
        return opener.open(asset, allowUserInteraction = false).getOrElse { error("Could not parse the PDF: $it") }
    }

    private fun beginOpen(line: String) {
        loadLine = line
        openStartedAt = SystemClock.elapsedRealtime()
        frameStats.reset()
    }

    private fun elapsed(): Long = SystemClock.elapsedRealtime() - openStartedAt

    private fun fail(engine: String, error: Throwable) {
        Log.e(TAG, "$engine failed to open the document", error)
        loadLine = "$engine: FAILED ${error.javaClass.simpleName}: ${error.message}"
        render()
    }

    private fun render() {
        val s = frameStats.summary
        val runtime = Runtime.getRuntime()
        val javaMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576f
        val nativeMb = Debug.getNativeHeapAllocatedSize() / 1_048_576f
        status.text = String.format(
            Locale.US,
            "%s\n%.0f Hz  %d frames  p50 %.1f  p99 %.1f  worst %.1f ms\nlate %.1f%%  dropped %.1f%%  native %.0f MB  java %.0f MB",
            loadLine,
            s.refreshHz,
            s.frames,
            s.p50Ms,
            s.p99Ms,
            s.worstMs,
            s.latePercent,
            s.droppedPercent,
            nativeMb,
            javaMb,
        )
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: uri.toString()
        }
        return uri.lastPathSegment ?: uri.toString()
    }

    companion object {
        const val EXTRA_ENGINE = "engine"
        private const val SAMPLE_NAME = "sample.pdf"
        private const val VIEWER_TAG = "viewer"
        private const val TAG = "PdfSpike"
    }
}

/** androidx.pdf's viewer, reporting back when the document has loaded or failed. */
class TimedPdfViewerFragment : PdfViewerFragment() {

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        (activity as? PdfSpikeActivity)?.onAndroidxLoaded()
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        (activity as? PdfSpikeActivity)?.onAndroidxFailed(error)
    }
}

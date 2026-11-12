package io.github.manrajsingh1.tsundoku.spike

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class FrameSummary(
    val refreshHz: Float = 0f,
    val frames: Int = 0,
    val p50Ms: Float = 0f,
    val p90Ms: Float = 0f,
    val p99Ms: Float = 0f,
    val worstMs: Float = 0f,
    /** Share of frames that missed their deadline. */
    val latePercent: Float = 0f,
    /** Share of frames that took at least twice the frame budget: the ones a person sees as a stutter. */
    val droppedPercent: Float = 0f,
) {
    val budgetMs: Float get() = if (refreshHz > 0f) 1000f / refreshHz else 0f

    /** Proposed pass mark for the carousel spike. Tune after the first real-device run. */
    val passes: Boolean get() = frames >= 300 && droppedPercent < 1f && p99Ms < budgetMs * 1.5f
}

/**
 * Frame timing straight from the platform (Window.OnFrameMetricsAvailableListener), with no
 * library in between. The numbers are what the system measured for each frame of this window.
 */
class FrameStats(private val activity: Activity) {

    var summary by mutableStateOf(FrameSummary())
        private set

    private val lock = Any()
    private val durations = LongArray(CAPACITY)
    private var count = 0
    private var late = 0
    private var dropped = 0
    private var lastPublishMs = 0L
    private var lastPublishCount = 0

    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val budget = budgetNanos()
        val deadline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            metrics.getMetric(FrameMetrics.DEADLINE)
        } else {
            budget
        }
        var publish = false
        synchronized(lock) {
            durations[count % CAPACITY] = total
            count++
            if (total > deadline) late++
            if (total >= budget * 2) dropped++
            val now = SystemClock.uptimeMillis()
            // Publishing redraws the read-out, which is itself a frame. Asking for a few new
            // frames first stops the read-out from feeding itself when the screen is idle.
            publish = now - lastPublishMs >= PUBLISH_EVERY_MS && count - lastPublishCount >= 5
            if (publish) {
                lastPublishMs = now
                lastPublishCount = count
            }
        }
        if (publish) publishNow()
    }

    fun start() {
        if (thread != null) return
        val t = HandlerThread("frame-stats").also { it.start() }
        thread = t
        activity.window.addOnFrameMetricsAvailableListener(listener, Handler(t.looper))
    }

    fun stop() {
        val t = thread ?: return
        activity.window.removeOnFrameMetricsAvailableListener(listener)
        t.quitSafely()
        thread = null
    }

    fun reset() {
        synchronized(lock) {
            count = 0
            late = 0
            dropped = 0
            lastPublishCount = 0
        }
        publishNow()
    }

    fun publishNow() {
        val snapshot = snapshot()
        main.post { summary = snapshot }
    }

    private fun snapshot(): FrameSummary {
        var sorted = LongArray(0)
        var frames = 0
        var lateFrames = 0
        var droppedFrames = 0
        synchronized(lock) {
            frames = count
            lateFrames = late
            droppedFrames = dropped
            sorted = durations.copyOf(minOf(count, CAPACITY))
        }
        val hz = refreshRate()
        if (frames == 0) return FrameSummary(refreshHz = hz)
        sorted.sort()
        fun percentile(p: Float): Float = sorted[((sorted.size - 1) * p).toInt()] / 1_000_000f
        return FrameSummary(
            refreshHz = hz,
            frames = frames,
            p50Ms = percentile(0.50f),
            p90Ms = percentile(0.90f),
            p99Ms = percentile(0.99f),
            worstMs = sorted.last() / 1_000_000f,
            latePercent = 100f * lateFrames / frames,
            droppedPercent = 100f * droppedFrames / frames,
        )
    }

    private fun budgetNanos(): Long {
        val hz = refreshRate()
        return if (hz > 0f) (1_000_000_000f / hz).toLong() else 16_666_667L
    }

    @Suppress("DEPRECATION")
    private fun refreshRate(): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.refreshRate ?: 60f
        } else {
            activity.windowManager.defaultDisplay.refreshRate
        }

    private companion object {
        const val CAPACITY = 8192
        const val PUBLISH_EVERY_MS = 500L
    }
}

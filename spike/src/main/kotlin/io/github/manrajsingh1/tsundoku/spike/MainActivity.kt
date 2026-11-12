package io.github.manrajsingh1.tsundoku.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    private lateinit var frameStats: FrameStats

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        frameStats = FrameStats(this)
        setContent { SpikeApp(frameStats) }
    }

    override fun onStart() {
        super.onStart()
        frameStats.start()
    }

    override fun onStop() {
        frameStats.stop()
        super.onStop()
    }
}

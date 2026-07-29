package dev.jyotiraditya.dmt

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import dev.jyotiraditya.dmt.data.remote.youtubedl.YouTubeDLBridge
import javax.inject.Inject

private const val TAG = "DmtApp"

@HiltAndroidApp
class DmtApp : Application() {

    @Inject
    lateinit var youTubeDLBridge: YouTubeDLBridge

    override fun onCreate() {
        super.onCreate()
        // Initialize the yt-dlp Python bridge on app start
        // This installs yt-dlp pip package and starts Chaquopy Python runtime
        Thread {
            try {
                youTubeDLBridge.initialize(this)
                Log.i(TAG, "YouTubeDL bridge initialized on app start")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init YouTubeDLBridge: ${e.message}")
            }
        }.start()
    }
}

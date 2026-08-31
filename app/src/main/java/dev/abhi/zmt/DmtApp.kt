package dev.abhi.zmt

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.abhi.zmt.data.remote.youtubedl.YouTubeDLBridge
import javax.inject.Inject

@HiltAndroidApp
class DmtApp : Application() {

    @Inject
    lateinit var youTubeDLBridge: YouTubeDLBridge

    override fun onCreate() {
        super.onCreate()
        // yt-dlp bridge is now a stub — YouTube stream resolution uses
        // Innertube fallback resolvers (no Python/Chaquopy required).
        youTubeDLBridge.initialize(this)
    }
}

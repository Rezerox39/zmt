package dev.abhi.zmt.data.remote.youtubedl

import android.content.Context
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YouTubeDLBridge"

/**
 * Stub bridge for yt-dlp integration.
 *
 * The Python/Chaquopy runtime has been removed to support Android 5+
 * (minSdk 21) and 32-bit ARM devices. YouTube stream resolution now
 * relies entirely on the Innertube fallback resolvers in
 * YoutubeStreamResolver, which work without any external dependencies.
 *
 * This stub is kept so the rest of the codebase compiles and the
 * resolver's graceful-fallback path (isReady() → false) is exercised.
 */
@Singleton
class YouTubeDLBridge @Inject constructor() {

    fun initialize(context: Context) {
        Log.i(TAG, "yt-dlp bridge disabled — using Innertube fallback resolvers")
    }

    fun runDownload(videoId: String): String? = null

    fun runPlaylist(url: String): String? = null

    fun isReady(): Boolean = false
}

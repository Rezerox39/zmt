package dev.jyotiraditya.dmt.data.remote.youtubedl

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YouTubeDLBridge"

/**
 * Bridge to Chaquopy Python runtime running yt-dlp.
 *
 * Mirrors ViTune's Dependencies object:
 * - Initializes Python with AndroidPlatform
 * - Loads download.py module
 * - Calls download(quickjs_bin, video_id) to resolve stream URLs
 *
 * QuickJS binary is compiled from CMake and found in nativeLibraryDir.
 */
@Singleton
class YouTubeDLBridge @Inject constructor() {

    @Volatile
    private var initialized = false

    @Volatile
    private var quickjsPath: String? = null

    /**
     * Initialize Python runtime and yt-dlp.
     * Must be called from a background thread (I/O) since it may
     * trigger pip install for yt-dlp on first run.
     */
    fun initialize(context: Context) {
        if (initialized) return

        try {
            // Start Python
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }

            val py = Python.getInstance()
            val module = py.getModule("download")

            // Find QuickJS binary
            val qjs = File(context.applicationInfo.nativeLibraryDir, "libqjs.so")
            if (qjs.exists()) {
                if (!qjs.canExecute()) qjs.setExecutable(true)
                quickjsPath = qjs.absolutePath
                Log.i(TAG, "QuickJS found: ${qjs.absolutePath}")
            } else {
                Log.w(TAG, "QuickJS binary not found, yt-dlp will use bundled JS runtime")
            }

            initialized = true
            Log.i(TAG, "YouTubeDL bridge initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YouTubeDL bridge: ${e.message}", e)
        }
    }

    /**
     * Resolve a YouTube video ID to a playable stream URL using yt-dlp.
     * Returns null if resolution fails.
     */
    fun runDownload(videoId: String): String? {
        if (!initialized) {
            Log.e(TAG, "Not initialized")
            return null
        }

        return try {
            val py = Python.getInstance()
            val module = py.getModule("download")
            val path = quickjsPath ?: ""
            val result = module.callAttr("download", path, videoId).toString()
            Log.d(TAG, "yt-dlp resolved $videoId")
            result
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp download failed for $videoId: ${e.message}")
            null
        }
    }

    /**
     * Check if the bridge is initialized and ready.
     */
    fun isReady(): Boolean = initialized
}

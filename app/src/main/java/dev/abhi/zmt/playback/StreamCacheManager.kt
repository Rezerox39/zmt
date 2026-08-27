package dev.abhi.zmt.playback

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.Volatile

private const val TAG = "StreamCacheMgr"
private const val CACHE_DIR = "stream_cache"

/**
 * Background download cache for streaming sources.
 *
 * When a song is played from YouTube/Telegram/Jellyfin, the full audio file
 * is downloaded in the background. On the next play, the local file is served
 * instantly — no URL resolution, no network wait.
 *
 * This makes repeat-playback feel instant while keeping the "streaming" UX.
 */
@Singleton
class StreamCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cacheDir: File by lazy {
        File(context.filesDir, CACHE_DIR).apply { mkdirs() }
    }

    /** Key → local file path for a given stream URI (e.g. youtube://video/ID). */
    fun cachedFileFor(streamUri: String): File? {
        val file = fileFor(streamUri)
        return if (file.exists() && file.length() > 0) file else null
    }

    /** True if the full audio for [streamUri] is already on disk. */
    fun isCached(streamUri: String): Boolean = cachedFileFor(streamUri) != null

    /**
     * Download the full audio file in the background.
     * Resolves the stream URL via [resolveUrl], then fetches the bytes.
     * Silently ignores errors — the playback stream is unaffected.
     */
    fun cacheInBackground(
        streamUri: String,
        resolveUrl: suspend () -> Pair<String, String>?,  // returns (url, userAgent) or null
    ) {
        if (isCached(streamUri)) {
            Log.d(TAG, "Already cached: $streamUri")
            return
        }
        val target = fileFor(streamUri)

        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    val resolved = resolveUrl() ?: return@runBlocking
                    val (url, userAgent) = resolved
                    Log.d(TAG, "Background cache start: $streamUri → $url")

                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 60_000
                    conn.setRequestProperty("User-Agent", userAgent)
                    conn.connect()

                    val total = conn.contentLength
                    val input = conn.inputStream
                    val tmp = File(target.parent, target.name + ".tmp")

                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var downloaded = 0
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            downloaded += read
                        }
                    }

                    // Rename tmp → final atomically
                    if (tmp.length() > 0) {
                        tmp.renameTo(target)
                        Log.d(TAG, "Background cache done: $streamUri (${target.length()} bytes)")
                    } else {
                        tmp.delete()
                        Log.w(TAG, "Background cache empty: $streamUri")
                    }
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background cache failed: $streamUri — ${e.message}")
                fileFor(streamUri).let { if (it.name.endsWith(".tmp")) it.delete() }
                File(fileFor(streamUri).parent, fileFor(streamUri).name + ".tmp").delete()
            }
        }.start()
    }


    /**
     * Download the full audio for [streamUri] to the local cache and return the file.
     * Reports fractional progress (0f..1f) via [onProgress] when the content length is known.
     * Returns null if resolution or download fails.
     */
    suspend fun downloadSync(
        streamUri: String,
        resolveUrl: suspend () -> Pair<String, String>?,
        onProgress: (Float) -> Unit = {},
    ): File? {
        cachedFileFor(streamUri)?.let { return it }
        val resolved = resolveUrl() ?: return null
        val (url, userAgent) = resolved
        val target = fileFor(streamUri)
        val tmp = File(target.parent, target.name + ".tmp")
        try {
            Log.d(TAG, "Download start: $streamUri")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.connect()
            val total = conn.contentLength
            val input = conn.inputStream
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(64 * 1024)
                var downloaded = 0L
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                }
            }
            conn.disconnect()
            if (tmp.length() > 0) {
                tmp.renameTo(target)
                Log.d(TAG, "Download done: $streamUri (${target.length()} bytes)")
                return target
            }
            tmp.delete()
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Download failed: $streamUri — ${e.message}")
            tmp.delete()
            return null
        }
    }

    /** Compute a stable filename from the stream URI. */
    private fun fileFor(streamUri: String): File {

        val safeName = streamUri
            .replace("://", "_")
            .replace("/", "_")
            .replace("?", "_")
            .replace("&", "_")
            .replace("=", "_")
            .replace("#", "_")
            .take(200) + ".audio"
        return File(cacheDir, safeName)
    }

    /** Purge the entire cache dir. */
    fun purgeAll() {
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "Cache purged")
    }

    /** How many cached files we have. */
    fun cachedCount(): Int = cacheDir.listFiles()?.count { it.length() > 0 } ?: 0

    /** Total cache size in bytes. */
    fun cachedBytes(): Long = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
}

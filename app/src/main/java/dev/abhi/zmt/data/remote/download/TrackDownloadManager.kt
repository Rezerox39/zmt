package dev.abhi.zmt.data.remote.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dev.abhi.zmt.data.remote.youtube.YoutubeStreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

private const val TAG = "TrackDL"
private const val BUFFER_SIZE = 8192  // 8 KB chunks

data class DownloadProgress(
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val isFinished: Boolean = false,
    val error: String? = null,
) {
    val percent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                 else if (isFinished) 100 else if (error != null) -1 else 0
}

@Singleton
class TrackDownloadManager @Inject constructor(
    private val resolver: YoutubeStreamResolver,
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        SupervisorJob() + Dispatchers.IO

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)   // longer read timeout for large files
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Download a YouTube track to the device's Music directory.
     * Runs on a dedicated IO scope — NOT tied to any ViewModel lifecycle.
     */
    fun downloadToDevice(
        context: Context,
        videoId: String,
        title: String,
        artist: String,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        // Dispatch progress updates to Main thread for UI safety
        val mainProgress: (DownloadProgress) -> Unit = { p ->
            kotlinx.coroutines.MainScope().launch { onProgress(p) }
        }
        launch {
            mainProgress(DownloadProgress())
            Log.i(TAG, "Starting download for $videoId")

            // 1. Resolve stream URL via yt-dlp (runs on IO)
            val resolved = resolver.resolve(videoId)
            if (resolved == null) {
                mainProgress(DownloadProgress(error = "Could not resolve stream URL"))
                return@launch
            }

            Log.i(TAG, "URL: ${resolved.url.take(80)}... UA: ${resolved.userAgent}")

            // 2. HTTP request with exact User-Agent from resolver
            val request = Request.Builder()
                .url(resolved.url)
                .header("User-Agent", resolved.userAgent)
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
                .header("Accept-Encoding", "identity")  // don't let OkHttp gzip — we want raw bytes
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                mainProgress(DownloadProgress(error = "HTTP ${response.code}: ${response.message}"))
                return@launch
            }

            val body = response.body ?: run {
                mainProgress(DownloadProgress(error = "Empty response body"))
                return@launch
            }

            // 3. Read in chunks, report progress
            val totalLen = body.contentLength()  // -1 for chunked streams
            val contentType = body.contentType()?.toString() ?: "audio/webm"

            val extension = when {
                contentType.contains("mp4") -> "m4a"
                contentType.contains("webm") -> "webm"
                contentType.contains("ogg") -> "ogg"
                contentType.contains("aac") -> "aac"
                else -> "m4a"
            }
            val mimeType = when {
                contentType.contains("mp4") -> "audio/mp4"
                contentType.contains("webm") -> "audio/webm"
                contentType.contains("ogg") -> "audio/ogg"
                contentType.contains("aac") -> "audio/aac"
                else -> "audio/mp4"
            }

            val fileName = "${title} - ${artist}.${extension}"
                .replace(Regex("[/\\\\:*?\"<>|]"), "_")

            // 4. Create MediaStore entry
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val resolver_ = context.contentResolver
            val collectionUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver_.insert(collectionUri, contentValues)

            if (itemUri == null) {
                mainProgress(DownloadProgress(error = "Failed to create MediaStore entry"))
                return@launch
            }

            try {
                resolver_.openOutputStream(itemUri)?.let { outputStream ->
                    val inputStream = body.byteStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    var totalRead = 0L
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        outputStream.flush()
                        totalRead += bytesRead

                        // Report progress every chunk
                        if (totalLen > 0) {
                            mainProgress(DownloadProgress(
                                bytesDownloaded = totalRead,
                                totalBytes = totalLen,
                            ))
                        } else {
                            // Send periodic heartbeats for chunked (unknown-length) streams
                            if (totalRead % (BUFFER_SIZE * 128L) == 0L) {
                                mainProgress(DownloadProgress(
                                    bytesDownloaded = totalRead,
                                    totalBytes = -1,
                                ))
                            }
                        }
                    }

                    outputStream.flush()
                    inputStream.close()
                } ?: throw Exception("Could not open output stream")

                // Clear pending flag
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver_.update(itemUri, contentValues, null, null)

                mainProgress(DownloadProgress(
                    bytesDownloaded = totalLen.coerceAtLeast(0),
                    totalBytes = totalLen.coerceAtLeast(0),
                    isFinished = true,
                ))
                Log.i(TAG, "Saved to Music: $fileName")
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                try { resolver_.delete(itemUri, null, null) } catch (_: Exception) {}
                mainProgress(DownloadProgress(error = "Download failed: ${e.message}"))
            }
        }
    }

    /**
     * Download to cache for Telegram sharing. Same chunked approach.
     */
    fun downloadToCache(
        context: Context,
        videoId: String,
        onResult: (File?) -> Unit,
    ) {
        launch {
            val resolved = resolver.resolve(videoId) ?: run {
                onResult(null); return@launch
            }

            val request = Request.Builder()
                .url(resolved.url)
                .header("User-Agent", resolved.userAgent)
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
                .header("Accept-Encoding", "identity")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                onResult(null); return@launch
            }

            val body = response.body ?: run {
                onResult(null); return@launch
            }

            val contentType = body.contentType()?.toString() ?: "audio/webm"
            // Always use m4a extension for cache files
            val extension = "m4a"

            val cacheDir = File(context.cacheDir, "downloads").apply { mkdirs() }
            val cacheFile = File(cacheDir, "yt_backup_${videoId}.${extension}")

            try {
                FileOutputStream(cacheFile).use { outputStream ->
                    val inputStream = body.byteStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        outputStream.flush()
                    }
                    outputStream.flush()
                    inputStream.close()
                }
                Log.i(TAG, "Cached to: ${cacheFile.absolutePath}")
                onResult(cacheFile)
            } catch (e: Exception) {
                Log.e(TAG, "Cache failed: ${e.message}", e)
                cacheFile.delete()
                onResult(null)
            }
        }
    }
}

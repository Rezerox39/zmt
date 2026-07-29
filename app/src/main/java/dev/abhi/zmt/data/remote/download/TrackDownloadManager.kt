package dev.abhi.zmt.data.remote.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dev.abhi.zmt.data.remote.youtube.YoutubeStreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TrackDL"

data class DownloadProgress(
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val isFinished: Boolean = false,
    val error: String? = null,
)

@Singleton
class TrackDownloadManager @Inject constructor(
    private val resolver: YoutubeStreamResolver,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Download a YouTube track to the device's Music directory.
     * Returns the content URI on success, null on failure.
     */
    suspend fun downloadToDevice(
        context: Context,
        videoId: String,
        title: String,
        artist: String,
        onProgress: (DownloadProgress) -> Unit,
    ): Uri? = withContext(Dispatchers.IO) {
        onProgress(DownloadProgress())

        // 1. Resolve stream URL via yt-dlp
        val resolved = resolver.resolve(videoId)
            ?: run {
                onProgress(DownloadProgress(error = "Could not resolve stream URL"))
                return@withContext null
            }

        Log.i(TAG, "Downloading: ${resolved.url.take(80)}...")
        Log.i(TAG, "User-Agent: ${resolved.userAgent}")

        // 2. HTTP request with correct User-Agent
        val request = Request.Builder()
            .url(resolved.url)
            .header("User-Agent", resolved.userAgent)
            .header("Accept", "*/*")
            .header("Connection", "keep-alive")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            onProgress(DownloadProgress(error = "HTTP ${response.code}: ${response.message}"))
            return@withContext null
        }

        val body = response.body ?: run {
            onProgress(DownloadProgress(error = "Empty response body"))
            return@withContext null
        }

        val contentLength = body.contentLength()
        val inputStream = body.byteStream()
        val bytes = inputStream.readBytes()
        inputStream.close()

        onProgress(DownloadProgress(bytesDownloaded = bytes.size.toLong(), totalBytes = bytes.size.toLong()))

        // 3. Determine MIME type and extension
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

        // 4. Save via MediaStore to Music directory
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
            onProgress(DownloadProgress(error = "Failed to create MediaStore entry"))
            return@withContext null
        }

        try {
            resolver_.openOutputStream(itemUri)?.use { outputStream ->
                outputStream.write(bytes)
            } ?: throw Exception("Could not open output stream")

            // Clear the pending flag
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver_.update(itemUri, contentValues, null, null)

            onProgress(DownloadProgress(bytesDownloaded = bytes.size.toLong(), totalBytes = bytes.size.toLong(), isFinished = true))
            Log.i(TAG, "Saved to Music: $fileName")
            return@withContext itemUri
        } catch (e: Exception) {
            // Cleanup on failure
            resolver_.delete(itemUri, null, null)
            onProgress(DownloadProgress(error = e.message ?: "Download failed"))
            return@withContext null
        }
    }

    /**
     * Download a YouTube track to a temporary cache file.
     * Returns the cached File on success.
     */
    suspend fun downloadToCache(
        context: Context,
        videoId: String,
    ): File? = withContext(Dispatchers.IO) {
        val resolved = resolver.resolve(videoId) ?: return@withContext null

        val request = Request.Builder()
            .url(resolved.url)
            .header("User-Agent", resolved.userAgent)
            .header("Accept", "*/*")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null

        val body = response.body ?: return@withContext null
        val contentType = body.contentType()?.toString() ?: "audio/webm"
        val extension = when {
            contentType.contains("mp4") -> "m4a"
            contentType.contains("webm") -> "webm"
            else -> "m4a"
        }

        val cacheFile = File(context.cacheDir, "yt_download_${videoId}.${extension}")
        body.byteStream().use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }

        Log.i(TAG, "Cached to: ${cacheFile.absolutePath}")
        cacheFile
    }
}

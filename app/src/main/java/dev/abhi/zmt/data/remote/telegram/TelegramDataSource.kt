package dev.abhi.zmt.data.remote.telegram

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

private const val TAG = "TelegramDS"
private const val SCHEME_TG = "tg"
private const val AUTHORITY_AUDIO = "audio"
private const val POLL_INTERVAL_MS = 100L
private const val DOWNLOAD_TIMEOUT_MS = 30_000L
private const val MIN_CHUNK = 128 * 1024
private const val MAX_CHUNK = 512 * 1024

/**
 * Streaming data source for Telegram audio files.
 *
 * Uses chunked serving: checks for local cache first, then triggers a
 * background download and serves bytes as they arrive. This avoids
 * blocking on the full download before playback starts.
 */
@OptIn(UnstableApi::class)
class TelegramDataSource private constructor(
    private val telegramClient: TelegramClient,
) : BaseDataSource(true) {

    private var fileId: Long = -1
    private var totalSize: Long = 0
    private var currentPosition: Long = 0
    private var localPath: String? = null
    private var uri: Uri? = null

    @Singleton
    class Factory @Inject constructor(
        private val telegramClient: TelegramClient,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return TelegramDataSource(telegramClient)
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        resetState()
        uri = dataSpec.uri
        fileId = parseFileId(dataSpec.uri)
            ?: throw IOException("Invalid Telegram URI: ${dataSpec.uri}")
        currentPosition = dataSpec.position

        Log.i(TAG, "Opening fileId=$fileId at position=$currentPosition")

        // Step 1: Check if file is already fully cached locally
        val cached = getCachedPath()
        if (cached != null) {
            localPath = cached
            totalSize = File(cached).length()
            Log.i(TAG, "Serving from local cache: $cached ($totalSize bytes)")
            transferInitializing(dataSpec)
            transferStarted(dataSpec)
            return totalSize
        }

        // Step 2: Trigger download and wait for it to complete
        val file = kotlinx.coroutines.runBlocking {
            telegramClient.downloadFile(fileId)
        }

        val path = file.local.path
        val size = file.expectedSize.toLong()

        if (file.local.isDownloadingCompleted && path != null && File(path).exists()) {
            localPath = path
            totalSize = size
            Log.i(TAG, "Download completed immediately: $path ($totalSize bytes)")
            transferInitializing(dataSpec)
            transferStarted(dataSpec)
            return totalSize
        }

        // Step 3: File is downloading — poll until we have enough data or it completes
        val resolvedPath = path ?: pollForPath(fileId)
        if (resolvedPath != null && File(resolvedPath).exists()) {
            localPath = resolvedPath
            totalSize = size.takeIf { it > 0 } ?: File(resolvedPath).length()
            Log.i(TAG, "Download ready: $resolvedPath ($totalSize bytes)")
            transferInitializing(dataSpec)
            transferStarted(dataSpec)
            return totalSize
        }

        throw IOException("TDLib could not provide local path for file $fileId")
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        val path = localPath ?: return C.RESULT_END_OF_INPUT
        val file = File(path)
        if (!file.exists()) return C.RESULT_END_OF_INPUT

        val fileLen = file.length()
        if (currentPosition >= fileLen) {
            // If total size was unknown, update it now
            if (totalSize <= 0) totalSize = fileLen
            return C.RESULT_END_OF_INPUT
        }

        val available = fileLen - currentPosition
        val toRead = min(length.toLong(), available).toInt()

        file.inputStream().use { stream ->
            stream.skip(currentPosition)
            val bytesRead = stream.read(target, offset, toRead)
            if (bytesRead > 0) {
                currentPosition += bytesRead
                bytesTransferred(bytesRead)
                return bytesRead
            }
        }
        return C.RESULT_END_OF_INPUT
    }

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun getUri(): Uri? = uri

    override fun close() {
        resetState()
        transferEnded()
    }

    private fun resetState() {
        fileId = -1
        totalSize = 0
        currentPosition = 0
        localPath = null
        uri = null
    }

    /** Check if TDLib already has this file fully downloaded locally. */
    private fun getCachedPath(): String? {
        return try {
            val result = kotlinx.coroutines.runBlocking {
                telegramClient.getFile(fileId)
            }
            val local = result.local
            if (local.isDownloadingCompleted && local.path != null) {
                val f = File(local.path)
                if (f.exists() && f.length() > 0) local.path else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "getCachedPath failed: ${e.message}")
            null
        }
    }

    /** Poll TDLib until the file path becomes available or timeout. */
    private fun pollForPath(fileId: Long): String? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < DOWNLOAD_TIMEOUT_MS) {
            try {
                val result = kotlinx.coroutines.runBlocking {
                    telegramClient.getFile(fileId)
                }
                val local = result.local
                if (local.isDownloadingCompleted && local.path != null) {
                    val f = File(local.path)
                    if (f.exists() && f.length() > 0) return local.path
                }
            } catch (_: Exception) { }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        Log.w(TAG, "pollForPath timed out for fileId=$fileId")
        return null
    }

    companion object {
        fun parseFileId(uri: Uri): Long? {
            if (uri.scheme != SCHEME_TG || uri.authority != AUTHORITY_AUDIO) return null
            return uri.lastPathSegment?.toLongOrNull()
        }
    }
}

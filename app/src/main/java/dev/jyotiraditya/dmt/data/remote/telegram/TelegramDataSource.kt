package dev.jyotiraditya.dmt.data.remote.telegram

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val SCHEME_TG = "tg"
private const val AUTHORITY_AUDIO = "audio"
private const val MIN_CHUNK_SIZE = 128 * 1024
private const val MAX_CHUNK_SIZE = 512 * 1024

@OptIn(UnstableApi::class)
class TelegramDataSource private constructor(
    private val telegramClient: TelegramClient,
) : BaseDataSource(/* isNetwork = */ true) {

    private var fileId: Long = -1
    private var totalSize: Long = 0
    private var currentPosition: Long = 0
    private var readBuffer: ByteArray = ByteArray(0)
    private var bufferOffset: Int = 0
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
        fileId = parseFileId(dataSpec.uri) ?: throw IOException("Invalid Telegram URI: ${dataSpec.uri}")

        totalSize = resolveFileSize(fileId)
        if (totalSize <= 0) {
            totalSize = C.LENGTH_UNSET.toLong()
        }
        currentPosition = dataSpec.uriPositionOffset
        transferInitializing(dataSpec)
        transferStarted(dataSpec)
        return totalSize
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (readBuffer.isEmpty() || bufferOffset >= readBuffer.size) {
            val chunk = runBlockingTelegram { fetchChunk(fileId, currentPosition, length.coerceAtLeast(MIN_CHUNK_SIZE)) }
            readBuffer = chunk
            bufferOffset = 0

            if (readBuffer.isEmpty()) {
                return C.RESULT_END_OF_INPUT
            }
        }

        val available = readBuffer.size - bufferOffset
        val toRead = length.coerceAtMost(available)
        System.arraycopy(readBuffer, bufferOffset, target, offset, toRead)
        bufferOffset += toRead
        currentPosition += toRead
        bytesTransferred(toRead)
        return toRead
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
        readBuffer = ByteArray(0)
        bufferOffset = 0
        uri = null
    }

    private fun fetchChunk(fileId: Long, offset: Long, size: Int): ByteArray {
        val chunkSize = size.coerceIn(MIN_CHUNK_SIZE, MAX_CHUNK_SIZE)
        val alignedOffset = (offset / MIN_CHUNK_SIZE) * MIN_CHUNK_SIZE

        return try {
            runBlockingTelegram { telegramClient.readRemoteFile(fileId, alignedOffset, chunkSize) }
        } catch (e: Exception) {
            throw IOException("Failed to download Telegram chunk: ${e.message}", e)
        }
    }

    private fun resolveFileSize(fileId: Long): Long {
        return try {
            val result = runBlockingTelegram { telegramClient.getFileLocal(fileId) }
            val expectedSize = result.optLong("expected_size", 0)
            if (expectedSize > 0) expectedSize else {
                runBlockingTelegram { telegramClient.downloadFile(fileId) }
                val recheck = runBlockingTelegram { telegramClient.getFileLocal(fileId) }
                recheck.optLong("expected_size", 0)
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun <T> runBlockingTelegram(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }

    companion object {
        fun parseFileId(uri: Uri): Long? {
            if (uri.scheme != SCHEME_TG || uri.authority != AUTHORITY_AUDIO) return null
            return uri.lastPathSegment?.toLongOrNull()
        }
    }
}

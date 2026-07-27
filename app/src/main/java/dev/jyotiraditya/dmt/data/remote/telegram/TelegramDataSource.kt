package dev.jyotiraditya.dmt.data.remote.telegram

import android.net.Uri
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

private const val SCHEME_TG = "tg"
private const val AUTHORITY_AUDIO = "audio"

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
        uri = dataSpec.uri
        fileId = parseFileId(dataSpec.uri) ?: throw IOException("Invalid Telegram URI: ${dataSpec.uri}")
        currentPosition = dataSpec.position

        // Download the file from Telegram
        val file = kotlinx.coroutines.runBlocking {
            telegramClient.downloadFile(fileId)
        }

        localPath = file.local.path
        totalSize = file.expectedSize.toLong()

        if (localPath == null || !File(localPath!!).exists()) {
            throw IOException("TDLib could not provide local path for file $fileId")
        }

        transferInitializing(dataSpec)
        transferStarted(dataSpec)
        return totalSize
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        val path = localPath ?: return C.RESULT_END_OF_INPUT
        val file = File(path)
        if (!file.exists()) return C.RESULT_END_OF_INPUT

        val remaining = file.length() - currentPosition
        if (remaining <= 0) return C.RESULT_END_OF_INPUT

        val toRead = length.toLong().coerceAtMost(remaining).toInt()
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
        localPath = null
        transferEnded()
    }

    companion object {
        fun parseFileId(uri: Uri): Long? {
            if (uri.scheme != SCHEME_TG || uri.authority != AUTHORITY_AUDIO) return null
            return uri.lastPathSegment?.toLongOrNull()
        }
    }
}

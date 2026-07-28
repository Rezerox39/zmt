package dev.jyotiraditya.dmt.data.remote.telegram

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import dev.jyotiraditya.dmt.data.remote.youtube.YoutubeStreamDataSource
import javax.inject.Inject
import javax.inject.Singleton

private const val SCHEME_TG = "tg"
private const val SCHEME_YT = "youtube"

@OptIn(UnstableApi::class)
class CompositeDataSource private constructor(
    private val defaultDelegate: DataSource,
    private val telegramDelegate: DataSource,
    private val youtubeDelegate: DataSource,
) : DataSource {

    private var active: DataSource = defaultDelegate

    override fun addTransferListener(transferListener: TransferListener) {
        defaultDelegate.addTransferListener(transferListener)
        telegramDelegate.addTransferListener(transferListener)
        youtubeDelegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        active = when (dataSpec.uri.scheme) {
            SCHEME_TG -> telegramDelegate
            SCHEME_YT -> youtubeDelegate
            else -> defaultDelegate
        }
        return active.open(dataSpec)
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int =
        active.read(target, offset, length)

    override fun getResponseHeaders(): Map<String, List<String>> =
        active.responseHeaders

    override fun getUri(): Uri? = active.uri

    override fun close() {
        active.close()
    }

    @Singleton
    class Factory @Inject constructor(
        private val defaultFactory: DefaultDataSource.Factory,
        private val telegramFactory: TelegramDataSource.Factory,
        private val youtubeFactory: YoutubeStreamDataSource.Factory,
    ) : DataSource.Factory {

        override fun createDataSource(): DataSource {
            return CompositeDataSource(
                defaultDelegate = defaultFactory.createDataSource(),
                telegramDelegate = telegramFactory.createDataSource(),
                youtubeDelegate = youtubeFactory.createDataSource(),
            )
        }
    }
}

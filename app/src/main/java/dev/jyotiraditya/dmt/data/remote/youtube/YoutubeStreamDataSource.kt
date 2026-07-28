package dev.jyotiraditya.dmt.data.remote.youtube

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val YOUTUBE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

@OptIn(UnstableApi::class)
class YoutubeStreamDataSource private constructor(
    private val resolver: YoutubeStreamResolver,
    private val httpDataSource: DataSource,
) : DataSource {

    private var resolvedUri: Uri? = null

    @Singleton
    class Factory @Inject constructor(
        private val resolver: YoutubeStreamResolver,
        private val okHttpClient: OkHttpClient,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val httpFactory = OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent(YOUTUBE_USER_AGENT)
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to "https://music.youtube.com/",
                        "Origin" to "https://music.youtube.com",
                    )
                )
            return YoutubeStreamDataSource(resolver, httpFactory.createDataSource())
        }
    }

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        httpDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val videoId = dataSpec.uri.lastPathSegment
            ?: throw IOException("No video ID in YouTube URI: ${dataSpec.uri}")

        val streamUrl = kotlinx.coroutines.runBlocking {
            resolver.resolve(videoId)
        } ?: throw IOException("Could not resolve YouTube stream for $videoId")

        resolvedUri = Uri.parse(streamUrl)

        val resolvedSpec = dataSpec.buildUpon().setUri(resolvedUri!!).build()
        return httpDataSource.open(resolvedSpec)
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int =
        httpDataSource.read(target, offset, length)

    override fun getResponseHeaders(): Map<String, List<String>> =
        httpDataSource.responseHeaders

    override fun getUri(): Uri? = resolvedUri

    override fun close() {
        httpDataSource.close()
        resolvedUri = null
    }
}

package dev.jyotiraditya.dmt.data.remote.youtube

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeStreamDS"

/**
 * DataSource for YouTube Music streaming.
 *
 * Mirrors ViTune's approach:
 * - Uses DefaultHttpDataSource (not OkHttpDataSource)
 * - Sets User-Agent to match the Innertube client context that generated the URL
 * - Sets Origin and Referer to music.youtube.com
 * - No interceptors, no custom OkHttp client
 */
@OptIn(UnstableApi::class)
class YoutubeStreamDataSource private constructor(
    private val resolver: YoutubeStreamResolver,
) : DataSource {

    private var resolvedUri: Uri? = null
    private var httpDataSource: DataSource? = null

    @Singleton
    class Factory @Inject constructor(
        private val resolver: YoutubeStreamResolver,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return YoutubeStreamDataSource(resolver)
        }
    }

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        httpDataSource?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val videoId = dataSpec.uri.lastPathSegment
            ?: throw IOException("No video ID in YouTube URI: ${dataSpec.uri}")

        val resolved = kotlinx.coroutines.runBlocking {
            resolver.resolve(videoId)
        } ?: throw IOException("Could not resolve YouTube stream for $videoId")

        resolvedUri = Uri.parse(resolved.url)
        Log.d(TAG, "Stream resolved: ${resolved.url.take(80)}... UA=${resolved.userAgent.take(40)}")

        // Mirror ViTune: DefaultHttpDataSource with context-matched User-Agent
        // No OkHttp, no interceptors, no custom client
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(16_000)
            .setReadTimeoutMs(8_000)
            .setUserAgent(resolved.userAgent)
            .setDefaultRequestProperties(
                mapOf(
                    "Origin" to "https://music.youtube.com",
                    "Referer" to "https://music.youtube.com/",
                )
            )

        httpDataSource = httpFactory.createDataSource()

        val resolvedSpec = dataSpec.buildUpon().setUri(resolvedUri!!).build()
        return httpDataSource!!.open(resolvedSpec)
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int =
        httpDataSource?.read(target, offset, length) ?: 0

    override fun getResponseHeaders(): Map<String, List<String>> =
        httpDataSource?.responseHeaders ?: emptyMap()

    override fun getUri(): Uri? = resolvedUri

    override fun close() {
        httpDataSource?.close()
        httpDataSource = null
        resolvedUri = null
    }
}

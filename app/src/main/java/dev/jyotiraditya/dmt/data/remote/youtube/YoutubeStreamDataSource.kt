package dev.jyotiraditya.dmt.data.remote.youtube

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeStreamDS"

@OptIn(UnstableApi::class)
class YoutubeStreamDataSource private constructor(
    private val resolver: YoutubeStreamResolver,
    private val baseOkHttpClient: OkHttpClient,
) : DataSource {

    private var resolvedUri: Uri? = null
    private var httpDataSource: DataSource? = null

    // Clean OkHttpClient without the User-Agent interceptor from NetworkModule
    // that would override the context-matched UA and cause 403
    private val streamClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .apply {
                interceptors().clear()
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Singleton
    class Factory @Inject constructor(
        private val resolver: YoutubeStreamResolver,
        private val okHttpClient: OkHttpClient,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return YoutubeStreamDataSource(resolver, okHttpClient)
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
        Log.d(TAG, "Stream URL resolved, UA=${resolved.userAgent.take(50)}")

        val httpFactory = OkHttpDataSource.Factory(streamClient)
            .setUserAgent(resolved.userAgent)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://music.youtube.com/",
                    "Origin" to "https://music.youtube.com",
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

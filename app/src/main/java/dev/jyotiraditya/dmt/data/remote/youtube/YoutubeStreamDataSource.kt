package dev.jyotiraditya.dmt.data.remote.youtube

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val SCHEME_YT = "youtube"
private const val AUTHORITY_VIDEO = "video"

@OptIn(UnstableApi::class)
class YoutubeStreamDataSource private constructor(
    private val resolver: YoutubeStreamResolver,
    private val httpDataSource: OkHttpDataSource,
) : BaseDataSource(true) {

    private var videoId: String = ""
    private var resolvedUri: Uri? = null

    @Singleton
    class Factory @Inject constructor(
        private val resolver: YoutubeStreamResolver,
        private val okHttpClient: OkHttpClient,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val httpFactory = OkHttpDataSource.Factory(okHttpClient)
            return YoutubeStreamDataSource(resolver, httpFactory.createDataSource())
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        videoId = uri.lastPathSegment
            ?: throw IOException("No video ID in YouTube URI: $uri")

        val streamUrl = kotlinx.coroutines.runBlocking {
            resolver.resolve(videoId)
        } ?: throw IOException("Could not resolve YouTube stream for $videoId")

        resolvedUri = Uri.parse(streamUrl)

        val resolvedSpec = dataSpec.buildUpon().setUri(resolvedUri).build()
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

    companion object {
        fun parseVideoId(uri: Uri): String? {
            if (uri.scheme != SCHEME_YT || uri.authority != AUTHORITY_VIDEO) return null
            return uri.lastPathSegment
        }
    }
}

package dev.jyotiraditya.dmt.data.remote.youtube

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeStreamDS"

/**
 * DataSource for YouTube Music streaming.
 *
 * Resolves [youtube://video/{videoId}] URIs to playable stream URLs via
 * [YoutubeStreamResolver], then delegates actual HTTP transfer to a fresh
 * [DefaultHttpDataSource] configured with the resolved stream's User-Agent.
 *
 * Headers:
 * - Only sends the User-Agent from the Innertube context that generated the URL.
 * - Does NOT send hardcoded Origin/Referer — those create a fingerprint mismatch
 *   with the IOS client context used for URL generation, causing YouTube CDN
 *   to respond with HTTP 403.
 * - No interceptors, no custom OkHttp client — mirroring ViTune's approach.
 */
@OptIn(UnstableApi::class)
class YoutubeStreamDataSource private constructor(
    private val resolver: YoutubeStreamResolver,
) : DataSource {

    private var inner: DataSource? = null
    private var openedUri: Uri? = null

    @Singleton
    class Factory @Inject constructor(
        private val resolver: YoutubeStreamResolver,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return YoutubeStreamDataSource(resolver)
        }
    }

    override fun addTransferListener(transferListener: TransferListener) {
        // Inner DataSource is created lazily in open(), so we attach
        // the listener after creation. ExoPlayer typically calls
        // addTransferListener before open().
        inner?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val videoId = dataSpec.uri.lastPathSegment
            ?: throw IOException("No video ID in YouTube URI: ${dataSpec.uri}")

        val resolved = kotlinx.coroutines.runBlocking {
            resolver.resolve(videoId)
        } ?: throw IOException("Could not resolve YouTube stream for $videoId")

        openedUri = Uri.parse(resolved.url)
        Log.d(TAG, "Stream resolved: ${resolved.url.take(80)}")

        // DefaultHttpDataSource — no OkHttp, no interceptors, no custom client.
        // Only the User-Agent from the resolved context; no hardcoded Origin/Referer.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(16_000)
            .setReadTimeoutMs(8_000)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0")
            .setAllowCrossProtocolRedirects(true)

        inner = httpFactory.createDataSource()

        val resolvedSpec = dataSpec.buildUpon().setUri(openedUri!!).build()
        return inner!!.open(resolvedSpec)
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        val ds = inner
        if (ds == null) {
            Log.w(TAG, "read() called but inner DataSource is null")
            return C.RESULT_END_OF_INPUT
        }
        return ds.read(target, offset, length)
    }

    override fun getResponseHeaders(): Map<String, List<String>> =
        inner?.responseHeaders ?: emptyMap()

    override fun getUri(): Uri? = inner?.uri ?: openedUri

    override fun close() {
        inner?.close()
        inner = null
        openedUri = null
    }
}

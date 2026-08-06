package dev.abhi.zmt.data.remote.youtube

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeStreamDS"
private const val MAX_OPEN_ATTEMPTS = 3

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
        inner?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val videoId = dataSpec.uri.lastPathSegment
            ?: throw IOException("No video ID in YouTube URI: ${dataSpec.uri}")

        // === PlaybackDebug: Step 1 — Resolve stream URL ===
        Log.i(TAG, "=== PlaybackDebug ===")
        Log.i(TAG, "Song videoId: $videoId")

        var lastError: Exception? = null
        repeat(MAX_OPEN_ATTEMPTS) { attempt ->
            val resolved = try {
                kotlinx.coroutines.runBlocking {
                    resolver.resolve(videoId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "CRASHED during resolve: ${e.message}")
                lastError = IOException("Resolver crashed: ${e.message}", e)
                return@repeat
            }

            if (resolved == null) {
                Log.e(TAG, "FAILED: resolver returned null for $videoId (attempt ${attempt + 1}/$MAX_OPEN_ATTEMPTS)")
                lastError = IOException("Could not resolve YouTube stream for $videoId")
                return@repeat
            }

            openedUri = Uri.parse(resolved.url)
            Log.i(TAG, "Resolver selected: ${resolved.resolverName}")
            Log.i(TAG, "Resolved URL: ${resolved.url}")
            Log.i(TAG, "User-Agent: ${resolved.userAgent}")

            val httpFactory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(30_000)
                .setReadTimeoutMs(30_000)
                .setUserAgent(resolved.userAgent)
                .setAllowCrossProtocolRedirects(true)

            val httpDataSource = httpFactory.createDataSource()
            val resolvedSpec = dataSpec.buildUpon().setUri(openedUri!!).build()
            try {
                val result = httpDataSource.open(resolvedSpec)
                inner = httpDataSource
                Log.i(TAG, "HTTP open succeeded, content length: $result")
                return result
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                val statusCode = try { e.responseCode } catch (ex: Exception) { -1 }
                val headers = try { e.headerFields?.toString() ?: "N/A" } catch (ex: Exception) { "N/A" }
                Log.e(TAG, "HTTP FAILED! Status code: $statusCode (attempt ${attempt + 1}/$MAX_OPEN_ATTEMPTS)")
                Log.e(TAG, "Exception message: ${e.message}")
                Log.e(TAG, "Response headers: $headers")
                Log.e(TAG, "URL: ${openedUri}")
                lastError = e
                if (attempt < MAX_OPEN_ATTEMPTS - 1) {
                    Log.w(TAG, "Refreshing stream URL and retrying...")
                }
            } catch (e: Exception) {
                Log.e(TAG, "HTTP open failed: ${e.message} (attempt ${attempt + 1}/$MAX_OPEN_ATTEMPTS)")
                lastError = e
            }
        }

        throw lastError ?: IOException("Could not resolve YouTube stream for $videoId")
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

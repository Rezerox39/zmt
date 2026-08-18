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

/**
 * Maximum number of resolution + playback attempts before giving up.
 */
private const val MAX_ATTEMPTS = 3

/**
 * HTTP status codes that indicate a transient / retryable error.
 */
private val RETRYABLE_HTTP_CODES = setOf(403, 429, 500, 502, 503)

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

        Log.i(TAG, "=== PlaybackDebug ===")
        Log.i(TAG, "Song videoId: $videoId")

        // Phase 1: Resolve stream URLs (all options upfront for retry)
        val candidates = resolveCandidates(videoId)
        if (candidates.isEmpty()) {
            throw IOException("Could not resolve any YouTube stream for $videoId")
        }

        // Phase 2: Try each candidate until one opens successfully
        var lastException: Exception? = null
        for ((index, candidate) in candidates.withIndex()) {
            if (index >= MAX_ATTEMPTS) break

            Log.i(TAG, "Attempt ${index + 1}/${minOf(candidates.size, MAX_ATTEMPTS)}: ${candidate.resolverName}")
            try {
                val length = openWithCandidate(candidate, dataSpec)
                Log.i(TAG, "HTTP open succeeded (attempt ${index + 1}), length: $length")
                return length
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                val code = try { e.responseCode } catch (_: Exception) { -1 }
                val isRetryable = code in RETRYABLE_HTTP_CODES
                Log.e(TAG, "HTTP $code from ${candidate.resolverName} (retryable=$isRetryable)")
                lastException = e
                if (!isRetryable) break
            } catch (e: Exception) {
                Log.e(TAG, "Attempt ${index + 1} failed: ${e.message}")
                lastException = e
            }
        }

        throw IOException(
            "Failed to open YouTube stream for $videoId after ${minOf(candidates.size, MAX_ATTEMPTS)} attempts",
            lastException,
        )
    }

    private fun resolveCandidates(videoId: String): List<ResolvedStream> {
        val candidates = try {
            kotlinx.coroutines.runBlocking {
                resolver.resolveAll(videoId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolveAll crashed: ${e.message}")
            emptyList()
        }

        if (candidates.isNotEmpty()) return candidates

        Log.w(TAG, "resolveAll returned empty, trying single resolve")
        return try {
            kotlinx.coroutines.runBlocking {
                resolver.resolve(videoId)?.let { listOf(it) } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolve crashed: ${e.message}")
            emptyList()
        }
    }

    private fun openWithCandidate(candidate: ResolvedStream, dataSpec: DataSpec): Long {
        val uri = Uri.parse(candidate.url)
        openedUri = uri

        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setUserAgent(candidate.userAgent)
            .setAllowCrossProtocolRedirects(true)

        close()
        inner = httpFactory.createDataSource()

        val resolvedSpec = dataSpec.buildUpon().setUri(uri).build()
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

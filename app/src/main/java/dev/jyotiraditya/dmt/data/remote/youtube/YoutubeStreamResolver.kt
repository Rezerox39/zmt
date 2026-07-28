package dev.jyotiraditya.dmt.data.remote.youtube

import android.util.Log
import dev.jyotiraditya.dmt.data.remote.youtubedl.YouTubeDLBridge
import dev.jyotiraditya.dmt.data.remote.youtubedl.YouTubeDLResponse
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.Innertube
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.bodies.PlayerBody
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.requests.player
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeStreamResolver"

/**
 * Resolved YouTube stream URL.
 */
data class ResolvedStream(
    val url: String,
    val contentLength: Long = 0L,
)

/**
 * Resolves YouTube Music stream URLs.
 *
 * Strategy matches ViTune's architecture:
 * 1. yt-dlp via Python/Chaquopy (PRIMARY) — handles JS signature deciphering,
 *    n-parameter transforms, and returns reliable playable URLs
 * 2. Innertube player API (FALLBACK) — used when yt-dlp is unavailable
 *
 * yt-dlp requires the QuickJS binary for JavaScript signature deciphering,
 * compiled from source via CMake in build process.
 */
@Singleton
class YoutubeStreamResolver @Inject constructor(
    private val youTubeDLBridge: YouTubeDLBridge,
) {

    /**
     * Resolve a YouTube videoId to a playable stream URL.
     */
    suspend fun resolve(videoId: String): ResolvedStream? {
        // 1. Try yt-dlp (PRIMARY) — matches ViTune's architecture
        val ytdl = resolveViaYtDlp(videoId)
        if (ytdl != null) return ytdl

        // 2. Fallback to Innertube
        val inner = resolveViaInnertube(videoId)
        if (inner != null) return inner

        Log.e(TAG, "All resolvers failed for $videoId")
        return null
    }

    /**
     * Resolve via yt-dlp (Python/Chaquopy).
     * Requires:
     * - Chaquopy Gradle plugin
     * - Python 3.14 bundled via Chaquopy
     * - yt-dlp and yt-dlp-ejs pip packages
     * - QuickJS binary compiled via CMake (libqjs.so)
     */
    private suspend fun resolveViaYtDlp(videoId: String): ResolvedStream? {
        if (!youTubeDLBridge.isReady()) {
            Log.w(TAG, "yt-dlp bridge not ready")
            return null
        }

        return try {
            val jsonStr = youTubeDLBridge.runDownload(videoId) ?: return null
            val response = YouTubeDLResponse.fromString(jsonStr)

            if (response.id != videoId) {
                Log.w(TAG, "yt-dlp returned wrong video ID: ${response.id} != $videoId")
                return null
            }

            val url = response.url ?: run {
                Log.w(TAG, "yt-dlp returned no URL for $videoId")
                null
            }

            if (url != null) {
                Log.d(TAG, "yt-dlp: ${response.formatId}, size=${response.fileSize}")
                ResolvedStream(url = url, contentLength = response.fileSize)
            } else {
                // Fallback: use best format URL from formats list
                response.formats
                    ?.firstOrNull { it.url != null }
                    ?.url
                    ?.let { ResolvedStream(url = it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp failed for $videoId: ${e.message}")
            null
        }
    }

    /**
     * Fallback: resolve via Innertube player API.
     * Uses IOS → Web → AndroidMusic → TV contexts.
     * Only returns formats with direct [url] (no signatureCipher).
     */
    private suspend fun resolveViaInnertube(videoId: String): ResolvedStream? {
        return try {
            val result = Innertube.player(
                body = PlayerBody(videoId = videoId),
                checkIsValid = true,
            )
            val response = result?.getOrNull() ?: return null
            val streamingData = response.streamingData ?: return null

            val format = streamingData.adaptiveFormats
                ?.filter { it.url != null }
                ?.let { formats ->
                    formats.findLast { it.itag == 251 || it.itag == 140 }
                        ?: formats.maxByOrNull { it.bitrate ?: 0L }
                } ?: return null

            val url = format.url ?: return null
            Log.d(TAG, "Innertube fallback: ${format.mimeType} (${format.bitrate}kbps)")
            ResolvedStream(url = url, contentLength = format.contentLength ?: 0L)
        } catch (e: Exception) {
            Log.e(TAG, "Innertube failed: ${e.message}")
            null
        }
    }
}

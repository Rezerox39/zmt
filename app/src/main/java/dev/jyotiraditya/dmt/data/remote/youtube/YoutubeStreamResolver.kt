package dev.jyotiraditya.dmt.data.remote.youtube

import android.util.Log
import dev.jyotiraditya.dmt.data.remote.youtubedl.YouTubeDLBridge
import dev.jyotiraditya.dmt.data.remote.youtubedl.YouTubeDLResponse
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.Innertube
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.UserAgents
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.bodies.PlayerBody
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.requests.player
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeStreamResolver"

/**
 * Resolved YouTube stream URL with the User-Agent that was used to fetch it.
 */
data class ResolvedStream(
    val url: String,
    val userAgent: String = UserAgents.IOS,
    val contentLength: Long = 0L,
    val resolverName: String = "unknown",
)

/**
 * Resolves YouTube Music stream URLs using yt-dlp (primary) or Innertube (fallback).
 */
@Singleton
class YoutubeStreamResolver @Inject constructor(
    private val youTubeDLBridge: YouTubeDLBridge,
) {

    suspend fun resolve(videoId: String): ResolvedStream? {
        // 1. Try yt-dlp (PRIMARY) — handles JS signature deciphering
        val ytdl = resolveViaYtDlp(videoId)
        if (ytdl != null) return ytdl

        // 2. Fallback to Innertube player API
        val inner = resolveViaInnertube(videoId)
        if (inner != null) return inner

        Log.e(TAG, "All resolvers failed for $videoId")
        return null
    }

    private suspend fun resolveViaYtDlp(videoId: String): ResolvedStream? {
        if (!youTubeDLBridge.isReady()) {
            Log.w(TAG, "yt-dlp bridge not ready (not initialized)")
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
                // yt-dlp handles its own User-Agent, use a generic one for the DataSource
                ResolvedStream(url = url, contentLength = response.fileSize, resolverName = "yt-dlp")
            } else {
                response.formats
                    ?.firstOrNull { it.url != null }
                    ?.url
                    ?.let { ResolvedStream(url = it, resolverName = "yt-dlp") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp failed for $videoId: ${e.message}")
            null
        }
    }

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

            // Determine which context succeeded and use its User-Agent
            val contextName = response.context?.client?.clientName ?: "IOS"
            val userAgent = when (contextName) {
                "ANDROID_MUSIC" -> UserAgents.ANDROID_MUSIC
                "IOS" -> UserAgents.IOS
                "WEB_REMIX" -> UserAgents.DESKTOP
                "TVHTML5" -> UserAgents.TV
                else -> UserAgents.IOS
            }

            Log.d(TAG, "Innertube fallback ($contextName): ${format.mimeType} (${format.bitrate}kbps)")
            ResolvedStream(url = url, userAgent = userAgent, contentLength = format.contentLength ?: 0L, resolverName = "Innertube($contextName)")
        } catch (e: Exception) {
            Log.e(TAG, "Innertube failed: ${e.message}")
            null
        }
    }
}

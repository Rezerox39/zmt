package dev.abhi.zmt.data.remote.youtube

import android.util.Log
import dev.abhi.zmt.data.remote.youtubedl.YouTubeDLBridge
import dev.abhi.zmt.data.remote.youtubedl.YouTubeDLResponse
import dev.abhi.zmt.data.remote.youtube.innertube.Innertube
import dev.abhi.zmt.data.remote.youtube.innertube.models.Context
import dev.abhi.zmt.data.remote.youtube.innertube.models.UserAgents
import dev.abhi.zmt.data.remote.youtube.innertube.models.bodies.PlayerBody
import dev.abhi.zmt.data.remote.youtube.innertube.requests.player
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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
 *
 * Fallback chain:
 *   1. yt-dlp (best quality, handles JS signature deciphering)
 *   2. Innertube IOS context
 *   3. Innertube Web (WEB_REMIX) context
 *   4. Innertube Android Music context
 *   5. Innertube TV context
 *
 * [resolve] returns only the best first result.
 * [resolveAll] returns all viable options in priority order for retry.
 */
@Singleton
class YoutubeStreamResolver @Inject constructor(
    private val youTubeDLBridge: YouTubeDLBridge,
) {

    suspend fun resolve(videoId: String): ResolvedStream? {
        val ytdl = resolveViaYtDlp(videoId)
        if (ytdl != null) return ytdl

        val contexts = listOf(
            Context.DefaultIOS,
            Context.DefaultWeb,
            Context.DefaultAndroidMusic,
            Context.DefaultTV,
        )
        for (context in contexts) {
            if (!currentCoroutineContext().isActive) return null
            val stream = resolveViaInnertubeContext(videoId, context)
            if (stream != null) return stream
        }

        Log.e(TAG, "All resolvers failed for $videoId")
        return null
    }

    /**
     * Return ALL viable stream options for [videoId] in priority order.
     * Useful for retrying playback with alternate URLs if the first one fails.
     */
    suspend fun resolveAll(videoId: String): List<ResolvedStream> {
        val results = mutableListOf<ResolvedStream>()

        val ytdl = resolveViaYtDlp(videoId)
        if (ytdl != null) results.add(ytdl)

        val contexts = listOf(
            Context.DefaultIOS,
            Context.DefaultWeb,
            Context.DefaultAndroidMusic,
            Context.DefaultTV,
        )
        for (context in contexts) {
            if (!currentCoroutineContext().isActive) break
            val stream = resolveViaInnertubeContext(videoId, context)
            if (stream != null && results.none { it.url == stream.url }) {
                results.add(stream)
            }
        }

        Log.d(TAG, "resolveAll($videoId): ${results.size} options")
        return results
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

    private suspend fun resolveViaInnertubeContext(
        videoId: String,
        context: Context,
    ): ResolvedStream? {
        val label = context.client.clientName
        return try {
            val result = Innertube.player(
                body = PlayerBody(videoId = videoId, context = context),
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

            val userAgent = when (label) {
                "ANDROID_MUSIC" -> UserAgents.ANDROID_MUSIC
                "IOS" -> UserAgents.IOS
                "WEB_REMIX" -> UserAgents.DESKTOP
                "TVHTML5" -> UserAgents.TV
                else -> UserAgents.IOS
            }

            Log.d(TAG, "Innertube($label): ${format.mimeType} (${format.bitrate}kbps)")
            ResolvedStream(
                url = url,
                userAgent = userAgent,
                contentLength = format.contentLength ?: 0L,
                resolverName = "Innertube($label)",
            )
        } catch (e: Exception) {
            Log.w(TAG, "Innertube($label) failed for $videoId: ${e.message}")
            null
        }
    }
}

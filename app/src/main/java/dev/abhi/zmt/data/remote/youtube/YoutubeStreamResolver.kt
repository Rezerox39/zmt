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
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeStreamResolver"

data class ResolvedStream(
    val url: String,
    val userAgent: String = UserAgents.IOS,
    val contentLength: Long = 0L,
    val resolverName: String = "unknown",
)

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

            // Try the top-level URL first
            var url = response.url
            var formatId = response.formatId

            // Fallback: try the first format with a URL
            if (url == null && response.formats != null) {
                for (fmt in response.formats) {
                    if (fmt.url != null) {
                        url = fmt.url
                        formatId = fmt.formatId
                        break
                    }
                }
            }

            if (url == null) {
                Log.w(TAG, "yt-dlp returned no URL for $videoId")
                return null
            }

            // Quick HEAD check to validate the URL is actually reachable
            if (!isUrlReachable(url)) {
                Log.w(TAG, "yt-dlp URL not reachable for $videoId, skipping")
                return null
            }

            Log.d(TAG, "yt-dlp: format=$formatId, size=${response.fileSize}")
            ResolvedStream(
                url = url,
                contentLength = response.fileSize,
                resolverName = "yt-dlp($formatId)",
            )
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
                    // Prefer free audio formats: itag 140 (m4a 128k) or 251 (opus)
                    formats.findLast { it.itag == 140 }
                        ?: formats.findLast { it.itag == 251 }
                        ?: formats.findLast { it.mimeType.startsWith("audio/") }
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

            Log.d(TAG, "Innertube($label): ${format.mimeType} itag=${format.itag} (${format.bitrate}kbps)")
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

    /**
     * Quick HEAD request to verify a URL is reachable (validates it won't 404 on read).
     */
    private fun isUrlReachable(url: String): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (e: Exception) {
            Log.w(TAG, "URL reachability check failed: ${e.message}")
            false
        }
    }
}

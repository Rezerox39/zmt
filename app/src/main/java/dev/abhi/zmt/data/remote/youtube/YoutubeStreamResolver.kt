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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeStreamResolver"
private const val MAX_ATTEMPTS = 5
private const val RETRY_DELAY_MS = 200L

/**
 * Shared Innertube client contexts ordered by reliability.
 * VisionOS and AndroidVR bypass cipher; others are fallbacks.
 * Used by both [resolve] and [resolveAll].
 */
private val INNERTUBE_CONTEXTS = listOf(
    Context.DefaultVisionOS,
    Context.DefaultAndroidVR,
    Context.DefaultWeb,
    Context.DefaultAndroidMusic,
    Context.DefaultIOS,
    Context.DefaultTV,
)

data class ResolvedStream(
    val url: String,
    val userAgent: String = UserAgents.IOS,
    val contentLength: Long = 0L,
    val resolverName: String = "unknown",
    val httpHeaders: Map<String, String> = emptyMap(),
)

@Singleton
class YoutubeStreamResolver @Inject constructor(
    private val youTubeDLBridge: YouTubeDLBridge,
) {

    suspend fun resolve(videoId: String): ResolvedStream? {
        val ytdl = resolveViaYtDlp(videoId)
        if (ytdl != null) return ytdl

        for (context in INNERTUBE_CONTEXTS) {
            if (!currentCoroutineContext().isActive) return null
            val stream = resolveViaInnertubeContext(videoId, context)
            if (stream != null) return stream
            delay(RETRY_DELAY_MS)
        }

        Log.e(TAG, "All resolvers failed for $videoId")
        return null
    }

    suspend fun resolveAll(videoId: String): List<ResolvedStream> {
        val results = mutableListOf<ResolvedStream>()

        val ytdl = resolveViaYtDlp(videoId)
        if (ytdl != null) results.add(ytdl)

        for (context in INNERTUBE_CONTEXTS) {
            if (!currentCoroutineContext().isActive) break
            val stream = resolveViaInnertubeContext(videoId, context)
            if (stream != null && results.none { it.url == stream.url }) {
                results.add(stream)
            }
            delay(RETRY_DELAY_MS)
        }

        Log.d(TAG, "resolveAll($videoId): ${results.size} options")
        return results
    }

    private suspend fun resolveViaYtDlp(videoId: String): ResolvedStream? {
        if (!youTubeDLBridge.isReady()) {
            Log.w(TAG, "yt-dlp bridge not ready")
            return null
        }

        return try {
            val jsonStr = youTubeDLBridge.runDownload(videoId)
            if (jsonStr == null) {
                Log.w(TAG, "yt-dlp returned null for $videoId")
                return null
            }

            val response = YouTubeDLResponse.fromString(jsonStr)

            if (response.hasError) {
                Log.w(TAG, "yt-dlp error for $videoId: ${response.error}")
                return null
            }

            if (response.id != videoId) {
                Log.w(TAG, "yt-dlp returned wrong video ID: ${response.id}")
                return null
            }

            var url = response.url
            var formatId = response.formatId
            var fileSize = response.fileSize
            var headers = emptyMap<String, String>()

            if (url == null && response.formats != null) {
                val audioFormats = response.formats.filter { it.isAudioOnly && it.url != null }
                val bestAudio = audioFormats.maxByOrNull { it.audioBitrate ?: 0.0 }

                if (bestAudio != null) {
                    url = bestAudio.url
                    formatId = bestAudio.formatId
                    fileSize = bestAudio.fileSize ?: 0L
                    headers = bestAudio.httpHeaders ?: emptyMap()
                    Log.d(TAG, "yt-dlp: picked audio format ${bestAudio.formatId} (${bestAudio.audioBitrate}kbps)")
                } else {
                    val anyFormat = response.formats.firstOrNull { it.url != null }
                    if (anyFormat != null) {
                        url = anyFormat.url
                        formatId = anyFormat.formatId
                        fileSize = anyFormat.fileSize ?: 0L
                        headers = anyFormat.httpHeaders ?: emptyMap()
                        Log.d(TAG, "yt-dlp: picked format ${anyFormat.formatId}")
                    }
                }
            }

            if (url == null) {
                Log.w(TAG, "yt-dlp returned no URL for $videoId (${response.formats?.size ?: 0} formats)")
                return null
            }

            val userAgent = headers["User-Agent"] ?: UserAgents.DESKTOP

            Log.d(TAG, "yt-dlp success: format=$formatId, size=$fileSize")
            ResolvedStream(
                url = url,
                userAgent = userAgent,
                contentLength = fileSize,
                resolverName = "yt-dlp($formatId)",
                httpHeaders = headers,
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

            val formats = streamingData.adaptiveFormats
            if (formats.isNullOrEmpty()) return null

            val format = formats
                .filter { it.mimeType.startsWith("audio/") }
                .let { audioFormats ->
                    audioFormats.find { it.url != null && it.itag == 251 }
                        ?: audioFormats.find { it.url != null && it.itag == 140 }
                        ?: audioFormats.find { it.url != null }
                        ?: audioFormats.find { it.signatureCipher != null }
                } ?: return null

            val url = format.url
            if (url == null) {
                Log.d(TAG, "Innertube($label): format has signatureCipher only, skipping")
                return null
            }

            val userAgent = when (label) {
                "VISIONOS" -> UserAgents.VISIONOS
                "ANDROID_VR" -> UserAgents.ANDROID_VR
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
}

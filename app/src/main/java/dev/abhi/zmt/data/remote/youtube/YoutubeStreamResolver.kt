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
    val httpHeaders: Map<String, String> = emptyMap(),
)

@Singleton
class YoutubeStreamResolver @Inject constructor(
    private val youTubeDLBridge: YouTubeDLBridge,
) {

    suspend fun resolve(videoId: String): ResolvedStream? {
        // yt-dlp is the PRIMARY resolver — it handles cipher, n-transform, PO tokens
        val ytdl = resolveViaYtDlp(videoId)
        if (ytdl != null) return ytdl

        // Fallback: try Innertube clients (VISIONOS and AndroidVR don't need cipher)
        val contexts = listOf(
            Context.DefaultVisionOS,
            Context.DefaultAndroidVR,
            Context.DefaultWeb,
            Context.DefaultAndroidMusic,
            Context.DefaultIOS,
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

        // yt-dlp first
        val ytdl = resolveViaYtDlp(videoId)
        if (ytdl != null) results.add(ytdl)

        // Innertube clients
        val contexts = listOf(
            Context.DefaultVisionOS,
            Context.DefaultAndroidVR,
            Context.DefaultWeb,
            Context.DefaultAndroidMusic,
            Context.DefaultIOS,
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

            // Check for yt-dlp errors
            if (response.hasError) {
                Log.w(TAG, "yt-dlp error for $videoId: ${response.error}")
                return null
            }

            if (response.id != videoId) {
                Log.w(TAG, "yt-dlp returned wrong video ID: ${response.id}")
                return null
            }

            // Try top-level URL first
            var url = response.url
            var formatId = response.formatId
            var fileSize = response.fileSize
            var headers = emptyMap<String, String>()

            // If no top-level URL, find best audio format with URL
            if (url == null && response.formats != null) {
                // Prefer audio-only formats
                val audioFormats = response.formats.filter { it.isAudioOnly && it.url != null }
                val bestAudio = audioFormats.maxByOrNull { it.audioBitrate ?: 0.0 }

                if (bestAudio != null) {
                    url = bestAudio.url
                    formatId = bestAudio.formatId
                    fileSize = bestAudio.fileSize ?: 0L
                    headers = bestAudio.httpHeaders ?: emptyMap()
                    Log.d(TAG, "yt-dlp: picked audio format ${bestAudio.formatId} (${bestAudio.audioBitrate}kbps)")
                } else {
                    // Fallback: any format with URL
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

            // Validate URL is reachable
            if (!isUrlReachable(url)) {
                Log.w(TAG, "yt-dlp URL not reachable for $videoId")
                return null
            }

            // Extract User-Agent from headers if available, or use a default
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

            // Try direct URL formats first, then signatureCipher
            val formats = streamingData.adaptiveFormats
            if (formats.isNullOrEmpty()) return null

            // Find best audio format with direct URL
            val format = formats
                .filter { it.mimeType.startsWith("audio/") }
                .let { audioFormats ->
                    audioFormats.find { it.url != null && it.itag == 251 }
                        ?: audioFormats.find { it.url != null && it.itag == 140 }
                        ?: audioFormats.find { it.url != null }
                        // If no direct URL, try signatureCipher (needs cipher deobfuscation)
                        ?: audioFormats.find { it.signatureCipher != null }
                } ?: return null

            val url = format.url
            if (url == null) {
                // Format has signatureCipher but no direct URL — we can't handle this
                // without cipher deobfuscation, skip it
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

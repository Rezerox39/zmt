package dev.jyotiraditya.dmt.data.remote.youtube

import android.util.Log
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.Innertube
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.UserAgents
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.bodies.PlayerBody
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.requests.player
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeStreamResolver"

/**
 * Resolved YouTube stream URL with the User-Agent required for playback.
 */
data class ResolvedStream(
    val url: String,
    val userAgent: String,
)

/**
 * Piped API /streams response.
 * Piped wraps yt-dlp internally, giving reliable playable URLs.
 */
@Serializable
data class PipedStreamResponse(
    val title: String? = null,
    @SerialName("audioStreams")
    val audioStreams: List<PipedAudioStream>? = null,
    val duration: Long? = null,
) {
    @Serializable
    data class PipedAudioStream(
        val url: String,
        val format: String,
        @SerialName("mimeType")
        val mimeType: String? = null,
        val bitrate: Long? = null,
    )
}

/**
 * Resolves YouTube Music stream URLs.
 *
 * Strategy mirrors ViTune: use yt-dlp quality streams via the Piped API
 * (which wraps yt-dlp internally) for reliable playback, with Innertube
 * as fallback.
 *
 * Piped instances provide the same yt-dlp resolved URLs without needing
 * to bundle Python/yt-dlp in the APK.
 */
@Singleton
class YoutubeStreamResolver @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient {
        // Minimal config - Piped API doesn't need special headers
    }

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
    )

    // In-memory cache: videoId -> (stream, timestamp)
    private val urlCache = mutableMapOf<String, Pair<ResolvedStream, Long>>()
    private val cacheTtlMs = 15 * 60 * 1000L // 15 min

    suspend fun resolve(videoId: String): ResolvedStream? {
        // 1. Check in-memory cache
        urlCache[videoId]?.let { (stream, timestamp) ->
            if (System.currentTimeMillis() - timestamp < cacheTtlMs) {
                Log.d(TAG, "Cache hit for $videoId")
                return stream
            }
        }

        // 2. Try Piped API (wraps yt-dlp internally — same quality as ViTune)
        val piped = resolveViaPiped(videoId)
        if (piped != null) {
            urlCache[videoId] = piped to System.currentTimeMillis()
            return piped
        }

        // 3. Fallback to Innertube
        val inner = resolveViaInnertube(videoId)
        if (inner != null) {
            urlCache[videoId] = inner to System.currentTimeMillis()
            return inner
        }

        Log.e(TAG, "All resolvers failed for $videoId")
        return null
    }

    /**
     * Resolve via Piped API. Piped wraps yt-dlp, producing the same reliable
     * stream URLs that ViTune uses for playback.
     */
    private suspend fun resolveViaPiped(videoId: String): ResolvedStream? {
        for (instance in pipedInstances) {
            try {
                val response = httpClient.get("$instance/streams/$videoId")
                val body = response.bodyAsText()
                val parsed = json.decodeFromString<PipedStreamResponse>(body)

                val audio = parsed.audioStreams
                    ?.filter { it.url.isNotBlank() }
                    ?.let { streams ->
                        // Prefer opus high bitrate, then m4a, then highest bitrate
                        streams.find { it.format.contains("opus") && (it.bitrate ?: 0) >= 128 }
                            ?: streams.find { it.format.contains("m4a") }
                            ?: streams.maxByOrNull { it.bitrate ?: 0 }
                    }

                if (audio != null) {
                    Log.d(TAG, "Piped: ${audio.format} @ ${audio.bitrate}kbps")
                    return ResolvedStream(
                        url = audio.url,
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0",
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Piped $instance failed: ${e.message}")
            }
        }
        return null
    }

    /**
     * Fallback: resolve via Innertube player API.
     */
    private suspend fun resolveViaInnertube(videoId: String): ResolvedStream? {
        return try {
            val result = Innertube.player(
                body = PlayerBody(videoId = videoId),
                checkIsValid = true,
            )
            val response = result?.getOrNull() ?: return null
            val streamingData = response.streamingData ?: return null
            val format = streamingData.highestQualityFormat ?: return null
            val url = format.url ?: return null

            val userAgent = response.context?.client?.userAgent
                ?: UserAgents.ANDROID_MUSIC

            Log.d(TAG, "Innertube: ${format.mimeType}")
            ResolvedStream(url = url, userAgent = userAgent)
        } catch (e: Exception) {
            Log.e(TAG, "Innertube failed: ${e.message}")
            null
        }
    }

    fun clearCache() {
        urlCache.clear()
    }
}

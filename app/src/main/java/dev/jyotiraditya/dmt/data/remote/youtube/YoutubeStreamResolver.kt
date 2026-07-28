package dev.jyotiraditya.dmt.data.remote.youtube

import android.net.Uri
import android.util.Log
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.Innertube
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.bodies.PlayerBody
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.requests.player
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeStreamResolver"

/**
 * Resolved YouTube stream URL.
 *
 * User-Agent is set to a generic Firefox desktop browser string,
 * matching ViTune's approach. YouTube CDN does NOT require the
 * Innertube client context User-Agent — a standard browser UA works.
 */
data class ResolvedStream(
    val url: String,
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0",
)

/**
 * Resolves YouTube Music stream URLs.
 *
 * Strategy matches ViTune:
 * - Primary: Innertube player API with IOS→Web→AndroidMusic→TV contexts
 * - User-Agent: Firefox desktop browser (not Innertube context UA)
 * - Returns direct playable URLs without signatureCipher
 */
@Singleton
class YoutubeStreamResolver @Inject constructor() {

    // In-memory cache: videoId -> (streamUrl, timestamp)
    private val urlCache = mutableMapOf<String, Pair<String, Long>>()
    private val cacheTtlMs = 15 * 60 * 1000L // 15 min

    /**
     * Resolve a YouTube videoId to a playable stream URL.
     *
     * Uses Innertube player API with multiple client contexts.
     * Returns the first context that provides a direct URL (no signatureCipher).
     */
    suspend fun resolve(videoId: String): ResolvedStream? {
        // 1. Check in-memory cache
        urlCache[videoId]?.let { (url, timestamp) ->
            if (System.currentTimeMillis() - timestamp < cacheTtlMs) {
                Log.d(TAG, "Cache hit for $videoId")
                return ResolvedStream(url = url)
            }
        }

        // 2. Resolve via Innertube player API
        val url = resolveViaInnertube(videoId)
        if (url != null) {
            urlCache[videoId] = url to System.currentTimeMillis()
            return ResolvedStream(url = url)
        }

        Log.e(TAG, "All resolvers failed for $videoId")
        return null
    }

    /**
     * Resolve via Innertube player API.
     *
     * Tries contexts in order: IOS → Web → AndroidMusic → TV
     * Only returns formats with a direct [url] field (no signatureCipher).
     */
    private suspend fun resolveViaInnertube(videoId: String): String? {
        return try {
            val result = Innertube.player(
                body = PlayerBody(videoId = videoId),
                checkIsValid = true,
            )
            val response = result?.getOrNull() ?: return null
            val streamingData = response.streamingData ?: return null

            // Only use formats with a direct URL — skip signatureCipher-only formats
            val format = streamingData.adaptiveFormats
                ?.filter { it.url != null }
                ?.let { formats ->
                    formats.findLast { it.itag == 251 || it.itag == 140 }
                        ?: formats.maxByOrNull { it.bitrate ?: 0L }
                } ?: return null

            val url = format.url ?: return null

            Log.d(TAG, "Innertube: ${format.mimeType} (${format.bitrate}kbps), itag=${format.itag}")
            url
        } catch (e: Exception) {
            Log.e(TAG, "Innertube failed: ${e.message}")
            null
        }
    }

    fun clearCache() {
        urlCache.clear()
    }
}

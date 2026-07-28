package dev.jyotiraditya.dmt.data.remote.youtube

import android.util.Log
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.Innertube
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.Context
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.UserAgents
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.bodies.PlayerBody
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.requests.player
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeStreamResolver"

data class ResolvedStream(
    val url: String,
    val userAgent: String,
)

@Singleton
class YoutubeStreamResolver @Inject constructor() {

    suspend fun resolve(videoId: String): ResolvedStream? {
        val body = PlayerBody(videoId = videoId)

        return try {
            val result = Innertube.player(body = body, checkIsValid = true)
            val response = result?.getOrNull() ?: return null
            val streamingData = response.streamingData ?: return null
            val format = streamingData.highestQualityFormat ?: return null
            val url = format.url

            if (url == null) {
                Log.w(TAG, "No direct URL for $videoId (signatureCipher only)")
                return null
            }

            val context = response.context
            val userAgent = context?.client?.userAgent
                ?: Context.UserAgents.ANDROID_MUSIC

            Log.d(TAG, "Resolved stream for $videoId (${format.mimeType}, ${format.bitrate}bps, UA=${userAgent.take(40)}...)")
            ResolvedStream(url = url, userAgent = userAgent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve stream for $videoId: ${e.message}")
            null
        }
    }
}

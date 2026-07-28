package dev.jyotiraditya.dmt.data.remote.youtube

import android.util.Log
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.Innertube
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.bodies.PlayerBody
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.requests.player
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeStreamResolver"

@Singleton
class YoutubeStreamResolver @Inject constructor() {

    suspend fun resolve(videoId: String): String? {
        val body = PlayerBody(videoId = videoId)

        return try {
            val result = Innertube.player(body = body, checkIsValid = true)
            val response = result?.getOrNull() ?: return null
            val streamingData = response.streamingData ?: return null
            val format = streamingData.highestQualityFormat ?: return null
            val url = format.url

            if (url != null) {
                Log.d(TAG, "Resolved stream for $videoId (${format.mimeType}, ${format.bitrate}bps)")
            }
            url
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve stream for $videoId: ${e.message}")
            null
        }
    }
}

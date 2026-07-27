package dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.bodies

import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlayerBody(
    val context: Context = Context.DefaultAndroidMusic,
    val videoId: String,
    val playlistId: String? = null,
    val cpn: String? = null,
    val contentCheckOk: String = "true",
    val racyCheckOn: String = "true",
    val playbackContext: PlaybackContext? = null
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext? = null
    ) {
        @Serializable
        data class ContentPlaybackContext(
            val signatureTimestamp: String? = null
        )
    }
}

package dev.abhi.zmt.data.remote.youtubedl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class YouTubeDLResponse(
    val id: String,
    @SerialName("format_id")
    val formatId: String? = null,
    val url: String? = null,
    val ext: String? = null,
    @SerialName("filesize")
    val fileSize: Long = 0L,
    @SerialName("acodec")
    val audioCodec: String? = null,
    @SerialName("abr")
    val audioBitrate: Double? = null,
    @SerialName("tbr")
    val totalBitrate: Double? = null,
    val duration: Double? = null,
    val title: String? = null,
    val error: String? = null,
    val formats: List<Format>? = null,
) {
    companion object {
        val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
        fun fromString(str: String) = json.decodeFromString<YouTubeDLResponse>(str)
    }

    val hasError: Boolean get() = error != null

    @Serializable
    data class Format(
        @SerialName("format_id")
        val formatId: String,
        val url: String? = null,
        val ext: String? = null,
        @SerialName("acodec")
        val audioCodec: String? = null,
        @SerialName("vcodec")
        val videoCodec: String? = null,
        @SerialName("abr")
        val audioBitrate: Double? = null,
        @SerialName("tbr")
        val totalBitrate: Double? = null,
        @SerialName("filesize")
        val fileSize: Long? = null,
        @SerialName("format_note")
        val formatNote: String? = null,
        val protocol: String? = null,
        @SerialName("http_headers")
        val httpHeaders: Map<String, String>? = null,
    ) {
        val isAudioOnly: Boolean get() = videoCodec == "none" || videoCodec == null
    }
}

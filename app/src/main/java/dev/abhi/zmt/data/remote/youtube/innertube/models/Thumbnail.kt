package dev.abhi.zmt.data.remote.youtube.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnail(
    val url: String,
    val height: Int?,
    val width: Int?
) {
    fun size(size: Int) = when {
        url.startsWith("https://lh3.googleusercontent.com") -> "$url-w$size-h$size"
        url.startsWith("https://yt3.ggpht.com") -> "$url-s$size"
        else -> url
    }

    /**
     * Returns the highest-resolution variant of this thumbnail so album art
     * stays crisp in raw-artwork mode. Google-hosted images accept a size
     * suffix; YouTube i.ytimg.com URLs accept quality keywords.
     */
    fun hd(): String = when {
        url.startsWith("https://lh3.googleusercontent.com") || url.startsWith("https://yt3.ggpht.com") ->
            size(this@Thumbnail.hdSize())
        url.contains("i.ytimg.com/vi/") ->
            url.substringBeforeLast("/") + "/maxresdefault.jpg"
        else -> url
    }

    private fun hdSize(): Int {
        val base = (width ?: height) ?: return 1440
        return if (base >= 1200) base else 1440
    }
}

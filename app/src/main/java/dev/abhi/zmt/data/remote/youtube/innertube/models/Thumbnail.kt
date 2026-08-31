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
     * Returns a full-resolution variant of this thumbnail so album art stays
     * crisp in raw-artwork mode.
     *
     * Google-hosted images (yt3.googleusercontent.com / yt3.ggpht.com) carry a
     * size segment like `=w544-h544-l90-rj`; we REPLACE it with a large size
     * so the CDN returns the true high-res source instead of the small crop.
     * YouTube i.ytimg.com video thumbnails are upgraded to maxresdefault.
     */
    fun hd(): String {
        if (url.startsWith("https://yt3.googleusercontent.com") ||
            url.startsWith("https://yt3.ggpht.com") ||
            url.startsWith("https://lh3.googleusercontent.com")) {
            return url.substringBefore("=") + "=w1440-h1440-l90-rj"
        }
        REGEX_YTIMG.find(url)?.groupValues?.getOrNull(1)?.let { base ->
            return base + "/maxresdefault.jpg"
        }
        return url
    }

    private companion object {
        val REGEX_YTIMG = Regex("(https://i\\.ytimg\\.com/vi/[^/]+)/[a-z0-9]+\\.jpg")
    }
}

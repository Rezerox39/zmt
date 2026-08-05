package dev.abhi.zmt.domain.model

import androidx.compose.runtime.Immutable

/**
 * Group of tracks that share the same title + artist across any source
 * (local, Telegram, YouTube, Jellyfin). The first track is the recommended
 * keep — highest container quality, then bitrate.
 */
@Immutable
data class DuplicateGroup(
    val title: String,
    val artist: String,
    val tracks: List<Track>,
    val wastedBytes: Long,
)

private val CONTAINER_RANK = mapOf(
    "flac" to 100, "wav" to 100, "ape" to 100, "alac" to 96,
    "opus" to 84, "m4a" to 78, "aac" to 76, "ogg" to 66,
    "mpeg" to 60, "mp3" to 60,
)

/** Quality score used to decide which duplicate to keep. */
fun trackQualityRank(track: Track): Int {
    val mime = track.mime.lowercase()
    val container = when {
        mime.contains("flac") -> "flac"
        mime.contains("wav") -> "wav"
        mime.contains("ape") -> "ape"
        mime.contains("alac") -> "alac"
        mime.contains("opus") -> "opus"
        mime.contains("m4a") || mime.contains("mp4") -> "m4a"
        mime.contains("aac") -> "aac"
        mime.contains("ogg") -> "ogg"
        mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
        else -> ""
    }
    val base = CONTAINER_RANK[container] ?: 40
    val kbps = track.bitrate / 1000
    val bonus = when {
        kbps >= 320 -> 20
        kbps >= 256 -> 15
        kbps >= 192 -> 10
        kbps >= 128 -> 5
        else -> 0
    }
    return base + bonus
}

/** Detects duplicate groups, best quality first, largest waste first. */
fun findDuplicates(tracks: List<Track>): List<DuplicateGroup> =
    tracks
        .groupBy { duplicateKey(it) }
        .values
        .mapNotNull { group ->
            if (group.size < 2) return@mapNotNull null
            val sorted = group.sortedByDescending { trackQualityRank(it) }
            DuplicateGroup(
                title = sorted.first().title,
                artist = sorted.first().artist,
                tracks = sorted,
                wastedBytes = sorted.drop(1).sumOf { it.size },
            )
        }
        .sortedByDescending { it.wastedBytes }

private fun duplicateKey(track: Track): String {
    val title = normalizeTitle(track.title)
    val artist = track.artist.trim().lowercase()
    return "$title\u00b7$artist"
}

private fun normalizeTitle(title: String): String {
    var t = title.trim().lowercase()
    t = t.replace(Regex("""\s*\(\s*\d+\s*\)\s*$"""), "")
    t = t.replace(Regex("""\s*-\s*copy\s*$"""), "")
    return t.trim()
}

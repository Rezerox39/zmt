package dev.abhi.zmt.core.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.abhi.zmt.domain.model.Track
import dev.abhi.zmt.domain.model.TrackSource
import dev.abhi.zmt.ui.theme.TuiAccent
import dev.abhi.zmt.ui.theme.TuiDim
import dev.abhi.zmt.ui.theme.TuiGreen
import dev.abhi.zmt.ui.theme.TuiRed

private val SOURCE_LABELS = mapOf(
    TrackSource.LOCAL to "loc",
    TrackSource.JELLYFIN to "jf",
    TrackSource.TELEGRAM to "tg",
    TrackSource.YOUTUBE to "yt",
)

private val SOURCE_COLORS = mapOf(
    TrackSource.LOCAL to TuiGreen,
    TrackSource.JELLYFIN to TuiAccent,
    TrackSource.TELEGRAM to TuiAccent,
    TrackSource.YOUTUBE to TuiRed,
)

/**
 * Derive a compact quality label from the track's MIME type and bitrate.
 * Falls back gracefully when metadata is missing (e.g. YouTube streams).
 */
fun trackQualityLabel(track: Track): String? {
    val mime = track.mime.lowercase()
    val kbps = if (track.bitrate > 0) track.bitrate / 1000 else 0
    // Streaming sources (YouTube) don't carry reliable bitrate metadata — skip badge
    if (track.source == TrackSource.YOUTUBE && kbps == 0) return null
    return when {
        mime.contains("flac") -> "flac"
        mime.contains("opus") -> "opus"
        mime.contains("ogg") -> "ogg"
        mime.contains("aac") -> "aac"
        mime.contains("mp4") || mime.contains("m4a") -> "m4a"
        mime.contains("mpeg") || mime.contains("mp3") ->
            if (kbps > 0) "${kbps.coerceAtMost(320)}k" else "mp3"
        kbps > 0 -> "${kbps.coerceAtMost(320)}k"
        else -> null
    }
}

/**
 * Terminal-styled source + quality badges for a track row.
 * Renders as small bordered chips, e.g. [yt 128k] or [flac].
 */
@Composable
fun TrackBadges(
    track: Track,
    modifier: Modifier = Modifier,
) {
    val sourceLabel = SOURCE_LABELS[track.source] ?: "?"
    val sourceColor = SOURCE_COLORS[track.source] ?: TuiDim
    val quality = trackQualityLabel(track)

    Row(modifier = modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            text = sourceLabel,
            style = MaterialTheme.typography.labelSmall,
            color = sourceColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        if (quality != null) {
            Text(
                text = quality,
                style = MaterialTheme.typography.labelSmall,
                color = TuiDim,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
    }
}

package dev.abhi.zmt.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.abhi.zmt.core.common.Caption
import dev.abhi.zmt.core.common.HeaderAction
import dev.abhi.zmt.core.common.ListRow
import dev.abhi.zmt.core.common.ScrollMemory
import dev.abhi.zmt.core.common.TrackBadges
import dev.abhi.zmt.core.common.tuiClickable
import dev.abhi.zmt.domain.model.Playlist
import dev.abhi.zmt.domain.model.Track
import dev.abhi.zmt.domain.model.asCredit
import dev.abhi.zmt.presentation.player.DmtAction
import dev.abhi.zmt.presentation.player.DmtState
import dev.abhi.zmt.ui.theme.TuiAccent
import dev.abhi.zmt.ui.theme.TuiBright
import dev.abhi.zmt.ui.theme.TuiDim
import dev.abhi.zmt.ui.theme.TuiFaint
import dev.abhi.zmt.ui.theme.TuiFg
import dev.abhi.zmt.ui.theme.TuiLine
import dev.abhi.zmt.ui.theme.TuiRaised
import dev.abhi.zmt.util.asTime

/**
 * Shows playlists imported from Spotify and YouTube Music.
 * Detected by name prefix: "spotify-" or "ytm-".
 */
@Composable
fun ImportedPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    val imported = state.playlists.filter { isImported(it.name) }
    val spotify = imported.filter { it.name.startsWith("spotify") }
    val ytm = imported.filter { it.name.startsWith("ytm") }
    val totalTracks = imported.sumOf { it.tracks.size }

    ScrollMemory("imported") {
        Column {
            Caption("imported playlists")

            if (imported.isEmpty()) {
                Text(
                    text = "no imported playlists yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TuiFaint,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "import from settings > import playlist",
                    style = MaterialTheme.typography.labelSmall,
                    color = TuiDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
                return@Column
            }

            // Stats row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            ) {
                StatBadge(label = "total", value = "${imported.size}")
                StatBadge(label = "tracks", value = "$totalTracks")
                StatBadge(label = "spotify", value = "${spotify.size}")
                StatBadge(label = "ytm", value = "${ytm.size}")
            }

            if (spotify.isNotEmpty()) {
                SectionHeader(label = "spotify")
                spotify.forEach { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        tag = "spotify",
                        onPlay = { dispatch(DmtAction.PlayAt(playlist.tracks, 0)) },
                    )
                }
            }

            if (ytm.isNotEmpty()) {
                SectionHeader(label = "youtube music")
                ytm.forEach { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        tag = "ytm",
                        onPlay = { dispatch(DmtAction.PlayAt(playlist.tracks, 0)) },
                    )
                }
            }

            // All imported tracks
            val allTracks = imported.flatMap { it.tracks }
            if (allTracks.isNotEmpty()) {
                HorizontalDivider(
                    color = TuiLine,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                SectionHeader(label = "all imported tracks")
                LazyColumn(
                    modifier = Modifier.heightIn(max = 600.dp),
                ) {
                    itemsIndexed(allTracks, key = { _, t -> t.id }) { index, track ->
                        ListRow(
                            title = track.title,
                            subtitle = "${track.artist.asCredit()} · ${track.durationMs.asTime()}",
                            badges = { TrackBadges(track = track) },
                        ) {
                            dispatch(DmtAction.PlayAt(allTracks, index))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(TuiAccent)
        )
        Text(
            text = " $label",
            style = MaterialTheme.typography.labelMedium,
            color = TuiDim,
        )
    }
}

@Composable
private fun StatBadge(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(1.dp, TuiLine)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = TuiFaint,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = TuiBright,
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    tag: String,
    onPlay: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TuiLine)
            .background(TuiRaised)
            .tuiClickable(onPlay)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "[$tag]",
            style = MaterialTheme.typography.labelSmall,
            color = TuiAccent,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TuiBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.tracks.size} tracks",
                style = MaterialTheme.typography.labelSmall,
                color = TuiDim,
            )
        }
        Text(
            text = "|>",
            style = MaterialTheme.typography.labelMedium,
            color = TuiAccent,
        )
    }
}

private fun isImported(name: String): Boolean =
    name.startsWith("spotify") || name.startsWith("ytm")


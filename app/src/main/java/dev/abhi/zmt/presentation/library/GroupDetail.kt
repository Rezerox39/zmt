package dev.abhi.zmt.presentation.library

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.abhi.zmt.core.common.ListRow
import dev.abhi.zmt.core.common.SubdirHeader
import dev.abhi.zmt.core.common.TrackBadges
import dev.abhi.zmt.domain.model.Album
import dev.abhi.zmt.domain.model.Track
import dev.abhi.zmt.presentation.player.DmtAction
import dev.abhi.zmt.presentation.player.DmtState

@Composable
internal fun <T> GroupDetail(
    spec: GroupSpec<T>,
    item: T,
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
) {
    var openAlbum by remember(spec.key(item)) { mutableStateOf<Album?>(null) }
    val album = openAlbum

    if (album != null) {
        TrackListDetail(
            title = album.name,
            meta = album.artist.lowercase(),
            counts = "${album.tracks.size} trk · ${totalTime(album.tracks)}",
            tracks = album.tracks,
            trackMeta = spec.trackMeta,
            nowPlayingId = state.nowPlayingId,
            onBack = { openAlbum = null },
            dispatch = dispatch,
        )
        return
    }

    when (val children = spec.children(item)) {
        is GroupChildren.Albums -> AlbumListDetail(
            title = spec.title(item),
            meta = spec.detailMeta(item).lowercase(),
            counts = listOf(spec.countLead(item), "${children.flatten().size} trk", totalTime(children.flatten()))
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            albums = children.albums,
            onBack = { dispatch(spec.open(null)) },
            onOpenAlbum = { openAlbum = it },
        )

        is GroupChildren.Tracks -> TrackListDetail(
            title = spec.title(item),
            meta = spec.detailMeta(item).lowercase(),
            counts = listOf(spec.countLead(item), "${children.tracks.size} trk", totalTime(children.tracks))
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            tracks = children.tracks,
            trackMeta = spec.trackMeta,
            nowPlayingId = state.nowPlayingId,
            onBack = { dispatch(spec.open(null)) },
            dispatch = dispatch,
        )
    }
}

@Composable
private fun AlbumListDetail(
    title: String,
    meta: String,
    counts: String,
    albums: List<Album>,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
) {
    LazyColumn {
        item {
            SubdirHeader(
                title = title,
                meta = meta,
                counts = counts,
                onBack = onBack,
            )
        }
        itemsIndexed(albums, key = { _, a -> a.name }) { index, a ->
            ListRow(
                index = index,
                line1 = a.name,
                line2 = "${a.tracks.size} trk",
                current = false,
                onClick = { onOpenAlbum(a) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun TrackListDetail(
    title: String,
    meta: String,
    counts: String,
    tracks: List<Track>,
    trackMeta: (Track) -> String,
    nowPlayingId: String?,
    onBack: () -> Unit,
    dispatch: (DmtAction) -> Unit,
) {
    LazyColumn {
        item {
            SubdirHeader(
                title = title,
                meta = meta,
                counts = counts,
                onBack = onBack,
            )
        }
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            ListRow(
                index = index,
                line1 = track.title,
                line2 = trackMeta(track),
                current = track.id.toString() == nowPlayingId,
                onClick = { dispatch(DmtAction.PlayAt(tracks, index)) },
                trailing = { TrackBadges(track) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

internal fun totalTime(tracks: List<Track>): String {
    val minutes = tracks.sumOf { it.durationMs } / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

package dev.abhi.zmt.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.abhi.zmt.R
import dev.abhi.zmt.core.common.Caption
import dev.abhi.zmt.core.common.HeaderAction
import dev.abhi.zmt.core.common.ListRow
import dev.abhi.zmt.core.common.TrackBadges
import dev.abhi.zmt.core.common.NewEntryRow
import dev.abhi.zmt.core.common.ScrollMemory
import dev.abhi.zmt.core.common.SearchRow
import dev.abhi.zmt.core.common.SubdirHeader
import dev.abhi.zmt.core.common.TuiKey
import dev.abhi.zmt.core.common.tuiClickable
import dev.abhi.zmt.domain.model.Playlist
import dev.abhi.zmt.presentation.player.DmtAction
import dev.abhi.zmt.presentation.player.DmtState
import dev.abhi.zmt.presentation.player.SheetHeader
import dev.abhi.zmt.presentation.player.TuiSheet
import dev.abhi.zmt.ui.theme.TuiAccent
import dev.abhi.zmt.ui.theme.TuiFaint
import dev.abhi.zmt.ui.theme.TuiFg
import dev.abhi.zmt.util.asTime

@Composable
fun PlaylistsPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    val playlist: Playlist? = state.playlists.find { it.name == state.openPlaylist }

    ScrollMemory(state.openPlaylist ?: "list") {
        if (playlist == null) {
            PlaylistList(state, dispatch)
        } else {
            PlaylistDetail(playlist, state, dispatch)
        }
    }
}

@Composable
private fun PlaylistList(state: DmtState, dispatch: (DmtAction) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    if (showCreate) {
        CreateSheet(
            onCreate = { name ->
                dispatch(DmtAction.CreatePlaylist(name))
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }

    var sheetPlaylist by remember { mutableStateOf<Playlist?>(null) }
    sheetPlaylist?.let { p ->
        TuiSheet(onDismiss = { sheetPlaylist = null }) {
            SheetHeader(title = p.name.lowercase())
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                if (p.tracks.isNotEmpty()) {
                    TuiKey(label = "[ ${stringResource(R.string.action_play)} ]") {
                        dispatch(DmtAction.PlayAt(p.tracks, 0))
                        sheetPlaylist = null
                    }
                    TuiKey(label = "[ ${stringResource(R.string.action_queue)} ]") {
                        dispatch(DmtAction.Enqueue(p.tracks, p.name))
                        sheetPlaylist = null
                    }
                }
                TuiKey(label = "[ ${stringResource(R.string.action_delete)} ]") {
                    dispatch(DmtAction.DeletePlaylist(p.name))
                    sheetPlaylist = null
                }
            }
        }
    }

    Column {
        SearchRow(
            query = state.query,
            hint = pluralStringResource(
                R.plurals.search_playlists_hint,
                state.playlists.size,
                state.playlists.size,
            ),
            shown = state.filteredPlaylists.size,
            onQuery = { dispatch(DmtAction.Query(it)) },
        )
        LazyColumn {
            item {
                NewEntryRow(
                    label = stringResource(R.string.playlist_new_title),
                    onClick = { showCreate = true },
                )
            }
            if (state.filteredPlaylists.isEmpty()) {
                item {
                    Caption(
                        stringResource(
                            if (state.playlists.isEmpty()) {
                                R.string.no_playlists
                            } else {
                                R.string.no_match
                            },
                        ),
                    )
                }
            }
            itemsIndexed(state.filteredPlaylists, key = { _, p -> p.name }) { index, p ->
                ListRow(
                    index = index,
                    line1 = p.name,
                    line2 = "${p.tracks.size} trk",
                    current = false,
                    onClick = { dispatch(DmtAction.OpenPlaylist(p.name)) },
                    onLongClick = { sheetPlaylist = p },
                    trailing = {
                        Text(
                            text = stringResource(R.string.open_album),
                            style = MaterialTheme.typography.labelMedium,
                            color = TuiFaint,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        PickerSheet(
            playlist = playlist,
            state = state,
            dispatch = dispatch,
            onDismiss = { showPicker = false },
        )
    }

    LazyColumn {
        item {
            SubdirHeader(
                title = playlist.name,
                meta = "${playlist.tracks.size} trk".lowercase(),
                onBack = { dispatch(DmtAction.OpenPlaylist(null)) },
                action = {
                    HeaderAction(
                        label = "+ ${stringResource(R.string.playlist_add)}",
                        onClick = { showPicker = true },
                    )
                },
            )
        }
        itemsIndexed(playlist.tracks) { index, track ->
            ListRow(
                index = index,
                line1 = track.title,
                line2 = "${track.artist} · ${track.durationMs.asTime()}".lowercase(),
                current = track.id.toString() == state.nowPlayingId,
                onClick = { dispatch(DmtAction.PlayAt(playlist.tracks, index)) },
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        TrackBadges(track)
                    }
                    Text(
                        text = stringResource(R.string.clear),
                        style = MaterialTheme.typography.labelMedium,
                        color = TuiFaint,
                        modifier = Modifier
                            .tuiClickable {
                                dispatch(
                                    DmtAction.RemoveFromPlaylist(playlist.name, track.path),
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun CreateSheet(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }

    TuiSheet(onDismiss = onDismiss) {
        SheetHeader(title = stringResource(R.string.playlist_new_title))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(
                text = " > ",
                style = MaterialTheme.typography.bodyLarge,
                color = TuiAccent,
            )
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TuiFg),
                cursorBrush = SolidColor(TuiAccent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (name.isEmpty()) {
                        Text(
                            text = stringResource(R.string.playlist_name_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TuiFaint,
                        )
                    }
                    inner()
                },
            )
            TuiKey(label = "[ ${stringResource(R.string.playlist_create)} ]") {
                if (name.isNotBlank()) onCreate(name.trim())
            }
        }
    }
}

@Composable
private fun PickerSheet(
    playlist: Playlist,
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val inPlaylist = playlist.tracks.map { it.path }.toSet()
    val candidates = state.tracks.filter { it.path.isNotEmpty() && it.path !in inPlaylist }

    TuiSheet(onDismiss = onDismiss) {
        SheetHeader(
            title = stringResource(R.string.playlist_add_title),
            meta = playlist.name.lowercase(),
        )
        if (candidates.isEmpty()) {
            Caption(stringResource(R.string.no_match))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        ) {
            itemsIndexed(candidates, key = { _, track -> track.id }) { index, track ->
                ListRow(
                    index = index,
                    line1 = track.title,
                    line2 = "${track.artist} · ${track.durationMs.asTime()}".lowercase(),
                    current = false,
                    onClick = { dispatch(DmtAction.AddToPlaylist(playlist.name, track)) },
                )
            }
        }
    }
}

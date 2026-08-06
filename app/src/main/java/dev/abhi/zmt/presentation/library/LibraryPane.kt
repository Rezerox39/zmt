package dev.abhi.zmt.presentation.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.abhi.zmt.R
import dev.abhi.zmt.core.common.Caption
import dev.abhi.zmt.core.common.ListRow
import dev.abhi.zmt.core.common.SearchRow
import dev.abhi.zmt.core.common.TrackBadges
import dev.abhi.zmt.core.common.TuiKey
import dev.abhi.zmt.core.common.tuiClickable
import dev.abhi.zmt.domain.model.SourceMode
import dev.abhi.zmt.domain.model.Track
import dev.abhi.zmt.domain.model.TrackSource
import dev.abhi.zmt.presentation.player.DmtAction
import dev.abhi.zmt.presentation.player.DmtState
import dev.abhi.zmt.presentation.player.LibrarySection
import dev.abhi.zmt.presentation.player.SheetHeader
import dev.abhi.zmt.presentation.player.TuiSheet
import dev.abhi.zmt.ui.theme.TuiAccent
import dev.abhi.zmt.ui.theme.TuiDim
import dev.abhi.zmt.ui.theme.TuiFaint
import dev.abhi.zmt.util.asTime

@Composable
fun LibraryPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    val isYouTube = state.settings.sourceMode == SourceMode.YOUTUBE

    Column {
        SearchRow(
            query = state.query,
            hint = if (isYouTube) {
                "search youtube music..."
            } else {
                pluralStringResource(
                    R.plurals.search_tracks_hint,
                    state.tracks.size,
                    state.tracks.size,
                )
            },
            shown = state.filtered.size,
            onQuery = { dispatch(DmtAction.Query(it)) },
            onSearch = { dispatch(DmtAction.Search) },
            sort = if (isYouTube) null else state.settings.librarySort.label,
            onSort = if (isYouTube) null else {
                {
                    dispatch(
                        DmtAction.Config(
                            state.settings.copy(
                                librarySort = state.settings.librarySort.next(
                                    state.settings.sourceMode,
                                ),
                            ),
                        ),
                    )
                }
            },
        )

        if (state.tracks.isEmpty() && state.query.isBlank() && isYouTube) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "type a song name above to search",
                style = MaterialTheme.typography.labelSmall,
                color = TuiDim,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        if (state.tracks.isEmpty() && state.query.isBlank() && !isYouTube) {
            Caption(stringResource(R.string.no_audio, state.settings.sourceMode.label))
        }

        if (state.scanning) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = TuiAccent,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isYouTube) "searching..." else "scanning...",
                style = MaterialTheme.typography.labelSmall,
                color = TuiDim,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        if (!state.scanning && state.query.isNotBlank() && state.filtered.isEmpty()) {
            Caption(stringResource(R.string.no_match))
        }

        if (!isYouTube && state.tracks.isNotEmpty() && state.query.isBlank()) {
            SectionChips(
                selected = state.librarySection,
                onSelect = { dispatch(DmtAction.SetLibrarySection(it)) },
            )
        }

        if (state.query.isBlank() && state.searchHistory.isNotEmpty()) {
            HistoryChips(
                history = state.searchHistory,
                onPick = { q ->
                    dispatch(DmtAction.Query(q))
                    if (isYouTube) dispatch(DmtAction.Search)
                },
            )
        }

        var longPress by remember { mutableStateOf<Track?>(null) }
        longPress?.let { track ->
            TuiSheet(onDismiss = { longPress = null }) {
                SheetHeader(title = track.title.lowercase(), meta = track.artist.lowercase())
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    TuiKey(label = "[ play next ]") {
                        dispatch(DmtAction.PlayNextTrack(track))
                        longPress = null
                    }
                    TuiKey(label = "[ queue ]") {
                        dispatch(DmtAction.Enqueue(listOf(track), track.title))
                        longPress = null
                    }
                    if (track.source == TrackSource.YOUTUBE) {
                        TuiKey(label = "[ download ]") {
                            dispatch(DmtAction.DownloadToDevice(track))
                            longPress = null
                        }
                    }
                }
            }
        }

        LazyColumn {
            itemsIndexed(state.filtered, key = { _, track -> track.id }) { index, track ->
                ListRow(
                    index = index,
                    line1 = track.title,
                    line2 = "${track.artist} · ${track.durationMs.asTime()}".lowercase(),
                    current = track.id.toString() == state.nowPlayingId,
                    onClick = { dispatch(DmtAction.PlayAt(state.filtered, index)) },
                    onLongClick = { longPress = track },
                    trailing = { TrackBadges(track) },
                )
            }
        }
    }
}

@Composable
private fun SectionChips(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        LibrarySection.entries.forEach { section ->
            val label = when (section) {
                LibrarySection.ALL -> "all"
                LibrarySection.RECENT -> "recent"
                LibrarySection.PLAYED -> "played"
            }
            Text(
                text = "[$label]",
                style = MaterialTheme.typography.labelMedium,
                color = if (section == selected) TuiAccent else TuiDim,
                modifier = Modifier
                    .tuiClickable { onSelect(section) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun HistoryChips(
    history: List<String>,
    onPick: (String) -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(top = 2.dp)) {
        Text(
            text = "recent:",
            style = MaterialTheme.typography.labelSmall,
            color = TuiFaint,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            history.forEach { q ->
                Text(
                    text = " [$q]",
                    style = MaterialTheme.typography.labelMedium,
                    color = TuiDim,
                    modifier = Modifier
                        .tuiClickable { onPick(q) }
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}

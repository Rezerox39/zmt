package dev.jyotiraditya.dmt.presentation.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.core.common.Caption
import dev.jyotiraditya.dmt.core.common.ListRow
import dev.jyotiraditya.dmt.core.common.SearchRow
import dev.jyotiraditya.dmt.domain.model.SourceMode
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.ui.theme.LocalTuiColors
import dev.jyotiraditya.dmt.util.asTime

@Composable
fun LibraryPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    val p = LocalTuiColors.current
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
                color = p.dim,
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
                color = p.accent,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isYouTube) "searching..." else "scanning...",
                style = MaterialTheme.typography.labelSmall,
                color = p.dim,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        if (!state.scanning && state.query.isNotBlank() && state.filtered.isEmpty()) {
            Caption(stringResource(R.string.no_match))
        }

        LazyColumn {
            itemsIndexed(state.filtered, key = { _, track -> track.id }) { index, track ->
                ListRow(
                    index = index,
                    line1 = track.title,
                    line2 = "${track.artist} · ${track.durationMs.asTime()}".lowercase(),
                    current = track.id.toString() == state.nowPlayingId,
                    onClick = { dispatch(DmtAction.PlayAt(state.filtered, index)) },
                    onLongClick = { dispatch(DmtAction.Enqueue(listOf(track), track.title)) },
                )
            }
        }
    }
}

package dev.abhi.zmt.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.abhi.zmt.R
import dev.abhi.zmt.core.common.Caption
import dev.abhi.zmt.core.common.SubdirHeader
import dev.abhi.zmt.core.common.trackQualityLabel
import dev.abhi.zmt.core.common.tuiClickable
import dev.abhi.zmt.domain.model.DuplicateGroup
import dev.abhi.zmt.domain.model.Track
import dev.abhi.zmt.domain.model.findDuplicates
import dev.abhi.zmt.presentation.player.DmtAction
import dev.abhi.zmt.presentation.player.DmtState
import dev.abhi.zmt.presentation.player.DmtView
import dev.abhi.zmt.ui.theme.TuiAccent
import dev.abhi.zmt.ui.theme.TuiBright
import dev.abhi.zmt.ui.theme.TuiDim
import dev.abhi.zmt.ui.theme.TuiFaint
import dev.abhi.zmt.ui.theme.TuiFg
import dev.abhi.zmt.util.asMB

@Composable
fun StatsPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    val top = state.stats.counts.entries
        .sortedByDescending { it.value }
        .take(10)
        .mapNotNull { entry ->
            state.tracks.find { it.id == entry.key }?.let { track -> track to entry.value }
        }
    val maxCount = (top.firstOrNull()?.second ?: 1).coerceAtLeast(1)
    val duplicates = remember(state.tracks) { findDuplicates(state.tracks) }
    val duplicateWaste = duplicates.sumOf { it.wastedBytes }

    LazyColumn {
        item {
            SubdirHeader(
                title = stringResource(R.string.stats),
                meta = "",
                onBack = { dispatch(DmtAction.Show(DmtView.SETTINGS)) },
            )

            Caption(stringResource(R.string.stat_listening))
            StatRow(
                label = stringResource(R.string.stat_time),
                value = formatListenTime(state.stats.totalMs),
            )
            StatRow(
                label = stringResource(R.string.stat_plays),
                value = "${state.stats.counts.values.sum()}",
            )

            Caption(stringResource(R.string.stat_library))
            StatRow(
                label = stringResource(R.string.stat_tracks),
                value = "${state.tracks.size}",
            )
            StatRow(
                label = stringResource(R.string.stat_albums),
                value = "${state.albums.size}",
            )
            StatRow(
                label = stringResource(R.string.stat_folders),
                value = "${state.folders.size}",
            )

            Caption(stringResource(R.string.stat_top))
            if (top.isEmpty()) {
                Text(
                    text = stringResource(R.string.stat_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TuiFaint,
                )
            }

            Caption(stringResource(R.string.stat_duplicates))
            StatRow(
                label = stringResource(R.string.stat_duplicate_groups),
                value = "${duplicates.size}",
            )
            StatRow(
                label = stringResource(R.string.stat_duplicate_waste),
                value = duplicateWaste.asMB(),
            )
            if (duplicates.isEmpty()) {
                Text(
                    text = stringResource(R.string.stat_duplicates_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TuiFaint,
                )
            }
        }
        itemsIndexed(duplicates.take(10), key = { _, group -> group.tracks.first().id }) { _, group ->
            DuplicateRow(group)
        }
        itemsIndexed(top, key = { _, (track, _) -> track.id }) { index, (track, count) ->
            TopTrackRow(
                index = index,
                track = track,
                count = count,
                fraction = count.toFloat() / maxCount,
                onClick = { dispatch(DmtAction.PlayAt(listOf(track), 0)) },
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TuiFg,
        )
        Text(
            text = ".".repeat(200),
            style = MaterialTheme.typography.labelSmall,
            color = TuiFaint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TuiBright,
        )
    }
}

@Composable
private fun TopTrackRow(
    index: Int,
    track: Track,
    count: Int,
    fraction: Float,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tuiClickable(onClick)
            .padding(vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "%02d  %s".format(index + 1, track.title),
                style = MaterialTheme.typography.bodyMedium,
                color = TuiFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = TuiDim,
            )
        }
        val cols = 28
        val filled = (fraction.coerceIn(0f, 1f) * cols).toInt().coerceAtLeast(1)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = TuiAccent)) { append("█".repeat(filled)) }
                withStyle(SpanStyle(color = TuiFaint)) { append("░".repeat(cols - filled)) }
            },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

private fun formatListenTime(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
}

@Composable
private fun DuplicateRow(group: DuplicateGroup) {
    val best = group.tracks.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = group.title.lowercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = TuiFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
            )
            Text(
                text = "${group.tracks.size} files",
                style = MaterialTheme.typography.labelMedium,
                color = TuiDim,
            )
        }
        Row(modifier = Modifier.padding(top = 2.dp)) {
            val bestLabel = best?.let { trackQualityLabel(it) } ?: "?"
            Text(
                text = "keep $bestLabel",
                style = MaterialTheme.typography.labelSmall,
                color = TuiAccent,
            )
            if (group.wastedBytes > 0) {
                Text(
                    text = "  ·  wasted ${group.wastedBytes.asMB()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TuiDim,
                )
            }
        }
        group.tracks.forEach { track ->
            Text(
                text = "  ·  ${trackQualityLabel(track) ?: track.mime.lowercase()}  " +
                    track.path.substringAfterLast('/').take(36),
                style = MaterialTheme.typography.labelSmall,
                color = TuiFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

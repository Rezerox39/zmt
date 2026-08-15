package dev.abhi.zmt.presentation.library

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.abhi.zmt.R
import dev.abhi.zmt.core.common.Caption
import dev.abhi.zmt.core.common.ListRow
import dev.abhi.zmt.core.common.ScrollMemory
import dev.abhi.zmt.core.common.SearchRow
import dev.abhi.zmt.core.common.TuiKey
import dev.abhi.zmt.domain.model.Track
import dev.abhi.zmt.domain.model.toAlbums
import dev.abhi.zmt.presentation.player.DmtAction
import dev.abhi.zmt.presentation.player.DmtState
import dev.abhi.zmt.presentation.player.SheetHeader
import dev.abhi.zmt.presentation.player.TuiSheet
import dev.abhi.zmt.ui.theme.TuiFaint
import dev.abhi.zmt.util.asTime

internal class GroupSpec<T>(
    val items: List<T>,
    val filtered: List<T>,
    val openKey: String?,
    @param:PluralsRes val searchHint: Int,
    @param:StringRes val emptyText: Int,
    val key: (T) -> String,
    val title: (T) -> String,
    val listMeta: (T) -> String,
    val detailMeta: (T) -> String = { "" },
    val countLead: (T) -> String = { "" },
    val trackMeta: (Track) -> String,
    val children: (T) -> GroupChildren,
    val open: (String?) -> DmtAction,
)

internal fun <T> GroupSpec<T>.tracksOf(item: T): List<Track> = children(item).flatten()

@Composable
fun AlbumsPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    GroupPane(
        spec = GroupSpec(
            items = state.albums,
            filtered = state.filteredAlbums,
            openKey = state.openAlbum,
            searchHint = R.plurals.search_albums_hint,
            emptyText = R.string.no_albums,
            key = { it.name },
            title = { it.name },
            listMeta = { "${it.artist} · ${it.tracks.size} trk" },
            detailMeta = { it.artist },
            trackMeta = { trackLine2(it, album = false) },
            children = { GroupChildren.Tracks(it.tracks) },
            open = { DmtAction.OpenAlbum(it) },
        ),
        state = state,
        dispatch = dispatch,
    )
}

@Composable
fun ArtistsPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    GroupPane(
        spec = GroupSpec(
            items = state.artists,
            filtered = state.filteredArtists,
            openKey = state.openArtist,
            searchHint = R.plurals.search_artists_hint,
            emptyText = R.string.no_artists,
            key = { it.name },
            title = { it.name },
            listMeta = { "${it.albums} alb · ${it.tracks.size} trk" },
            detailMeta = { "" },
            countLead = { "${it.albums} alb" },
            trackMeta = { trackLine2(it, artist = false) },
            children = { GroupChildren.Albums(it.tracks.toAlbums()) },
            open = { DmtAction.OpenArtist(it) },
        ),
        state = state,
        dispatch = dispatch,
    )
}

@Composable
fun GenresPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    GroupPane(
        spec = GroupSpec(
            items = state.genres,
            filtered = state.filteredGenres,
            openKey = state.openGenre,
            searchHint = R.plurals.search_artists_hint,
            emptyText = R.string.no_genres,
            key = { it.name },
            title = { it.name },
            listMeta = { "${it.tracks.size} trk" },
            detailMeta = { "" },
            trackMeta = { trackLine2(it) },
            children = { GroupChildren.Tracks(it.tracks) },
            open = { DmtAction.OpenGenre(it) },
        ),
        state = state,
        dispatch = dispatch,
    )
}

@Composable
fun FoldersPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    GroupPane(
        spec = GroupSpec(
            items = state.folders,
            filtered = state.filteredFolders,
            openKey = state.openFolder,
            searchHint = R.plurals.search_folders_hint,
            emptyText = R.string.no_files,
            key = { it.path },
            title = { it.name },
            listMeta = { "${it.tracks.size} trk" },
            detailMeta = { it.path },
            trackMeta = { trackLine2(it, album = false) },
            children = { GroupChildren.Tracks(it.tracks) },
            open = { DmtAction.OpenFolder(it) },
        ),
        state = state,
        dispatch = dispatch,
    )
}

@Composable
private fun <T> GroupPane(
    spec: GroupSpec<T>,
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
) {
    val openItem: T? = spec.items.find { spec.key(it) == spec.openKey }

    ScrollMemory(spec.openKey ?: "list") {
        if (openItem == null) {
            GroupList(spec, state, dispatch)
        } else {
            GroupDetail(spec, openItem, state, dispatch)
        }
    }
}

@Composable
private fun <T> GroupList(
    spec: GroupSpec<T>,
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
) {
    if (spec.items.isEmpty()) {
        Caption(stringResource(spec.emptyText))
        return
    }

    var sheetItem by remember { mutableStateOf<T?>(null) }
    sheetItem?.let { item ->
        TuiSheet(onDismiss = { sheetItem = null }) {
            SheetHeader(title = spec.title(item).lowercase())
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                TuiKey(label = "[ ${stringResource(R.string.action_play)} ]") {
                    dispatch(DmtAction.PlayAt(spec.tracksOf(item), 0))
                    sheetItem = null
                }
                TuiKey(label = "[ ${stringResource(R.string.action_queue)} ]") {
                    dispatch(DmtAction.Enqueue(spec.tracksOf(item), spec.title(item)))
                    sheetItem = null
                }
            }
        }
    }

    Column {
        SearchRow(
            query = state.query,
            hint = pluralStringResource(
                spec.searchHint,
                spec.items.size,
                spec.items.size,
            ),
            shown = spec.filtered.size,
            onQuery = { dispatch(DmtAction.Query(it)) },
        )
        if (spec.filtered.isEmpty()) {
            Caption(stringResource(R.string.no_match))
        }
        LazyColumn {
            itemsIndexed(spec.filtered, key = { _, item -> spec.key(item) }) { index, item ->
                ListRow(
                    index = index,
                    line1 = spec.title(item),
                    line2 = spec.listMeta(item).lowercase(),
                    current = false,
                    onClick = { dispatch(spec.open(spec.key(item))) },
                    onLongClick = { sheetItem = item },
                    trailing = {
                        Text(
                            text = stringResource(R.string.open_album),
                            style = MaterialTheme.typography.labelMedium,
                            color = TuiFaint,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

package dev.abhi.zmt.presentation.main

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.abhi.zmt.R
import dev.abhi.zmt.core.common.Caption
import dev.abhi.zmt.core.common.FitScaled
import dev.abhi.zmt.core.common.ScrollMemory
import dev.abhi.zmt.core.common.TuiNotice
import dev.abhi.zmt.core.common.TuiTab
import dev.abhi.zmt.core.common.fitScaleFor
import dev.abhi.zmt.core.common.isLandscapeWindow
import dev.abhi.zmt.domain.model.Track
import dev.abhi.zmt.presentation.home.HomePane
import dev.abhi.zmt.presentation.library.AlbumsPane
import dev.abhi.zmt.presentation.library.ArtistsPane
import dev.abhi.zmt.presentation.library.FoldersPane
import dev.abhi.zmt.presentation.library.GenresPane
import dev.abhi.zmt.presentation.library.LibraryPane
import dev.abhi.zmt.presentation.library.PlaylistsPane
import dev.abhi.zmt.presentation.library.ImportedPane
import dev.abhi.zmt.presentation.player.ChainContent
import dev.abhi.zmt.presentation.player.DmtAction
import dev.abhi.zmt.presentation.player.DmtState
import dev.abhi.zmt.presentation.player.DmtView
import dev.abhi.zmt.presentation.player.InfoContent
import dev.abhi.zmt.presentation.player.MiniPlayer
import dev.abhi.zmt.presentation.player.PlayerSheet
import dev.abhi.zmt.presentation.player.QueueList
import dev.abhi.zmt.presentation.player.SheetHeader
import dev.abhi.zmt.presentation.player.TuiSheet
import dev.abhi.zmt.presentation.settings.BlocklistPane
import dev.abhi.zmt.presentation.settings.PermissionsPane
import dev.abhi.zmt.presentation.settings.SettingsPane
import dev.abhi.zmt.presentation.settings.FingerprintAuthManager
import dev.abhi.zmt.domain.model.SourceMode
import kotlinx.coroutines.launch
import dev.abhi.zmt.presentation.settings.SourceLoginPane
import dev.abhi.zmt.presentation.settings.SourcesPane
import dev.abhi.zmt.presentation.settings.StatsPane
import dev.abhi.zmt.ui.theme.TuiAccent
import dev.abhi.zmt.ui.theme.TuiBg
import dev.abhi.zmt.ui.theme.TuiBright
import dev.abhi.zmt.ui.theme.TuiLine
import dev.abhi.zmt.ui.theme.TuiFg
import dev.abhi.zmt.ui.theme.TuiFaint
import dev.abhi.zmt.ui.theme.TuiDim
import dev.abhi.zmt.ui.theme.TuiRaised
import dev.abhi.zmt.core.common.tuiClickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DmtScreen(
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    art: suspend (Track) -> Bitmap,
) {
    var showQueueSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var miniAnchor by remember { mutableStateOf<Rect?>(null) }
    val imeVisible = WindowInsets.isImeVisible
    val landscape = isLandscapeWindow()

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.expanded, state.view, showQueueSheet, showInfoSheet) {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    LaunchedEffect(state.queue.isEmpty()) {
        if (state.queue.isEmpty()) showQueueSheet = false
    }

    val backHandled = !state.expanded &&
            ((state.view == DmtView.ALBUMS && state.openAlbum != null) ||
                    state.view != DmtView.HOME)
    BackHandler(enabled = backHandled) {
        when {
            state.view == DmtView.IMPORTED -> dispatch(DmtAction.Show(DmtView.SOURCES))

            state.view == DmtView.STATS -> dispatch(DmtAction.Show(DmtView.SETTINGS))

            state.view == DmtView.BLOCKLIST -> dispatch(DmtAction.Show(DmtView.SETTINGS))

            state.view == DmtView.PERMISSIONS -> dispatch(DmtAction.Show(DmtView.SETTINGS))

            state.view == DmtView.SOURCE_LOGIN -> dispatch(DmtAction.Show(DmtView.SOURCES))

            state.view == DmtView.ALBUMS && state.openAlbum != null ->
                dispatch(DmtAction.OpenAlbum(null))

            state.view == DmtView.ARTISTS && state.openArtist != null ->
                dispatch(DmtAction.OpenArtist(null))

            state.view == DmtView.GENRES && state.openGenre != null ->
                dispatch(DmtAction.OpenGenre(null))

            state.view == DmtView.FOLDERS && state.openFolder != null ->
                dispatch(DmtAction.OpenFolder(null))

            state.view == DmtView.PLAYLISTS && state.openPlaylist != null ->
                dispatch(DmtAction.OpenPlaylist(null))

            else -> dispatch(DmtAction.Show(if (state.view == DmtView.LIBRARY) DmtView.HOME else DmtView.LIBRARY))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TuiBg),
    ) {
        if (landscape) {
            FitScaled(fitScaleFor(designHeightDp = 400f, minScale = 0.85f)) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp),
                ) {
                    SideRail(state, dispatch)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        PaneHost(
                            state = state,
                            dispatch = dispatch,
                            art = art,
                            modifier = Modifier.weight(1f),
                                    )

                        TuiNotice(error = state.error, notice = state.notice)

                        if (state.nowPlayingId != null && !imeVisible) {
                            MiniPlayerAnchor(state) { miniAnchor = it }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp),
            ) {
                Titlebar(state, dispatch)
                TabsRow(state, dispatch)
                PaneHost(
                    state = state,
                    dispatch = dispatch,
                    art = art,
                    modifier = Modifier.weight(1f),
                    )

                TuiNotice(error = state.error, notice = state.notice)

                if (state.nowPlayingId != null && !imeVisible) {
                    MiniPlayerAnchor(state) { miniAnchor = it }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }


        PlayerSheet(
            state = state,
            dispatch = dispatch,
            anchor = miniAnchor,
            hidden = imeVisible && !state.expanded,
            onInfo = { showInfoSheet = true },
            onQueue = { showQueueSheet = true },
        )

        if (showQueueSheet) {
            TuiSheet(onDismiss = { showQueueSheet = false }) {
                val position = (state.queuePosition + 1).coerceAtLeast(1)
                SheetHeader(
                    title = stringResource(R.string.queue_title),
                    meta = "$position/${state.queue.size}",
                    actions = {
                        Text(
                            text = "[ save ]",
                            style = MaterialTheme.typography.labelMedium,
                            color = TuiDim,
                            modifier = Modifier
                                .tuiClickable { dispatch(DmtAction.SaveQueueAsPlaylist) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    },
                )
                QueueList(
                    state = state,
                    dispatch = dispatch,
                    modifier = Modifier.heightIn(max = 420.dp),
                )
            }
        }

        if (showInfoSheet) {
            TuiSheet(onDismiss = { showInfoSheet = false }) {
                var showChain by remember { mutableStateOf(false) }
                SheetHeader(title = stringResource(R.string.track_info)) {
                    TuiTab(
                        label = stringResource(R.string.tab_info),
                        active = !showChain,
                    ) {
                        showChain = false
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TuiTab(
                        label = stringResource(R.string.tab_chain),
                        active = showChain,
                    ) {
                        showChain = true
                    }
                }
                if (showChain) ChainContent(state) else InfoContent(state)
            }
        }
    }

    // Import playlist URL dialog
    if (state.showImportDialog) {
        var importUrl by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { dispatch(DmtAction.DismissImportDialog) },
            containerColor = dev.abhi.zmt.ui.theme.TuiBg,
            title = {
                Text(text = "import playlist", color = TuiFg)
            },
            text = {
                Column {
                    Text(
                        text = "paste spotify or youtube music playlist url",
                        style = MaterialTheme.typography.labelSmall,
                        color = TuiDim,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.TextField(
                        value = importUrl,
                        onValueChange = { importUrl = it },
                        placeholder = {
                            Text(
                                text = "https://open.spotify.com/playlist/...",
                                style = MaterialTheme.typography.labelSmall,
                                color = TuiFaint,
                            )
                        },
                        singleLine = true,
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedTextColor = TuiFg,
                            unfocusedTextColor = TuiFg,
                            focusedContainerColor = TuiRaised,
                            unfocusedContainerColor = TuiRaised,
                            focusedIndicatorColor = TuiAccent,
                            unfocusedIndicatorColor = TuiLine,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (importUrl.isNotBlank()) {
                            dispatch(DmtAction.ImportPlaylistFromUrl(importUrl))
                        }
                    }
                ) {
                    Text("import", color = TuiAccent)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { dispatch(DmtAction.DismissImportDialog) }
                ) {
                    Text("cancel", color = TuiDim)
                }
            },
        )
    }
}

@Composable
private fun MiniPlayerAnchor(
    state: DmtState,
    onAnchor: (Rect) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onAnchor(it.boundsInRoot()) }
            .alpha(0f)
            .clearAndSetSemantics {},
    ) {
        MiniPlayer(state = state, dispatch = {})
    }
}

@Composable
private fun PaneHost(
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    art: suspend (Track) -> Bitmap,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ScrollMemory(state.view.name) {
            when {
                state.view == DmtView.HOME -> HomePane(
                    state = state,
                    dispatch = dispatch,
                    art = art,
                    onOpenAlbum = { name ->
                        dispatch(DmtAction.Show(DmtView.ALBUMS))
                        dispatch(DmtAction.OpenAlbum(name))
                    },
                    onOpenAlbums = { dispatch(DmtAction.Show(DmtView.ALBUMS)) },
                    onOpenTracks = { dispatch(DmtAction.Show(DmtView.LIBRARY)) },
                    onOpenArtist = { name ->
                        dispatch(DmtAction.Show(DmtView.ARTISTS))
                        dispatch(DmtAction.OpenArtist(name))
                    },
                    onOpenArtists = { dispatch(DmtAction.Show(DmtView.ARTISTS)) },
                )
                state.view == DmtView.STATS -> StatsPane(state, dispatch)
                state.view == DmtView.BLOCKLIST -> BlocklistPane(state, dispatch)
                state.view == DmtView.PERMISSIONS -> PermissionsPane(state, dispatch)
                state.view == DmtView.SETTINGS -> SettingsPane(state, dispatch)
                state.view == DmtView.SOURCES -> SourcesPane(state, dispatch)
                state.view == DmtView.SOURCE_LOGIN -> SourceLoginPane(mode = state.loginSource, state = state, dispatch = dispatch)
                state.scanning && state.tracks.isEmpty() ->
                    Caption(stringResource(R.string.scanning))

                state.view == DmtView.ALBUMS -> AlbumsPane(state, dispatch)
                state.view == DmtView.ARTISTS -> ArtistsPane(state, dispatch)
                state.view == DmtView.GENRES -> GenresPane(state, dispatch)
                state.view == DmtView.FOLDERS -> FoldersPane(state, dispatch)
                state.view == DmtView.PLAYLISTS -> PlaylistsPane(state, dispatch)
                state.view == DmtView.IMPORTED -> ImportedPane(state, dispatch)
                else -> LibraryPane(state, dispatch)
            }
        }
    }
}

@Composable
private fun SideRail(state: DmtState, dispatch: (DmtAction) -> Unit) {
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .fillMaxHeight(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(TuiAccent),
            )
            Text(
                text = " " + stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = TuiBright,
            )
        }
        HorizontalDivider(color = TuiLine, modifier = Modifier.padding(top = 8.dp))

        Spacer(modifier = Modifier.height(12.dp))
        libraryTabs(state).forEachIndexed { index, (label, view) ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            TuiTab(
                label = label,
                active = state.view == view,
                modifier = Modifier.fillMaxWidth(),
            ) {
                dispatch(DmtAction.Show(view))
            }
        }
        Spacer(modifier = Modifier.weight(1f))

        SourcesTab(state, dispatch, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        ConfigTab(state, dispatch, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun SourcesTab(
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.view == DmtView.SOURCES || state.view == DmtView.SOURCE_LOGIN
    TuiTab(
        label = stringResource(R.string.tab_sources),
        active = active,
        modifier = modifier,
    ) {
        dispatch(DmtAction.Show(if (active) DmtView.LIBRARY else DmtView.SOURCES))
    }
}

@Composable
private fun ConfigTab(
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.view == DmtView.SETTINGS ||
            state.view == DmtView.STATS ||
            state.view == DmtView.PERMISSIONS
    TuiTab(
        label = stringResource(R.string.cfg),
        active = active,
        modifier = modifier,
    ) {
        dispatch(DmtAction.Show(if (active) DmtView.LIBRARY else DmtView.SETTINGS))
    }
}

@Composable
private fun Titlebar(state: DmtState, dispatch: (DmtAction) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(TuiAccent),
        )
        Text(
            text = " " + stringResource(R.string.app_name) + " ",
            style = MaterialTheme.typography.titleMedium,
            color = TuiBright,
        )
        HorizontalDivider(color = TuiLine, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(10.dp))
        SourcesTab(state, dispatch)
        Spacer(modifier = Modifier.width(8.dp))
        ConfigTab(state, dispatch)
    }
}

@Composable
private fun libraryTabs(state: DmtState): List<Pair<String, DmtView>> =
    buildList {
        add(stringResource(R.string.tab_home) to DmtView.HOME)
        add(stringResource(R.string.tab_library) to DmtView.LIBRARY)
        add(stringResource(R.string.tab_albums) to DmtView.ALBUMS)
        add(stringResource(R.string.tab_artists) to DmtView.ARTISTS)
        if (state.genres.isNotEmpty()) {
            add(stringResource(R.string.tab_genres) to DmtView.GENRES)
        }
        if (state.folders.isNotEmpty()) {
            add(stringResource(R.string.tab_folders) to DmtView.FOLDERS)
            add(stringResource(R.string.tab_playlists) to DmtView.PLAYLISTS)
        }
    }

@Composable
private fun TabsRow(state: DmtState, dispatch: (DmtAction) -> Unit) {
    val tabs = libraryTabs(state)
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.view) { requester.bringIntoView() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        tabs.forEach { (label, view) ->
            val active = state.view == view
            TuiTab(
                label = label,
                active = active,
                modifier = if (active) {
                    Modifier.bringIntoViewRequester(requester)
                } else {
                    Modifier
                },
            ) {
                dispatch(DmtAction.Show(view))
            }
        }
    }
}

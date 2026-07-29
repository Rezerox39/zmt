package dev.abhi.zmt.presentation.player

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import kotlin.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.abhi.zmt.R
import dev.abhi.zmt.core.base.BaseViewModel
import dev.abhi.zmt.core.common.generateAsciiPlaceholder
import dev.abhi.zmt.core.common.toAsciiBitmap
import dev.abhi.zmt.data.repository.PlaylistRepository
import dev.abhi.zmt.data.repository.PreferencesRepository
import dev.abhi.zmt.data.repository.TrackMediaRepository
import dev.abhi.zmt.domain.model.Album
import dev.abhi.zmt.domain.model.Artist
import dev.abhi.zmt.domain.model.Folder
import dev.abhi.zmt.domain.model.LibrarySort
import dev.abhi.zmt.domain.model.Playlist
import dev.abhi.zmt.domain.model.SourceMode
import dev.abhi.zmt.domain.model.Track
import dev.abhi.zmt.domain.usecase.EmbedLyricsUseCase
import dev.abhi.zmt.domain.usecase.GetLyricsUseCase
import dev.abhi.zmt.domain.usecase.GetTrackTechUseCase
import dev.abhi.zmt.domain.usecase.JellyfinLoginUseCase
import dev.abhi.zmt.domain.usecase.TelegramLoginUseCase
import dev.abhi.zmt.data.remote.telegram.TelegramAuthStep
import kotlinx.coroutines.flow.collectLatest
import dev.abhi.zmt.domain.usecase.ScanLibraryUseCase
import dev.abhi.zmt.playback.PlaybackService
import dev.abhi.zmt.util.QUEUE_CAP
import dev.abhi.zmt.util.audioPermission
import dev.abhi.zmt.util.cycleRepeat
import dev.abhi.zmt.util.mediaController
import dev.abhi.zmt.util.queueLabels
import dev.abhi.zmt.util.resolveQueue
import dev.abhi.zmt.util.toMediaItem
import dev.abhi.zmt.util.togglePlayPause
import dev.abhi.zmt.util.windowQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn

import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val SPEED_STEPS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val SLEEP_STEPS = listOf(0, 15, 30, 60)

private data class FilteredLibrary(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val folders: List<Folder>,
    val playlists: List<Playlist> = emptyList(),
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val scanLibrary: ScanLibraryUseCase,
    private val jellyfinLogin: JellyfinLoginUseCase,
    private val telegramLogin: TelegramLoginUseCase,
    private val getLyrics: GetLyricsUseCase,
    private val embedLyrics: EmbedLyricsUseCase,
    private val getTrackTech: GetTrackTechUseCase,
    private val trackMediaRepository: TrackMediaRepository,
    private val playlistRepository: PlaylistRepository,
    private val mediaSourceProvider: dev.abhi.zmt.domain.usecase.MediaSourceProvider,
    private val youtubeRepository: dev.abhi.zmt.data.repository.YoutubeMediaRepositoryImpl,
    private val downloadManager: dev.abhi.zmt.data.remote.download.TrackDownloadManager,
) : BaseViewModel<DmtAction, DmtState, PlayerEffect>(
    DmtState(
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            audioPermission,
        ) == PackageManager.PERMISSION_GRANTED,
    ),
) {

    private var controller: MediaController? = null
    private var pendingEmbed: Pair<Track, String>? = null
    private var noticeJob: Job? = null
    private var sleepEndAt: Long? = null
    private var sessionRestored = false

    init {
        viewModelScope.launch {
            val settings = preferencesRepository.settings.first()
            reduce { it.copy(settings = settings) }
        }
        viewModelScope.launch {
            val initErr = telegramLogin.initialize()
            if (initErr != null) {
                reduce { it.copy(error = initErr) }
            }
            telegramLogin.observeAuthState().collectLatest { step ->
                reduce { it.copy(telegramAuthStep = step) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.stats.collect { stats ->
                reduce { if (it.stats == stats) it else it.copy(stats = stats) }
            }
        }
        viewModelScope.launch {
            trackMediaRepository.routeSpecs()
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .collect { route ->
                    reduce { if (it.route == route) it else it.copy(route = route) }
                }
        }
        if (currentState.hasPermission) scan()
        connect()
    }

    private suspend fun performYouTubeSearch(query: String) {
        if (query.isBlank()) {
            reduce { it.copy(tracks = emptyList(), filtered = emptyList(), scanning = false) }
            return
        }
        reduce { it.copy(scanning = true) }
        try {
            val results = youtubeRepository.search(query)
            if (currentState.query == query) {
                reduce {
                    it.copy(
                        scanning = false,
                        tracks = results,
                        filtered = results,
                    )
                }
            }
        } catch (e: Exception) {
            if (currentState.query == query) {
                reduce {
                    it.copy(
                        scanning = false,
                        error = "YouTube search failed: ${e.message}",
                    )
                }
            }
        }
    }

    private suspend fun performLocalFilter(query: String) {
        val tracks = currentState.tracks
        val albums = currentState.albums
        val artists = currentState.artists
        val folders = currentState.folders
        val playlists = currentState.playlists
        val sort = currentState.settings.librarySort
        val (filteredTracks, filteredAlbums, filteredArtists, filteredFolders, filteredPlaylists) =
            withContext(Dispatchers.Default) {
                FilteredLibrary(
                    tracks = filter(tracks, query, sort),
                    albums = filterAlbums(albums, query),
                    artists = filterArtists(artists, query),
                    folders = filterFolders(folders, query),
                    playlists = filterPlaylists(playlists, query),
                )
            }
        if (currentState.query == query) {
            reduce {
                it.copy(
                    filtered = filteredTracks,
                    filteredAlbums = filteredAlbums,
                    filteredArtists = filteredArtists,
                    filteredFolders = filteredFolders,
                    filteredPlaylists = filteredPlaylists,
                )
            }
        }
    }

    private fun lookupCurrentTrack(): dev.abhi.zmt.domain.model.Track? {
        val id = currentState.nowPlayingId
        if (id == null) return null
        // Try to find the track in available lists by matching nowPlayingId
        for (list in listOf(currentState.filtered, currentState.tracks, currentState.queue.mapNotNull { mid ->
            currentState.tracks.find { it.id.toString() == mid }
        })) {
            val found = list.find { it.id.toString() == id }
            if (found != null) return found
        }
        return null
    }

    private fun <T> List<T>.matching(query: String, fields: (T) -> List<String>): List<T> =
        if (query.isBlank()) {
            this
        } else {
            filter { item -> fields(item).any { it.contains(query, true) } }
        }

    private fun filter(tracks: List<Track>, query: String, sort: LibrarySort): List<Track> =
        tracks
            .matching(query) { listOf(it.title, it.artist, it.album) }
            .sortedWith(sort.comparator)

    private fun filterAlbums(albums: List<Album>, query: String): List<Album> =
        albums.matching(query) { listOf(it.name, it.artist) }

    private fun filterArtists(artists: List<Artist>, query: String): List<Artist> =
        artists.matching(query) { listOf(it.name) }

    private fun filterFolders(folders: List<Folder>, query: String): List<Folder> =
        folders.matching(query) { listOf(it.name) }

    private fun filterPlaylists(playlists: List<Playlist>, query: String): List<Playlist> =
        playlists.matching(query) { listOf(it.name) }

    private fun mutatePlaylists(block: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
            val playlists = playlistRepository.load(currentState.tracks)
            reduce {
                it.copy(
                    playlists = playlists,
                    filteredPlaylists = filterPlaylists(playlists, it.query),
                )
            }
        }
    }

    override fun onIntent(intent: DmtAction) {
        val c = controller
        when (intent) {
            is DmtAction.Permission -> {
                reduce { it.copy(hasPermission = intent.granted) }
                if (intent.granted) scan()
            }

            DmtAction.Rescan -> scan()
            is DmtAction.Query -> {
                reduce { it.copy(query = intent.value) }
                // Local filter is instant (in-memory), keep it on every keystroke
                if (currentState.settings.sourceMode != SourceMode.YOUTUBE) {
                    viewModelScope.launch {
                        performLocalFilter(intent.value)
                    }
                }
            }
            is DmtAction.Search -> {
                val query = currentState.query
                if (query.isNotBlank() && currentState.settings.sourceMode == SourceMode.YOUTUBE) {
                    viewModelScope.launch {
                        performYouTubeSearch(query)
                    }
                }
            }
            DmtAction.ShowDownloadSheet -> {
                reduce { it.copy(showDownloadSheet = true) }
            }
            DmtAction.DismissDownloadSheet -> {
                reduce { it.copy(showDownloadSheet = false) }
            }
            DmtAction.DownloadToDevice -> {
                reduce { it.copy(showDownloadSheet = false, downloadProgress = 0, downloadError = null) }
                val track = lookupCurrentTrack()
                if (track == null) { reduce { it.copy(downloadError = "No track playing") }; return@onIntent }
                val videoId = track.remoteId ?: track.uri.lastPathSegment ?: run {
                    reduce { it.copy(downloadError = "No video ID") }; return@onIntent
                }
                // Download runs on TrackDownloadManager's own IO scope — NOT viewModelScope
                downloadManager.downloadToDevice(
                    context = context,
                    videoId = videoId,
                    title = track.title,
                    artist = track.artist,
                    onProgress = { progress ->
                        if (progress.isFinished) {
                            reduce {
                                it.copy(
                                    downloadProgress = 101,
                                    downloadError = null,
                                )
                            }
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(2000L)
                                reduce { it.copy(downloadProgress = -1, downloadError = null) }
                            }
                        } else {
                            reduce {
                                it.copy(
                                    downloadProgress = progress.percent.coerceIn(0, 99),
                                    downloadError = progress.error,
                                )
                            }
                        }
                    },
                )
            }
            is DmtAction.Show -> {
                reduce { it.copy(view = intent.view, error = null) }
            }

            is DmtAction.OpenAlbum -> reduce { it.copy(openAlbum = intent.name) }
            is DmtAction.OpenArtist -> reduce { it.copy(openArtist = intent.name) }
            is DmtAction.OpenFolder -> reduce { it.copy(openFolder = intent.path) }
            is DmtAction.OpenPlaylist -> reduce { it.copy(openPlaylist = intent.name) }

            is DmtAction.CreatePlaylist -> mutatePlaylists {
                playlistRepository.create(intent.name)
            }

            is DmtAction.DeletePlaylist -> {
                reduce { it.copy(openPlaylist = null) }
                mutatePlaylists { playlistRepository.delete(intent.name) }
            }

            is DmtAction.AddToPlaylist -> mutatePlaylists {
                playlistRepository.addTrack(intent.name, intent.track)
            }

            is DmtAction.RemoveFromPlaylist -> mutatePlaylists {
                playlistRepository.removeTrack(intent.name, intent.path)
            }

            is DmtAction.PlayAt -> c?.run {
                reduce { it.copy(error = null) }
                val (queue, startIndex) = windowQueue(intent.list, intent.index)
                setMediaItems(
                    queue.map { it.toMediaItem() },
                    startIndex,
                    0L,
                )
                prepare()
                play()
            }

            is DmtAction.Enqueue -> c?.run {
                addMediaItems(intent.list.take(QUEUE_CAP).map { it.toMediaItem() })
                prepare()
                notify(context.getString(R.string.queued, intent.label))
            }

            is DmtAction.Jump -> c?.run {
                seekTo(intent.index, 0L)
                prepare()
                play()
            }

            DmtAction.TogglePlay -> c?.togglePlayPause()
            DmtAction.Next -> c?.seekToNext()
            DmtAction.Prev -> c?.seekToPrevious()
            DmtAction.ToggleShuffle -> c?.run { shuffleModeEnabled = !shuffleModeEnabled }
            DmtAction.CycleRepeat -> c?.cycleRepeat()

            is DmtAction.Seek -> c?.run {
                val duration = currentState.durationMs
                if (duration > 0) {
                    val target = (intent.fraction * duration).toLong()
                    seekTo(target)
                    reduce { it.copy(positionMs = target) }
                }
            }

            is DmtAction.Expand -> reduce { it.copy(expanded = intent.value) }

            is DmtAction.RemoveAt -> c?.run {
                if (intent.index in 0 until mediaItemCount) removeMediaItem(intent.index)
            }

            DmtAction.FetchLyrics -> fetchOnlineLyrics()
            is DmtAction.EmbedLyrics -> embedPendingLyrics(intent.granted)
            DmtAction.CycleSleep -> cycleSleep()
            DmtAction.CycleSpeed -> cycleSpeed()
            DmtAction.OpenEqualizer -> openEqualizer()
            DmtAction.NoEqualizer -> notify(context.getString(R.string.no_eq))

            is DmtAction.Config -> {
                val old = currentState.settings
                reduce { it.copy(settings = intent.settings) }
                if (old.librarySort != intent.settings.librarySort) {
                    reduce {
                        it.copy(
                            filtered = filter(it.tracks, it.query, intent.settings.librarySort),
                        )
                    }
                }
                if (old.sourceMode != intent.settings.sourceMode) {
                    c?.run {
                        stop()
                        clearMediaItems()
                    }
                }
                viewModelScope.launch {
                    preferencesRepository.save(intent.settings)
                    if (old.sourceMode != intent.settings.sourceMode ||
                        old.blockedFolders != intent.settings.blockedFolders
                    ) {
                        scan()
                    }
                }
                if (old.cols != intent.settings.cols) loadCover(c?.currentMediaItem)
            }

            is DmtAction.ShowLogin ->
                reduce {
                    it.copy(
                        view = DmtView.SOURCE_LOGIN,
                        loginSource = intent.mode,
                        error = null,
                    )
                }

            is DmtAction.SourceLogin -> when (intent.mode) {
                SourceMode.JELLYFIN -> loginToJellyfin(intent)
                SourceMode.LOCAL -> Unit
                SourceMode.YOUTUBE -> Unit
                SourceMode.TELEGRAM -> {
                    viewModelScope.launch {
                        val initErr = telegramLogin.initialize()
                        if (initErr != null) {
                            reduce { it.copy(error = initErr) }
                            return@launch
                        }
                        val result = telegramLogin.sendPhoneNumber(intent.username)
                        if (result.isFailure) {
                            reduce { it.copy(error = result.exceptionOrNull()?.message ?: "Failed") }
                        }
                    }
                }
            }
            is DmtAction.TelegramSendPhone -> {
                viewModelScope.launch {
                    reduce { it.copy(error = null, scanning = true) }
                    val initErr = telegramLogin.initialize()
                    if (initErr != null) {
                        android.util.Log.e("TDLibDebug", "Init failed: $initErr")
                        reduce { it.copy(error = "Init failed: $initErr", scanning = false) }
                        return@launch
                    }
                    android.util.Log.d("TDLibDebug", "Init OK, sending phone: ${intent.phoneNumber.take(4)}****")
                    try {
                        val result = telegramLogin.sendPhoneNumber(intent.phoneNumber)
                        if (result.isFailure) {
                            val errMsg = result.exceptionOrNull()?.message ?: "Failed to send phone number"
                            android.util.Log.e("TDLibDebug", "Send phone failed: $errMsg")
                            reduce { it.copy(error = "Error: $errMsg", scanning = false) }
                        } else {
                            android.util.Log.d("TDLibDebug", "Send phone OK, waiting for auth state...")
                            reduce { it.copy(scanning = false) }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("TDLibDebug", "Send phone exception: ${e.message}", e)
                        reduce { it.copy(error = "Connection error: ${e.message}", scanning = false) }
                    }
                }
            }
            is DmtAction.TelegramSubmitCode -> {
                viewModelScope.launch {
                    val result = telegramLogin.submitCode(intent.code)
                    if (result.isFailure) {
                        reduce { it.copy(error = result.exceptionOrNull()?.message ?: "Invalid code") }
                    }
                }
            }
            is DmtAction.TelegramSubmitPassword -> {
                viewModelScope.launch {
                    val result = telegramLogin.submitPassword(intent.password)
                    if (result.isFailure) {
                        reduce { it.copy(error = result.exceptionOrNull()?.message ?: "Invalid password") }
                    }
                }
            }
            is DmtAction.TelegramResolveChannel -> {
                viewModelScope.launch {
                    reduce { it.copy(telegramSyncing = true) }
                    val result = telegramLogin.resolveChannel(intent.channelInput)
                    if (result.isSuccess) {
                        val settings = preferencesRepository.settings.first()
                        reduce {
                            it.copy(
                                settings = settings,
                                telegramSyncing = false,
                                notice = "Channel connected",
                            )
                        }
                        scan()
                        onIntent(DmtAction.Show(DmtView.LIBRARY))
                    } else {
                        reduce {
                            it.copy(
                                telegramSyncing = false,
                                error = result.exceptionOrNull()?.message ?: "Failed to connect channel",
                            )
                        }
                    }
                }
            }
            is DmtAction.TelegramLogout -> {
                viewModelScope.launch {
                    telegramLogin.logout()
                    val settings = preferencesRepository.settings.first()
                    reduce { it.copy(settings = settings, telegramAuthStep = "") }
                    onIntent(DmtAction.Show(DmtView.SOURCES))
                }
            }
        }
    }

    private fun loginToJellyfin(intent: DmtAction.SourceLogin) =
        viewModelScope.launch {
            reduce { it.copy(scanning = true, error = null) }
            jellyfinLogin(intent.url, intent.username, intent.password)
                .onSuccess {
                    val settings = preferencesRepository.settings.first()
                    reduce { it.copy(settings = settings, view = DmtView.LIBRARY) }
                    scan()
                }
                .onFailure {
                    reduce {
                        it.copy(
                            scanning = false,
                            error = context.getString(R.string.source_login_failed),
                        )
                    }
                }
        }

    private fun connect() =
        viewModelScope.launch {
            val c = runCatching { context.mediaController() }.getOrNull()
                ?: return@launch
            controller = c
            c.addListener(listener)
            syncFrom(c)
            restoreSleep(c)
            restoreSpeed(c)
            loadCover(c.currentMediaItem)
            loadTech(c.currentMediaItem)
            loadLyrics(c.currentMediaItem)
            restoreSession()
            while (isActive) {
                val position = c.currentPosition.coerceAtLeast(0L)
                val duration = c.duration.takeIf { d -> d != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
                val index = c.currentMediaItemIndex
                val sleepLeft = sleepEndAt?.let { end ->
                    (end - System.currentTimeMillis()).coerceAtLeast(0L)
                } ?: 0L
                val sleepExpired = sleepEndAt != null && sleepLeft == 0L
                if (sleepExpired) sleepEndAt = null
                reduce {
                    if (it.positionMs == position &&
                        it.durationMs == duration &&
                        it.queueIndex == index &&
                        it.sleepLeftMs == sleepLeft &&
                        !sleepExpired
                    ) {
                        it
                    } else {
                        it.copy(
                            positionMs = position,
                            durationMs = duration,
                            queueIndex = index,
                            sleepLeftMs = sleepLeft,
                            sleepMinutes = if (sleepExpired) 0 else it.sleepMinutes,
                        )
                    }
                }
                delay((if (c.isPlaying) 500 else 1500).milliseconds)
            }
        }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            reduce {
                it.copy(
                    nowPlayingId = mediaItem?.mediaId,
                    lyrics = null,
                    error = null,
                )
            }
            loadCover(mediaItem)
            loadTech(mediaItem)
            loadLyrics(mediaItem)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            reduce {
                it.copy(
                    title = mediaMetadata.title?.toString() ?: "unknown",
                    artist = mediaMetadata.artist?.toString() ?: "unknown artist",
                    album = mediaMetadata.albumTitle?.toString().orEmpty(),
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            reduce { it.copy(isPlaying = isPlaying) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            reduce { it.copy(shuffle = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            reduce { it.copy(repeat = repeatMode) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            reduce { it.copy(speed = playbackParameters.speed) }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            controller?.let { c -> reduce { it.copy(queue = c.queueLabels()) } }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Log detailed error info for debugging
            val sb = StringBuilder()
            sb.appendLine("PlaybackError: ${error.errorCodeName} (${error.errorCode})")
            // Walk cause chain looking for HTTP response code
            var c: Throwable? = error.cause
            while (c != null) {
                val cn = c::class.simpleName ?: ""
                sb.appendLine("  Cause: $cn: ${c.message?.take(200)}")
                if (cn == "InvalidResponseCodeException") {
                    try {
                        val codeField = c::class.java.getDeclaredField("responseCode")
                        codeField.isAccessible = true
                        sb.appendLine("  >>> HTTP Status: ${codeField.get(c)} <<<")
                        val headersField = c::class.java.getDeclaredField("headerFields")
                        headersField.isAccessible = true
                        sb.appendLine("  >>> Response headers: ${headersField.get(c)} <<<")
                    } catch (_: Exception) {
                        sb.appendLine("  >>> HTTP error (reflection failed) <<<")
                    }
                    break
                }
                c = c.cause
            }
            android.util.Log.e("PlaybackDebug", sb.toString())
            reduce {
                val name = error.errorCodeName.lowercase()
                it.copy(error = context.getString(R.string.playback_error, name))
            }
        }
    }

    private fun syncFrom(c: MediaController) {
        reduce {
            it.copy(
                nowPlayingId = c.currentMediaItem?.mediaId,
                title = c.mediaMetadata.title?.toString() ?: "unknown",
                artist = c.mediaMetadata.artist?.toString() ?: "unknown artist",
                isPlaying = c.isPlaying,
                shuffle = c.shuffleModeEnabled,
                repeat = c.repeatMode,
                album = c.mediaMetadata.albumTitle?.toString().orEmpty(),
                speed = c.playbackParameters.speed,
                queue = c.queueLabels(),
            )
        }
    }

    private fun scan() =
        viewModelScope.launch {
            reduce { it.copy(scanning = true) }
            val query = currentState.query
            val library = runCatching { scanLibrary() }.getOrElse {
                reduce { state ->
                    state.copy(
                        scanning = false,
                        tracks = emptyList(),
                        albums = emptyList(),
                        artists = emptyList(),
                        folders = emptyList(),
                        filtered = emptyList(),
                        filteredAlbums = emptyList(),
                        filteredArtists = emptyList(),
                        filteredFolders = emptyList(),
                        error = context.getString(
                            R.string.scan_failed,
                            state.settings.sourceMode.label,
                        ),
                    )
                }
                return@launch
            }
            val (filteredTracks, filteredAlbums, filteredArtists, filteredFolders) = withContext(
                Dispatchers.Default,
            ) {
                FilteredLibrary(
                    tracks = filter(library.tracks, query, currentState.settings.librarySort),
                    albums = filterAlbums(library.albums, query),
                    artists = filterArtists(library.artists, query),
                    folders = filterFolders(library.folders, query),
                )
            }
            reduce {
                it.copy(
                    scanning = false,
                    tracks = library.tracks,
                    albums = library.albums,
                    artists = library.artists,
                    folders = library.folders,
                    filtered = filteredTracks,
                    filteredAlbums = filteredAlbums,
                    filteredArtists = filteredArtists,
                    filteredFolders = filteredFolders,
                    error = null,
                )
            }
            mutatePlaylists()
            restoreSession()
        }

    private fun restoreSession() {
        if (sessionRestored) return
        val c = controller ?: return
        val tracks = currentState.tracks
        if (tracks.isEmpty()) return
        if (c.mediaItemCount > 0) {
            sessionRestored = true
            return
        }
        sessionRestored = true
        viewModelScope.launch {
            val session = preferencesRepository.lastSession() ?: return@launch
            val (existing, index, position) = session.resolveQueue(tracks) ?: return@launch
            val (queue, startIndex) = windowQueue(existing, index)
            c.setMediaItems(
                queue.map { it.toMediaItem() },
                startIndex,
                position,
            )
            c.prepare()
        }
    }

    private fun fetchOnlineLyrics() {
        val id = currentState.nowPlayingId ?: return
        if (currentState.lyricsFetching || currentState.lyrics != null) return
        val track = currentState.tracks.find { it.id.toString() == id } ?: return

        reduce { it.copy(lyricsFetching = true) }
        viewModelScope.launch {
            val text = getLyrics.onlineText(track)
            val lyrics = text?.let { getLyrics.parse(it) }

            reduce {
                if (it.nowPlayingId != id) {
                    it.copy(lyricsFetching = false)
                } else {
                    it.copy(lyricsFetching = false, lyrics = lyrics)
                }
            }
            if (lyrics == null) {
                notify(context.getString(R.string.no_lyrics_found))
                return@launch
            }

            val intentSender = embedLyrics.writeRequest(track)
            if (intentSender == null) {
                notify(context.getString(R.string.lyrics_embed_unsupported))
                return@launch
            }
            pendingEmbed = track to text
            sendEffect(PlayerEffect.RequestWrite(intentSender))
        }
    }

    private fun embedPendingLyrics(granted: Boolean) {
        val (track, text) = pendingEmbed ?: return
        pendingEmbed = null
        if (!granted) return

        viewModelScope.launch {
            val done = embedLyrics(track, text)
            notify(
                context.getString(
                    if (done) R.string.lyrics_embedded else R.string.lyrics_embed_failed,
                ),
            )
        }
    }

    private fun loadLyrics(mediaItem: MediaItem?) {
        val forId = mediaItem?.mediaId
        reduce { it.copy(lyricsFetching = true) }
        viewModelScope.launch {
            val track = currentState.tracks.find { it.id.toString() == forId }
            val lyrics = track?.let { getLyrics(it) }
            reduce {
                if (it.nowPlayingId != forId) {
                    it
                } else {
                    it.copy(lyrics = lyrics, lyricsFetching = false)
                }
            }
        }
    }

    private fun loadCover(mediaItem: MediaItem?) {
        val uri: Uri? = mediaItem?.mediaMetadata?.artworkUri
        val fileUri: Uri? = mediaItem?.localConfiguration?.uri
        val forId = mediaItem?.mediaId
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) {
                uri?.let { trackMediaRepository.loadArt(it, fileUri) }
            }
            val cover = withContext(Dispatchers.IO) {
                raw?.let { art ->
                    runCatching {
                        art.toAsciiBitmap(context, currentState.settings.cols)
                    }.getOrNull()
                } ?: mediaItem?.let {
                    generateAsciiPlaceholder(
                        context = context,
                        seed = forId?.toLongOrNull() ?: forId.hashCode().toLong(),
                        cols = currentState.settings.cols,
                    )
                }
            }
            reduce {
                if (it.nowPlayingId != forId) it else it.copy(cover = cover, artRaw = raw)
            }
        }
    }

    private fun loadTech(mediaItem: MediaItem?) {
        val uri = mediaItem?.localConfiguration?.uri
        val id = mediaItem?.mediaId
        viewModelScope.launch {
            val track = currentState.tracks.find { t -> t.id.toString() == id }
            val tech = uri?.let { getTrackTech(it, track) }.orEmpty()
            reduce {
                if (it.nowPlayingId != id) it else it.copy(tech = tech)
            }
        }
    }

    private fun openEqualizer() {
        val c = controller ?: return
        viewModelScope.launch {
            val sessionId = runCatching {
                c.sendCustomCommand(PlaybackService.CMD_AUDIO_SESSION, Bundle.EMPTY)
                    .await()
                    .extras
                    .getInt(PlaybackService.KEY_AUDIO_SESSION)
            }.getOrDefault(0)
            sendEffect(PlayerEffect.OpenEqualizer(sessionId))
        }
    }

    private fun cycleSpeed() {
        val c = controller ?: return
        val currentIndex = SPEED_STEPS.indexOfFirst { abs(it - currentState.speed) < 0.01f }
        val next = SPEED_STEPS[(currentIndex + 1).mod(SPEED_STEPS.size)]
        c.setPlaybackSpeed(next)
        viewModelScope.launch {
            preferencesRepository.saveSpeed(next)
        }
    }

    private fun cycleSleep() {
        val c = controller ?: return
        val currentIndex = SLEEP_STEPS.indexOf(currentState.sleepMinutes)
        val next = SLEEP_STEPS[(currentIndex + 1).mod(SLEEP_STEPS.size)]
        val endAt = if (next == 0) 0L else System.currentTimeMillis() + next * 60_000L
        c.sendCustomCommand(
            PlaybackService.CMD_SLEEP_SET,
            Bundle().apply { putLong(PlaybackService.KEY_END_AT, endAt) },
        )
        sleepEndAt = endAt.takeIf { it > 0L }
        reduce {
            it.copy(
                sleepMinutes = next,
                sleepLeftMs = if (next == 0) 0L else next * 60_000L,
            )
        }
    }

    private suspend fun restoreSpeed(c: MediaController) {
        val saved = preferencesRepository.savedSpeed()
        if (abs(c.playbackParameters.speed - saved) > 0.01f) {
            c.setPlaybackSpeed(saved)
        }
    }

    private suspend fun restoreSleep(c: MediaController) {
        runCatching {
            val result = c.sendCustomCommand(PlaybackService.CMD_SLEEP_GET, Bundle.EMPTY).await()
            val endAt = result.extras.getLong(PlaybackService.KEY_END_AT)
            if (endAt > System.currentTimeMillis()) {
                sleepEndAt = endAt
                val left = endAt - System.currentTimeMillis()
                val step = when {
                    left <= 15 * 60_000L -> 15
                    left <= 30 * 60_000L -> 30
                    else -> 60
                }
                reduce { it.copy(sleepMinutes = step, sleepLeftMs = left) }
            }
        }
    }

    private fun notify(message: String) {
        noticeJob?.cancel()
        reduce { it.copy(notice = message) }
        noticeJob = viewModelScope.launch {
            delay(2.seconds)
            reduce { it.copy(notice = null) }
        }
    }

    override fun onCleared() {
        controller?.release()
        controller = null
    }
}

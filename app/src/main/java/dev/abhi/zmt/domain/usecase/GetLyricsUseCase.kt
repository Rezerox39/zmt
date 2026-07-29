package dev.abhi.zmt.domain.usecase

import dev.abhi.zmt.data.remote.jellyfin.JellyfinApi
import dev.abhi.zmt.data.remote.lrclib.LrclibApi
import dev.abhi.zmt.lyrics.LyricsParser
import dev.abhi.zmt.lyrics.Lyrics
import dev.abhi.zmt.domain.model.Track
import dev.abhi.zmt.domain.model.TrackSource
import dev.abhi.zmt.data.repository.LyricsRepository
import dev.abhi.zmt.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetLyricsUseCase @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val jellyfinApi: JellyfinApi,
    private val lrclibApi: LrclibApi,
    private val settingsRepository: PreferencesRepository,
) {

    suspend operator fun invoke(track: Track): Lyrics? =
        withContext(Dispatchers.IO) {
            if (track.source == TrackSource.JELLYFIN) {
                jellyfinLyrics(track)
            } else {
                lyricsRepository.lyricsFor(track.path, track.mime)
            }
        }

    suspend fun onlineText(track: Track): String? =
        withContext(Dispatchers.IO) {
            runCatching { lrclibApi.fetchLyrics(track) }.getOrNull()
        }

    suspend fun parse(text: String): Lyrics? =
        withContext(Dispatchers.Default) {
            LyricsParser.parse(text)
        }

    private suspend fun jellyfinLyrics(track: Track): Lyrics? {
        val remoteId = track.remoteId ?: return null

        val settings = settingsRepository.settings.first()
        val url = settings.jellyfinUrl ?: return null
        val token = settings.jellyfinToken ?: return null

        return runCatching {
            jellyfinApi.fetchLyrics(url, remoteId, token)
        }.getOrNull()
    }
}

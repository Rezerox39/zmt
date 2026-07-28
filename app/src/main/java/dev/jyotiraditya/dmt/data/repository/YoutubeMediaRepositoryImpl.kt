package dev.jyotiraditya.dmt.data.repository

import android.net.Uri
import android.util.Log
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.Innertube
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.bodies.SearchBody
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.requests.searchPage
import dev.jyotiraditya.dmt.domain.model.Track
import dev.jyotiraditya.dmt.domain.model.TrackSource
import dev.jyotiraditya.dmt.domain.repository.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoutubeMediaRepo"

@Singleton
class YoutubeMediaRepositoryImpl @Inject constructor() : MediaRepository {

    suspend fun search(query: String): List<Track> {
        return try {
            val body = SearchBody(
                query = query,
                params = Innertube.SearchFilter.Song.value,
            )
            val result = Innertube.searchPage(
                body = body,
                fromMusicShelfRendererContent = Innertube.SongItem.Companion::from,
            )
            val songs = result?.getOrNull()?.items

            Log.d(TAG, "Found ${songs?.size ?: 0} results for '$query'")

            songs?.mapNotNull { song ->
                val videoId = song.info?.endpoint?.videoId ?: return@mapNotNull null
                val thumbUrl = song.thumbnail?.thumbnails?.lastOrNull()?.url
                Track(
                    id = videoId.hashCode().toLong(),
                    uri = Uri.parse("youtube://video/$videoId"),
                    title = song.info?.name ?: "Unknown",
                    artist = song.authors?.firstOrNull()?.name ?: "Unknown Artist",
                    album = song.album?.name ?: "YouTube",
                    path = "youtube://video/$videoId",
                    durationMs = song.durationText?.let { parseDuration(it) } ?: 0L,
                    mime = "audio/mpeg",
                    bitrate = 0,
                    size = 0L,
                    trackNumber = 0,
                    dateAdded = System.currentTimeMillis(),
                    dateModified = System.currentTimeMillis(),
                    coverUri = thumbUrl?.let { Uri.parse(it) },
                    source = TrackSource.YOUTUBE,
                    remoteId = videoId,
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    override suspend fun scan(): List<Track> = emptyList()

    private fun parseDuration(text: String): Long {
        val parts = text.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3_600_000 + parts[1] * 60_000 + parts[2] * 1_000
            2 -> parts[0] * 60_000 + parts[1] * 1_000
            1 -> parts[0] * 1_000
            else -> 0L
        }
    }
}

package dev.abhi.zmt.data.repository

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.abhi.zmt.domain.model.Track
import dev.abhi.zmt.domain.model.TrackSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full metadata for liked tracks, kept alongside the `liked` playlist so the
 * YouTube library can show liked songs without re-searching them.
 */
@Singleton
class LikedTracksRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file: File
        get() = File(context.filesDir, "liked_tracks.json")

    @Synchronized
    fun all(): List<Track> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    Track(
                        id = obj.getLong("id"),
                        uri = obj.getString("uri").toUri(),
                        title = obj.optString("title", "unknown"),
                        artist = obj.optString("artist", "unknown artist"),
                        album = obj.optString("album", ""),
                        path = obj.optString("path", ""),
                        durationMs = obj.optLong("durationMs"),
                        mime = obj.optString("mime", ""),
                        bitrate = obj.optInt("bitrate"),
                        size = obj.optLong("size"),
                        trackNumber = obj.optInt("trackNumber"),
                        dateAdded = obj.optLong("dateAdded"),
                        dateModified = obj.optLong("dateModified"),
                        coverUri = obj.optString("coverUri")
                            .takeIf { it.isNotBlank() }
                            ?.toUri(),
                        source = runCatching {
                            TrackSource.valueOf(obj.optString("source"))
                        }.getOrDefault(TrackSource.LOCAL),
                        remoteId = obj.optString("remoteId").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun upsert(track: Track) {
        val tracks = all().filterNot { it.path == track.path }
        write(tracks + track)
    }

    @Synchronized
    fun remove(path: String) {
        write(all().filterNot { it.path == path })
    }

    private fun write(tracks: List<Track>) {
        runCatching {
            val arr = JSONArray()
            tracks.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("uri", t.uri.toString())
                        .put("title", t.title)
                        .put("artist", t.artist)
                        .put("album", t.album)
                        .put("path", t.path)
                        .put("durationMs", t.durationMs)
                        .put("mime", t.mime)
                        .put("bitrate", t.bitrate)
                        .put("size", t.size)
                        .put("trackNumber", t.trackNumber)
                        .put("dateAdded", t.dateAdded)
                        .put("dateModified", t.dateModified)
                        .put("coverUri", t.coverUri?.toString() ?: "")
                        .put("source", t.source.name)
                        .put("remoteId", t.remoteId ?: ""),
                )
            }
            file.writeText(arr.toString())
        }
    }
}

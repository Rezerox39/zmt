package dev.abhi.zmt.data.remote.playlist

import android.util.Log
import dev.abhi.zmt.data.remote.youtubedl.YouTubeDLBridge
import dev.abhi.zmt.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UrlPlaylistResolver"
private val json = Json { ignoreUnknownKeys = true }

/**
 * Resolves playlist URLs from Spotify and YouTube Music into track lists.
 *
 * Spotify: scrapes the public /embed/playlist page whose __NEXT_DATA__ blob
 * server-renders the full track list.
 * YouTube Music: uses yt-dlp which returns a JSON document with all entries.
 */
@Singleton
class UrlPlaylistResolver @Inject constructor(
    private val youTubeDLBridge: YouTubeDLBridge,
) {

    sealed class ResolveResult {
        data class Tracks(val name: String, val tracks: List<Pair<String, String>>) : ResolveResult()
        data class Error(val message: String) : ResolveResult()
    }

    suspend fun resolve(url: String, library: List<Track>): ResolveResult {
        val trimmed = url.trim()

        return when {
            trimmed.contains("open.spotify.com") || trimmed.contains("spotify.com") ||
                trimmed.contains("spotify.link") ->
                resolveSpotify(trimmed, library)
            trimmed.contains("youtube.com") || trimmed.contains("youtu.be") ||
                trimmed.contains("music.youtube.com") ->
                resolveYouTubeMusic(trimmed, library)
            else -> ResolveResult.Error("Unsupported URL — use Spotify or YouTube Music")
        }
    }

    // ── Spotify ──────────────────────────────────────────────────────

    private suspend fun resolveSpotify(url: String, library: List<Track>): ResolveResult =
        withContext(Dispatchers.IO) {
            try {
                val playlistId = extractSpotifyId(url)
                    ?: return@withContext ResolveResult.Error("Could not extract Spotify playlist ID")

                val tracks = scrapeSpotifyPage(playlistId)
                if (tracks.isEmpty()) {
                    return@withContext ResolveResult.Error("Could not fetch tracks from Spotify playlist")
                }

                val name = "spotify-${playlistId.take(8)}"
                ResolveResult.Tracks(name, tracks)
            } catch (e: Exception) {
                Log.e(TAG, "Spotify resolve failed: ${e.message}")
                ResolveResult.Error("Spotify import failed: ${e.message}")
            }
        }

    private fun extractSpotifyId(url: String): String? {
        val regex = Regex("""spotify\.com/playlist/([a-zA-Z0-9]+)""")
        regex.find(url)?.let { return it.groupValues[1] }
        // spotify.link short links need redirect resolution
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connect()
            val loc = conn.getHeaderField("Location") ?: ""
            conn.disconnect()
            regex.find(loc)?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }

    private fun scrapeSpotifyPage(playlistId: String): List<Pair<String, String>> {
        val pageUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        val conn = URL(pageUrl).openConnection() as HttpURLConnection
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
        )
        conn.connectTimeout = 12_000
        conn.readTimeout = 12_000
        conn.connect()

        val html = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        // Parse the server-rendered __NEXT_DATA__ JSON blob.
        return parseSpotifyNextData(html)
    }

    private fun parseSpotifyNextData(html: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        try {
            val nextDataPattern =
                Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            val match = nextDataPattern.find(html) ?: return result
            val root = json.parseToJsonElement(match.groupValues[1]).jsonObject
            val entity = root["props"]?.jsonObject
                ?.get("pageProps")?.jsonObject
                ?.get("state")?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("entity")?.jsonObject
                ?: return result
            val trackList = entity["trackList"]?.jsonArray ?: return result
            for (item in trackList) {
                if (item !is kotlinx.serialization.json.JsonObject) continue
                val title = item["title"]?.jsonPrimitive?.contentOrNull ?: continue
                val subtitle = item["subtitle"]?.jsonPrimitive?.contentOrNull ?: ""
                result.add(title.trim() to subtitle.trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Spotify __NEXT_DATA__ parse failed: ${e.message}")
        }
        return result
    }

    // ── YouTube Music ────────────────────────────────────────────────

    private suspend fun resolveYouTubeMusic(url: String, library: List<Track>): ResolveResult =
        withContext(Dispatchers.IO) {
            try {
                if (!youTubeDLBridge.isReady()) {
                    return@withContext ResolveResult.Error("YouTube DL not ready")
                }

                val jsonStr = youTubeDLBridge.runPlaylist(url)
                    ?: return@withContext ResolveResult.Error("Could not fetch YouTube playlist")

                val tracks = parseYtDlpPlaylist(jsonStr)
                if (tracks.isEmpty()) {
                    return@withContext ResolveResult.Error("No tracks found in playlist")
                }

                val name = "ytm-${tracks.size}tracks"
                ResolveResult.Tracks(name, tracks)
            } catch (e: Exception) {
                Log.e(TAG, "YouTube Music resolve failed: ${e.message}")
                ResolveResult.Error("YouTube Music import failed: ${e.message}")
            }
        }

    /**
     * list_playlist returns a single JSON document like
     * {"entries": [{"title": .., "artist": ..}, ...]}. Parse it as JSON (it is
     * not newline-delimited despite older logic splitting on lines).
     */
    private fun parseYtDlpPlaylist(jsonStr: String): List<Pair<String, String>> {
        val tracks = mutableListOf<Pair<String, String>>()
        try {
            val root = json.parseToJsonElement(jsonStr).jsonObject
            val entries = root["entries"]?.jsonArray ?: return tracks
            for (entry in entries) {
                if (entry !is kotlinx.serialization.json.JsonObject) continue
                val title = entry["title"]?.jsonPrimitive?.contentOrNull
                    ?: entry["track"]?.jsonPrimitive?.contentOrNull ?: continue
                val artist = entry["artist"]?.jsonPrimitive?.contentOrNull
                    ?: entry["uploader"]?.jsonPrimitive?.contentOrNull
                    ?: entry["channel"]?.jsonPrimitive?.contentOrNull ?: ""
                tracks.add(title.trim() to artist.trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp playlist parse failed: ${e.message}")
            // Fallback: legacy line-based parsing (robust to plain text output)
            for (line in jsonStr.lines()) {
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("{")) continue
                val parts = t.split(" - ", limit = 2)
                tracks.add(parts[0].trim() to parts.getOrElse(1) { "" }.trim())
            }
        }
        return tracks
    }

    /** Match resolved tracks against local library. */
    fun matchTracks(
        resolved: List<Pair<String, String>>,
        library: List<Track>,
    ): List<Track> {
        return resolved.mapNotNull { (title, artist) ->
            val titleLower = title.lowercase().trim()
            val artistLower = artist.lowercase().trim()

            if (artistLower.isNotEmpty()) {
                library.find { it.title.lowercase() == titleLower && it.artist.lowercase() == artistLower }
                    ?: library.find { it.title.lowercase().contains(titleLower) && it.artist.lowercase().contains(artistLower) }
                    ?: library.find { it.title.lowercase().contains(titleLower) }
            } else {
                library.find { it.title.lowercase() == titleLower }
                    ?: library.find { it.title.lowercase().contains(titleLower) }
            }
        }
    }
}

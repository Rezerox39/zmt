package dev.abhi.zmt.data.remote.playlist

import android.util.Log
import dev.abhi.zmt.data.remote.youtubedl.YouTubeDLBridge
import dev.abhi.zmt.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UrlPlaylistResolver"

/**
 * Resolves playlist URLs from Spotify and YouTube Music into track lists.
 *
 * Spotify: scrapes the public playlist page for JSON-LD track data.
 * YouTube Music: uses yt-dlp to list playlist tracks.
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
            trimmed.contains("open.spotify.com") || trimmed.contains("spotify.com") ->
                resolveSpotify(trimmed, library)
            trimmed.contains("youtube.com") || trimmed.contains("youtu.be") || trimmed.contains("music.youtube.com") ->
                resolveYouTubeMusic(trimmed, library)
            else -> ResolveResult.Error("Unsupported URL — use Spotify or YouTube Music")
        }
    }

    private suspend fun resolveSpotify(url: String, library: List<Track>): ResolveResult =
        withContext(Dispatchers.IO) {
            try {
                // Extract playlist ID from URL
                val playlistId = extractSpotifyId(url)
                    ?: return@withContext ResolveResult.Error("Could not extract Spotify playlist ID")

                // Scrape the public Spotify playlist page for track data
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
        // https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=...
        val regex = Regex("""spotify\.com/playlist/([a-zA-Z0-9]+)""")
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun scrapeSpotifyPage(playlistId: String): List<Pair<String, String>> {
        val pageUrl = "https://open.spotify.com/playlist/$playlistId"
        val conn = URL(pageUrl).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.connect()

        val html = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val tracks = mutableListOf<Pair<String, String>>()

        // Extract from JSON-LD schema
        val jsonLdPattern = Regex("""<script type="application/ld\+json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        jsonLdPattern.findAll(html).forEach { match ->
            val json = match.groupValues[1]
            // Extract name and artist from tracklist
            val trackPattern = Regex(""""name"\s*:\s*"([^"]+)"""")
            val tracksFound = trackPattern.findAll(json).map { it.groupValues[1] }.toList()
            // Every other entry after the playlist name is a track
            tracksFound.drop(1).forEach { name ->
                tracks.add(name to "")
            }
        }

        // Fallback: extract from meta tags / structured data
        if (tracks.isEmpty()) {
            val ogPattern = Regex(""""musicSong"[^{]*\{[^}]*"name"\s*:\s*"([^"]+)"[^}]*"byArtist"[^{]*\{[^}]*"name"\s*:\s*"([^"]+)"""")
            ogPattern.findAll(html).forEach { match ->
                tracks.add(match.groupValues[1] to match.groupValues[2])
            }
        }

        // Fallback 2: extract from __NEXT_DATA__
        if (tracks.isEmpty()) {
            val nextDataPattern = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            nextDataPattern.find(html)?.let { match ->
                val data = match.groupValues[1]
                // Find track names in the JSON
                val namePattern = Regex(""""name"\s*:\s*"([^"]{1,200})"""")
                val artistPattern = Regex(""""name"\s*:\s*"([^"]{1,100})"""")
                val names = namePattern.findAll(data).map { it.groupValues[1] }.toList()
                // Spotify JSON alternates: track_name, artist_name, album_name, ...
                names.windowed(2, 3).forEach { (name, _) ->
                    tracks.add(name to "")
                }
            }
        }

        return tracks
    }

    private suspend fun resolveYouTubeMusic(url: String, library: List<Track>): ResolveResult =
        withContext(Dispatchers.IO) {
            try {
                if (!youTubeDLBridge.isReady()) {
                    return@withContext ResolveResult.Error("YouTube DL not ready")
                }

                // Use yt-dlp to list playlist tracks
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

    private fun parseYtDlpPlaylist(json: String): List<Pair<String, String>> {
        val tracks = mutableListOf<Pair<String, String>>()
        // yt-dlp playlist output is newline-delimited JSON or simple text
        val lines = json.lines().filter { it.isNotBlank() }
        for (line in lines) {
            // Try JSON format: {"title": "...", "artist": "..."}
            val titleMatch = Regex(""""title"\s*:\s*"([^"]+)"""").find(line)
            val artistMatch = Regex(""""artist"\s*:\s*"([^"]+)"""").find(line)
            if (titleMatch != null) {
                tracks.add(titleMatch.groupValues[1] to (artistMatch?.groupValues?.get(1) ?: ""))
            } else if (!line.startsWith("{")) {
                // Plain text format: "Title - Artist"
                val parts = line.split(" - ", limit = 2)
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

package dev.abhi.zmt.data.remote.playlist

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.abhi.zmt.data.repository.PlaylistRepository
import dev.abhi.zmt.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlaylistImporter"

@Singleton
class PlaylistImporter @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {

    sealed class ImportResult {
        data class Success(val name: String, val matched: Int, val total: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        library: List<Track>,
        name: String? = null,
    ): ImportResult {
        val content = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return ImportResult.Error("Could not read file")
        } catch (e: Exception) {
            return ImportResult.Error("Read failed: ${e.message}")
        }

        val fileName = name ?: context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }?.substringBeforeLast(".") ?: "imported"

        return when {
            content.trimStart().startsWith("#EXTM3U") -> importM3U(fileName, content, library)
            content.contains(",") -> importCSV(fileName, content, library)
            else -> ImportResult.Error("Unrecognized format")
        }
    }

    private suspend fun importM3U(name: String, content: String, library: List<Track>): ImportResult {
        val entries = content.lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.trim() }

        if (entries.isEmpty()) return ImportResult.Error("Empty M3U playlist")

        val tracks = mutableListOf<Track>()
        var total = 0

        for (entry in entries) {
            total++
            val baseName = entry.substringAfterLast("/").substringBeforeLast(".")
            val matched = findMatch(baseName, library)
            if (matched != null) tracks.add(matched)
        }

        savePlaylist(name, tracks)
        return ImportResult.Success(name, tracks.size, total)
    }

    private suspend fun importCSV(name: String, content: String, library: List<Track>): ImportResult {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ImportResult.Error("Empty CSV")

        val header = lines.first().lowercase()
        val isSpotify = header.contains("track name") || header.contains("trackname")
        val isYTMusic = header.contains("title") && header.contains("artist")

        val dataLines = lines.drop(1)
        val tracks = mutableListOf<Track>()
        var total = 0

        for (line in dataLines) {
            total++
            val fields = parseCSVLine(line)
            val (title, artist) = when {
                isSpotify -> (fields.getOrNull(0) ?: "") to (fields.getOrNull(1) ?: "")
                isYTMusic -> (fields.getOrNull(0) ?: "") to (fields.getOrNull(1) ?: "")
                else -> (fields.getOrNull(0) ?: "") to (fields.getOrNull(1) ?: "")
            }
            val matched = findMatch(title, artist, library)
            if (matched != null) tracks.add(matched)
        }

        savePlaylist(name, tracks)
        return ImportResult.Success(name, tracks.size, total)
    }

    private fun findMatch(name: String, artist: String, library: List<Track>): Track? {
        val titleLower = name.lowercase().trim()
        val artistLower = artist.lowercase().trim()

        if (artistLower.isEmpty()) {
            return library.find { it.title.lowercase() == titleLower }
                ?: library.find { it.title.lowercase().contains(titleLower) }
        }

        return library.find {
            it.title.lowercase() == titleLower && it.artist.lowercase() == artistLower
        } ?: library.find {
            it.title.lowercase().contains(titleLower) && it.artist.lowercase().contains(artistLower)
        } ?: library.find {
            it.title.lowercase().contains(titleLower)
        }
    }

    private fun findMatch(name: String, library: List<Track>): Track? {
        val lower = name.lowercase().trim()
        return library.find { it.title.lowercase() == lower }
            ?: library.find { it.title.lowercase().contains(lower) }
    }

    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    private suspend fun savePlaylist(name: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val safeName = name.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim()
        playlistRepository.create(safeName)
        tracks.forEach { track ->
            playlistRepository.addTrack(safeName, track)
        }
        Log.d(TAG, "Imported playlist '$safeName': ${tracks.size} tracks")
    }
}

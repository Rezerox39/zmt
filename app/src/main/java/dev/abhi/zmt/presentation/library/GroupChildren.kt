package dev.abhi.zmt.presentation.library

import dev.abhi.zmt.domain.model.Album
import dev.abhi.zmt.domain.model.Track

sealed interface GroupChildren {
    data class Tracks(val tracks: List<Track>) : GroupChildren
    data class Albums(val albums: List<Album>) : GroupChildren

    fun flatten(): List<Track> = when (this) {
        is Tracks -> tracks
        is Albums -> albums.flatMap { it.tracks }
    }
}

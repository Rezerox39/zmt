package dev.abhi.zmt.data.repository

import dev.abhi.zmt.lyrics.LyricsParser
import dev.abhi.zmt.lyrics.LyricsTags
import dev.abhi.zmt.lyrics.Lyrics
import dev.jyotiraditya.metadata.AudioTags
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor() {

    fun lyricsFor(path: String, mime: String): Lyrics? =
        LyricsTags.bestOf(AudioTags.read(path))?.let(LyricsParser::parse)
}

package dev.jyotiraditya.dmt.data.repository

import dev.jyotiraditya.lyrics.LyricsParser
import dev.jyotiraditya.lyrics.LyricsTags
import dev.jyotiraditya.lyrics.Lyrics
import dev.jyotiraditya.metadata.AudioTags
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor() {

    fun lyricsFor(path: String, mime: String): Lyrics? =
        LyricsTags.bestOf(AudioTags.read(path))?.let(LyricsParser::parse)
}

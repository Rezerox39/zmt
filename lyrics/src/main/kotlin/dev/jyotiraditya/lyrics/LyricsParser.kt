package dev.jyotiraditya.lyrics

/**
 * Format-detecting entry point: picks [TtmlLyricsParser] or [LrcLyricsParser]
 * by sniffing [raw], falling back to unsynced plain text.
 */
object LyricsParser {

    fun parse(raw: String): Lyrics? {
        val trimmed = raw.trim()

        return when {
            trimmed.isEmpty() -> null

            trimmed.startsWith("<") && trimmed.contains("<tt") ->
                TtmlLyricsParser.parse(trimmed)

            LrcLyricsParser.matches(trimmed) ->
                LrcLyricsParser.parse(trimmed)

            else -> parsePlain(trimmed)
        }
    }

    private fun parsePlain(trimmed: String): Lyrics {
        val lines = trimmed.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { text ->
                LyricLine(
                    startMs = -1L,
                    endMs = -1L,
                    text = text,
                )
            }

        return Lyrics(
            lines = lines,
            synced = false,
        )
    }
}

/** Fills in each line's [LyricLine.endMs] from the next line's start where the source left it unset. */
fun List<LyricLine>.fillLineEnds(): List<LyricLine> =
    mapIndexed { index, line ->
        if (line.endMs > line.startMs) {
            line
        } else {
            val nextStart = getOrNull(index + 1)?.startMs
            line.copy(endMs = nextStart ?: (line.startMs + 10_000L))
        }
    }

/**
 * Collapses back-to-back lines with identical text and overlapping time into one
 * [Voice.GROUP] line. If either side already has a real declared identity (a named
 * TTML group agent, not just two soloists who happened to overlap), that identity
 * is kept instead of being thrown away, so the same named group always renders in
 * the same color instead of falling back to a generic one whenever it happens to
 * collide with someone else's duplicate line.
 */
fun List<LyricLine>.mergeSimultaneousDuplicates(): List<LyricLine> {
    val out = mutableListOf<LyricLine>()

    forEach { line ->
        val last = out.lastOrNull()

        if (last != null &&
            !last.interlude &&
            last.text == line.text &&
            line.startMs < last.endMs
        ) {
            val singer = when {
                last.voice == Voice.GROUP -> last.singer
                line.voice == Voice.GROUP -> line.singer
                else -> -1
            }

            out[out.size - 1] = last.copy(
                endMs = maxOf(last.endMs, line.endMs),
                voice = Voice.GROUP,
                singer = singer,
            )
        } else {
            out += line
        }
    }

    return out
}

/**
 * Assigns each non-group, non-interlude line a [Voice.PRIMARY]/[Voice.SECONDARY] side
 * based on [LyricLine.singer], so the same singer always lands on the same side for
 * the whole song (singer 0 and 2 on one side, 1 and 3 on the other, and so on) instead
 * of flipping on every transition, which put a singer on the wrong side depending on
 * how many other singers came before them.
 */
fun List<LyricLine>.alternateVoices(): List<LyricLine> =
    map { line ->
        if (line.voice == Voice.GROUP || line.interlude || line.singer < 0) return@map line

        val side = if (line.singer % 2 == 0) Voice.PRIMARY else Voice.SECONDARY
        line.copy(voice = side)
    }

/** Inserts a `* * *` marker line into gaps of 8s or more between lines. */
fun List<LyricLine>.withInterludes(): List<LyricLine> {
    val out = mutableListOf<LyricLine>()
    var previousEnd = 0L

    forEach { line ->
        if (line.startMs - previousEnd >= 8_000L) {
            out += LyricLine(
                startMs = previousEnd + 400,
                endMs = line.startMs - 200,
                text = "* * *",
                voice = line.voice,
                singer = -1,
                interlude = true,
            )
        }

        out += line
        previousEnd = maxOf(previousEnd, line.endMs)
    }

    return out
}

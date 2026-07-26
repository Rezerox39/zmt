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

/** Collapses back-to-back lines with identical text and overlapping time into one [Voice.GROUP] line. */
fun List<LyricLine>.mergeSimultaneousDuplicates(): List<LyricLine> {
    val out = mutableListOf<LyricLine>()

    forEach { line ->
        val last = out.lastOrNull()

        if (last != null &&
            !last.interlude &&
            last.text == line.text &&
            line.startMs < last.endMs
        ) {
            out[out.size - 1] = last.copy(
                endMs = maxOf(last.endMs, line.endMs),
                voice = Voice.GROUP,
                singer = -1,
            )
        } else {
            out += line
        }
    }

    return out
}

/**
 * Assigns each non-group, non-interlude line a [Voice.PRIMARY]/[Voice.SECONDARY] side,
 * flipping only when [LyricLine.singer] changes from the previous such line. See
 * [Voice] for why this stays binary even when more than two singers are tagged.
 */
fun List<LyricLine>.alternateVoices(): List<LyricLine> {
    var side = Voice.SECONDARY
    var lastSinger = -1

    return map { line ->
        if (line.voice == Voice.GROUP || line.interlude) return@map line

        if (line.singer != lastSinger) {
            side = if (side == Voice.PRIMARY) Voice.SECONDARY else Voice.PRIMARY
            lastSinger = line.singer
        }

        line.copy(voice = side)
    }
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

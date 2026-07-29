package dev.abhi.zmt.lyrics

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.newGenericReader
import nl.adaptivity.xmlutil.xmlStreaming

/**
 * Parses the Apple Music lyric-TTML dialect, the kind amll/syncedlyrics-style
 * community uploads use: `<p>` lines under `<div>` sections, word spans timed
 * with `begin`/`end`, plus a few extra conventions:
 *
 * - `ttm:agent` in `<head><metadata>` declares each voice. A `<p ttm:agent="...">`
 *   (or its enclosing `<div ttm:agent="...">`) assigns that line a stable
 *   [LyricLine.singer] index in first-seen order. An agent of `type="group"`
 *   (ensemble/choir) maps to [Voice.GROUP] instead of a singer index.
 * - `ttm:role="x-bg"` on a span marks backing/adlib vocals.
 * - There are two ways a source attaches a reading or translation to a line,
 *   and we handle both. The block-level way is a `<translation>`/`<transliteration>`
 *   section with `<text for="#lineKey">` entries: one `<translation>` block per
 *   language, tagged with its own `xml:lang`, and only the first `<transliteration>`
 *   block per line is kept since these files never seem to carry more than one
 *   romanization. The more common way in community (amll-style) uploads is
 *   `ttm:role="x-translation"` and `ttm:role="x-roman"` spans sitting right inside
 *   the `<p>`, next to the timed word spans: one `x-translation` span per language,
 *   at most one `x-roman` span. Either way the result ends up in
 *   [LyricLine.translation] or [LyricLine.transliteration], never mixed into the
 *   line's sung [LyricLine.text].
 */
object TtmlLyricsParser {

    private const val ROLE_BACKGROUND = "x-bg"
    private const val ROLE_TRANSLATION = "x-translation"
    private const val ROLE_ROMANIZATION = "x-roman"
    private const val AGENT_TYPE_GROUP = "group"

    fun parse(raw: String): Lyrics? =
        runCatching {
            val parser = xmlStreaming.newGenericReader(raw)

            val agents = Agents()
            val lines = mutableListOf<LyricLine>()

            var inLine = false
            var lineBegin = -1L
            var lineEnd = -1L
            var lineVoice = Voice.PRIMARY
            var lineSinger = 0
            var lineSection = false
            var newSection = false
            var divAgent: String? = null
            var pendingSpace = false
            var lineKey: String? = null

            val translations = mutableMapOf<String, List<TimedText>>()
            val transliterations = mutableMapOf<String, TimedText>()
            var inTranslation = false
            var inTransliteration = false
            var currentTranslationLang: String? = null

            val text = StringBuilder()
            val words = mutableListOf<LyricWord>()
            val spanStack = ArrayDeque<SpanFrame>()
            val pendingTranslations = mutableListOf<TimedText>()
            var pendingTransliteration: TimedText? = null

            fun flushSpace() {
                if (pendingSpace && text.isNotEmpty() && text.last() != '\n') text.append(' ')
                pendingSpace = false
            }

            fun appendLyricText(chunk: String) {
                if (isFormattingOnly(chunk)) return

                chunk.forEach { c ->
                    if (c.isWhitespace()) {
                        pendingSpace = true
                    } else {
                        flushSpace()
                        text.append(c)
                    }
                }
            }

            var event = parser.next()
            while (event != EventType.END_DOCUMENT) {
                when (event) {
                    EventType.START_ELEMENT -> when (parser.localName) {
                        "agent" ->
                            agents.register(parser.attr("id"), parser.attr("type"))

                        "div" -> {
                            newSection = true
                            divAgent = parser.attr("agent")
                        }

                        "br" -> if (inLine) {
                            pendingSpace = false
                            text.append('\n')
                        }

                        "p" -> {
                            inLine = true
                            text.clear()
                            words.clear()
                            spanStack.clear()
                            pendingSpace = false
                            pendingTranslations.clear()
                            pendingTransliteration = null

                            lineBegin = parseTime(parser.attr("begin"))
                            lineEnd = parseTime(parser.attr("end"))
                            lineKey = parser.attr("key")

                            val agentId = parser.attr("agent") ?: divAgent
                            lineVoice = agents.voiceFor(agentId)
                            lineSinger = agents.singerFor(agentId)

                            lineSection = newSection
                            newSection = false
                        }

                        "translation" -> {
                            inTranslation = true
                            currentTranslationLang = parser.attr("lang")
                        }

                        "transliteration" -> inTransliteration = true

                        "text" -> {
                            val forKey = parser.attr("for")

                            if (forKey != null && inTranslation) {
                                val segments = readTranslationSegments(parser)
                                if (segments.isNotEmpty()) {
                                    val lang = currentTranslationLang
                                    translations[forKey] = (translations[forKey] ?: emptyList()) +
                                        segments.map { TimedText(text = it, lang = lang) }
                                }
                            } else if (forKey != null && inTransliteration) {
                                val (content, spanWords) = readTimedText(parser)

                                if (content.isNotBlank() && forKey !in transliterations) {
                                    transliterations[forKey] = TimedText(
                                        text = content,
                                        words = spanWords,
                                    )
                                }
                            }
                        }

                        "span" -> if (inLine) {
                            val role = parser.attr("role")

                            when (role) {
                                ROLE_TRANSLATION -> {
                                    val lang = parser.attr("lang")
                                    val (content, _) = readTimedText(parser)
                                    if (content.isNotBlank()) {
                                        pendingTranslations += TimedText(text = content, lang = lang)
                                    }
                                }

                                ROLE_ROMANIZATION -> {
                                    val (content, _) = readTimedText(parser)
                                    if (content.isNotBlank()) {
                                        pendingTransliteration = TimedText(text = content)
                                    }
                                }

                                else -> {
                                    spanStack.lastOrNull()?.hadChild = true
                                    flushSpace()

                                    val parentBackground = spanStack.lastOrNull()?.background == true

                                    spanStack.addLast(
                                        SpanFrame(
                                            beginMs = parseTime(parser.attr("begin")),
                                            endMs = parseTime(parser.attr("end")),
                                            textStart = text.length,
                                            background = parentBackground || role == ROLE_BACKGROUND,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    EventType.TEXT, EventType.IGNORABLE_WHITESPACE -> if (inLine) appendLyricText(parser.text)

                    EventType.END_ELEMENT -> when (parser.localName) {
                        "translation" -> {
                            inTranslation = false
                            currentTranslationLang = null
                        }

                        "transliteration" -> inTransliteration = false

                        "span" -> if (inLine && spanStack.isNotEmpty()) {
                            val frame = spanStack.removeLast()
                            val isWord = !frame.hadChild &&
                                    frame.beginMs >= 0 &&
                                    text.length > frame.textStart

                            if (isWord) {
                                words += LyricWord(
                                    startMs = frame.beginMs,
                                    endMs = frame.endMs,
                                    start = frame.textStart,
                                    end = text.length,
                                    background = frame.background,
                                )
                            }
                        }

                        "p" -> if (inLine) {
                            inLine = false

                            val lineText = text.toString()
                            if (lineText.isNotEmpty()) {
                                val bounded = words
                                    .map { word ->
                                        word.copy(
                                            start = word.start.coerceIn(0, lineText.length),
                                            end = word.end.coerceIn(0, lineText.length),
                                        )
                                    }
                                    .filter { it.end > it.start }

                                lines += LyricLine(
                                    startMs = lineBegin,
                                    endMs = lineEnd,
                                    text = lineText,
                                    words = bounded,
                                    voice = lineVoice,
                                    singer = lineSinger,
                                    sectionStart = lineSection,
                                    translation = (lineKey?.let { translations[it] } ?: emptyList()) +
                                        pendingTranslations,
                                    transliteration = lineKey?.let { transliterations[it] }
                                        ?: pendingTransliteration,
                                )
                            }
                        }
                    }

                    else -> Unit
                }

                event = parser.next()
            }

            if (lines.isEmpty()) return null

            val synced = lines.all { it.startMs >= 0 }
            if (!synced) {
                return Lyrics(
                    lines = lines.alternateVoices(),
                    synced = false,
                )
            }

            Lyrics(
                lines = lines.sortedBy { it.startMs }
                    .markInstrumentalLines()
                    .fillLineEnds()
                    .mergeSimultaneousDuplicates()
                    .alternateVoices()
                    .withInterludes(),
                synced = true,
            )
        }.getOrNull()

    private fun XmlReader.attr(localName: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeLocalName(i) == localName) return getAttributeValue(i)
        }
        return null
    }

    private fun parseTime(value: String?): Long {
        if (value.isNullOrBlank()) return -1L

        val trimmed = value.trim()

        return runCatching {
            when {
                trimmed.endsWith("ms") -> trimmed.dropLast(2).toDouble().toLong()

                trimmed.endsWith("s") && !trimmed.contains(':') ->
                    (trimmed.dropLast(1).toDouble() * 1000).toLong()

                else -> {
                    val parts = trimmed.split(':')
                    val seconds = parts.last().toDouble()
                    val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0L
                    val hours = parts.getOrNull(parts.size - 3)?.toLongOrNull() ?: 0L

                    (hours * 3600_000) + (minutes * 60_000) + (seconds * 1000).toLong()
                }
            }
        }.getOrDefault(-1L)
    }

    private class SpanFrame(
        val beginMs: Long,
        val endMs: Long,
        val textStart: Int,
        val background: Boolean,
    ) {
        var hadChild = false
    }

    private fun isFormattingOnly(chunk: String): Boolean =
        chunk.isNotEmpty() &&
                chunk.all { it.isWhitespace() } &&
                chunk.any { it == '\n' || it == '\r' }

    private fun readTimedText(parser: XmlReader): Pair<String, List<LyricWord>> {
        val text = StringBuilder()
        val words = mutableListOf<LyricWord>()
        val spanStack = ArrayDeque<SpanFrame>()
        var pendingSpace = false

        fun flushSpace() {
            if (pendingSpace && text.isNotEmpty() && text.last() != '\n') text.append(' ')
            pendingSpace = false
        }

        var depth = 1
        var event = parser.next()

        while (depth > 0) {
            when (event) {
                EventType.START_ELEMENT -> {
                    depth++

                    if (parser.localName == "span") {
                        flushSpace()

                        spanStack.addLast(
                            SpanFrame(
                                beginMs = parseTime(parser.attr("begin")),
                                endMs = parseTime(parser.attr("end")),
                                textStart = text.length,
                                background = false,
                            ),
                        )
                    }
                }

                EventType.TEXT, EventType.IGNORABLE_WHITESPACE -> if (!isFormattingOnly(parser.text)) {
                    parser.text.forEach { c ->
                        if (c.isWhitespace()) {
                            pendingSpace = true
                        } else {
                            flushSpace()
                            text.append(c)
                        }
                    }
                }

                EventType.END_ELEMENT -> {
                    depth--

                    if (parser.localName == "span" && spanStack.isNotEmpty()) {
                        val frame = spanStack.removeLast()

                        if (frame.beginMs >= 0 && text.length > frame.textStart) {
                            words += LyricWord(
                                startMs = frame.beginMs,
                                endMs = frame.endMs,
                                start = frame.textStart,
                                end = text.length,
                                background = false,
                            )
                        }
                    }
                }

                else -> Unit
            }

            if (depth > 0) event = parser.next()
        }

        return text.toString().trim() to words
    }

    private fun readTranslationSegments(parser: XmlReader): List<String> {
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        val bgStack = ArrayDeque<Boolean>()
        var currentBg = false
        var pendingSpace = false

        fun flushSpace() {
            if (pendingSpace && current.isNotEmpty() && current.last() != '\n') current.append(' ')
            pendingSpace = false
        }

        fun cutSegment() {
            val text = current.toString().trim()
            if (text.isNotEmpty()) segments += text

            current.clear()
            pendingSpace = false
        }

        var depth = 1
        var event = parser.next()

        while (depth > 0) {
            when (event) {
                EventType.START_ELEMENT -> {
                    depth++

                    if (parser.localName == "span") {
                        val isBg = currentBg || parser.attr("role") == ROLE_BACKGROUND

                        if (isBg != currentBg) {
                            cutSegment()
                            currentBg = isBg
                        }

                        bgStack.addLast(currentBg)
                        flushSpace()
                    }
                }

                EventType.TEXT, EventType.IGNORABLE_WHITESPACE -> if (!isFormattingOnly(parser.text)) {
                    parser.text.forEach { c ->
                        if (c.isWhitespace()) {
                            pendingSpace = true
                        } else {
                            flushSpace()
                            current.append(c)
                        }
                    }
                }

                EventType.END_ELEMENT -> {
                    depth--

                    if (parser.localName == "span" && bgStack.isNotEmpty()) {
                        bgStack.removeLast()

                        val outerBg = bgStack.lastOrNull() ?: false
                        if (outerBg != currentBg) {
                            cutSegment()
                            currentBg = outerBg
                        }
                    }
                }

                else -> Unit
            }

            if (depth > 0) event = parser.next()
        }

        cutSegment()

        return segments
    }

    /**
     * Tracks the order and type of `ttm:agent` declarations so each agent, solo
     * or a named group, gets a stable [LyricLine.singer] index in the order it's
     * first actually used on a line, independent of how many the document
     * declares. `-1` is reserved for lines with no declared agent at all.
     */
    private class Agents {

        private val types = mutableMapOf<String, String>()
        private val order = mutableListOf<String>()

        fun register(id: String?, type: String?) {
            if (id == null) return
            if (type != null) types[id] = type
            if (types[id] != AGENT_TYPE_GROUP && id !in order) order += id
        }

        fun voiceFor(agentId: String?): Voice {
            if (agentId == null) return Voice.PRIMARY
            return if (types[agentId] == AGENT_TYPE_GROUP) Voice.GROUP else Voice.PRIMARY
        }

        fun singerFor(agentId: String?): Int {
            if (agentId == null) return 0

            if (agentId !in order) order += agentId

            return order.indexOf(agentId)
        }
    }
}

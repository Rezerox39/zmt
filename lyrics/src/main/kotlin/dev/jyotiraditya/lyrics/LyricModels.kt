package dev.jyotiraditya.lyrics

/**
 * Which side a line renders on in a duet layout. Not who's singing.
 *
 * This only ever flips between [PRIMARY] and [SECONDARY] whenever the singer
 * changes, no matter how many voices the source actually tags (`v1`, `v2`,
 * `v3`, and so on). If you need to know who's actually singing, that's
 * [LyricLine.singer].
 */
enum class Voice { PRIMARY, SECONDARY, GROUP }

/**
 * Timing for one word (or syllable, for CJK) inside the text it belongs to.
 *
 * @property start inclusive character offset into the owning text.
 * @property end exclusive character offset into the owning text.
 * @property background true for backing vocals or adlibs: LRC `[bg: ...]`
 *   lines, or TTML spans with `ttm:role="x-bg"`.
 */
data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val start: Int,
    val end: Int,
    val background: Boolean,
)

/**
 * A chunk of text with its own optional word timing, separate from whatever
 * line it's attached to. Used for a [LyricLine.transliteration] or one entry
 * in [LyricLine.translation].
 *
 * @property lang a BCP-47 tag like `en` or `zh-Hant` when the source bothered
 *   to tag one, so multiple languages can be told apart in the UI. Null if
 *   the source didn't tag it.
 */
data class TimedText(val text: String, val words: List<LyricWord> = emptyList(), val lang: String? = null)

/**
 * One line of lyrics, synced or not.
 *
 * @property singer stable 0-based id for whoever's singing, assigned in the
 *   order each voice tag first shows up (`vN` in LRC, `ttm:agent` in TTML). A
 *   named [Voice.GROUP] agent (TTML `type="group"`) gets its own index too,
 *   same as a solo singer. `-1` means the line has no declared agent at all:
 *   it's either an [interlude] or a group line synthesized by merging
 *   duplicate simultaneous lines from different singers. This is the field
 *   to key a color palette off of, since unlike [voice] it isn't capped at
 *   two.
 * @property translation zero or more full-line translations, can be more
 *   than one language, see [TimedText.lang].
 * @property transliteration a same-line reading, when the source gave one.
 */
data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val voice: Voice = Voice.PRIMARY,
    val singer: Int = 0,
    val sectionStart: Boolean = false,
    val interlude: Boolean = false,
    val translation: List<TimedText> = emptyList(),
    val transliteration: TimedText? = null,
)

/** Parse result. Either synced lines with real timestamps, or plain unsynced text. */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
)

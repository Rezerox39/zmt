package dev.jyotiraditya.dmt.domain.model

enum class SourceMode(val label: String) {
    LOCAL("local"),
    JELLYFIN("jellyfin"),
    TELEGRAM("telegram"),
    YOUTUBE("youtube"),
}

enum class LibrarySort(val label: String) {
    TITLE("title"),
    ARTIST("artist"),
    RECENT_ADDED("recent-a"),
    RECENT_MODIFIED("recent-m"),
    RECENT("recent"),
    ;

    val comparator: Comparator<Track>
        get() = when (this) {
            TITLE -> Comparator { a, b -> a.title.compareTo(b.title, ignoreCase = true) }
            ARTIST -> Comparator<Track> { a, b -> a.artist.compareTo(b.artist, ignoreCase = true) }
                .thenComparator { a, b -> a.title.compareTo(b.title, ignoreCase = true) }
            RECENT_ADDED, RECENT -> compareByDescending { it.dateAdded }
            RECENT_MODIFIED -> compareByDescending { it.dateModified }
        }

    fun next(mode: SourceMode): LibrarySort {
        val cycle = when (mode) {
            SourceMode.LOCAL -> listOf(TITLE, ARTIST, RECENT_ADDED, RECENT_MODIFIED)
            SourceMode.JELLYFIN -> listOf(TITLE, ARTIST, RECENT)
            SourceMode.TELEGRAM -> listOf(TITLE, ARTIST, RECENT)
            SourceMode.YOUTUBE -> listOf(TITLE, ARTIST)
        }
        return cycle[(cycle.indexOf(this) + 1) % cycle.size]
    }
}

enum class ThemeOption(val label: String) {
    AMOLED_BLACK("amoled"),
    CRIMSON_NOIR("crimson noir"),
    AQUA_GLASS("aqua glass"),
    ;
}

data class DmtSettings(
    val wave: Boolean = true,
    val normalizeVolume: Boolean = false,
    val cols: Int = 96,
    val listSpecs: Boolean = true,
    val romanizedLyrics: Boolean = false,
    val rawArt: Boolean = false,
    val theme: ThemeOption = ThemeOption.AMOLED_BLACK,
    val blockedFolders: Set<String> = emptySet(),
    val sourceMode: SourceMode = SourceMode.LOCAL,
    val librarySort: LibrarySort = LibrarySort.TITLE,
    val jellyfinUrl: String? = null,
    val jellyfinUserId: String? = null,
    val jellyfinToken: String? = null,
    val telegramChannelId: Long? = null,
    val telegramChannelName: String? = null,
    val telegramAuthState: String? = null,
)

data class LastSession(
    val queueIds: List<Long>,
    val index: Int,
    val positionMs: Long,
)

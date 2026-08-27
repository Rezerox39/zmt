package dev.abhi.zmt.domain.model

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
    RED_AMOLED("red amoled"),
    LIQUID_GLASS("liquid glass"),
    ;
}

enum class AccentColor(val argb: Long, val label: String) {
    CRIMSON(0xFFDC143C, "crimson"),
    AMBER(0xFFE8A33D, "amber"),
    STEEL(0xFF7A9BB3, "steel"),
    MOSS(0xFF98A96F, "moss"),
    MAUVE(0xFFAF87A3, "mauve"),
    TEAL(0xFF74AB9E, "teal"),
    LAVENDER(0xFF9389C4, "lavender"),
    CLAY(0xFFC08575, "clay"),
    SKY(0xFF80A6C4, "sky"),
    ROSE(0xFFBD84A0, "rose"),
    SAGE(0xFF8AB194, "sage"),
    PERIWINKLE(0xFF8C96C0, "periwinkle"),
    RUST(0xFFB97B62, "rust"),
    ;

    fun next(): AccentColor = entries[(ordinal + 1) % entries.size]
}


enum class CrossfadeDuration(val seconds: Int, val label: String) {
    OFF(0, "off"),
    SHORT(3, "3s"),
    MEDIUM(5, "5s"),
    LONG(8, "8s"),
    ;

    fun next(): CrossfadeDuration = entries[(ordinal + 1) % entries.size]
}

enum class SleepFade(val label: String) {
    OFF("off"),
    LOW("low"),
    MED("med"),
    ;

    fun next(): SleepFade = entries[(ordinal + 1) % entries.size]
}

data class DmtSettings(
    val wave: Boolean = true,
    val normalizeVolume: Boolean = false,
    val cols: Int = 96,
    val listSpecs: Boolean = true,
    val romanizedLyrics: Boolean = false,
    val rawArt: Boolean = false,
    val theme: ThemeOption = ThemeOption.AMOLED_BLACK,
    val accent: AccentColor = AccentColor.CRIMSON,
    val stopOnDismiss: Boolean = false,
    val setupDone: Boolean = false,
    val blockedFolders: Set<String> = emptySet(),
    val sourceMode: SourceMode = SourceMode.LOCAL,
    val librarySort: LibrarySort = LibrarySort.TITLE,
    val jellyfinUrl: String? = null,
    val jellyfinUserId: String? = null,
    val jellyfinToken: String? = null,
    val telegramChannelId: Long? = null,
    val telegramChannelName: String? = null,
    val telegramAuthState: String? = null,
    val crossfadeDuration: CrossfadeDuration = CrossfadeDuration.OFF,
    val sleepFade: SleepFade = SleepFade.OFF,
    val equalizerPreset: Int = -1,
    val volumeFadeOnSleep: Boolean = false,
    val gapless: Boolean = true,
    val fingerprintLock: Boolean = false,
    val uploadedTrackIds: Set<String> = emptySet(),
)

data class LastSession(
    val queueIds: List<Long>,
    val index: Int,
    val positionMs: Long,
)

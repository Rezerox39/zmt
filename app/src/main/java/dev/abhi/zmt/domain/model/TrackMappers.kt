package dev.abhi.zmt.domain.model

private val COLLAB_SEPARATORS = Regex("""\s*[;/]\s*""")

val Track.primaryArtist: String
    get() = albumArtist.ifBlank { artist }

fun String.asCredit(): String = replace(COLLAB_SEPARATORS, " + ")

fun List<Track>.toFolders(): List<Folder> =
    asSequence()
        .filter { it.path.isNotEmpty() }
        .groupBy { it.path.substringBeforeLast('/') }
        .map { (dir, tracks) ->
            Folder(
                name = dir.removePrefix("/storage/emulated/0/").ifEmpty { "/" },
                path = dir,
                tracks = tracks,
            )
        }
        .sortedBy { it.name.lowercase() }

fun List<Track>.toGenres(): List<Genre> =
    groupBy { it.genre?.takeIf(String::isNotBlank)?.lowercase() ?: UNKNOWN_GENRE }
        .map { (_, tracks) ->
            Genre(
                name = tracks.first().genre?.takeIf(String::isNotBlank) ?: UNKNOWN_GENRE,
                tracks = tracks,
            )
        }
        .sortedBy { it.name.lowercase() }

fun List<Track>.toArtists(): List<Artist> =
    groupBy { it.primaryArtist.lowercase() }
        .map { (_, tracks) ->
            Artist(
                name = tracks.groupingBy { it.primaryArtist }
                    .eachCount()
                    .maxBy { it.value }
                    .key,
                albums = tracks.map { it.album }.distinct().size,
                tracks = tracks.sortedWith(
                    compareBy({ it.album.lowercase() }, { it.trackNumber }),
                ),
            )
        }
        .sortedBy { it.name.lowercase() }

fun List<Track>.toAlbums(): List<Album> =
    groupBy { it.album }
        .map { (name, tracks) ->
            val artists = tracks.map { it.primaryArtist }.distinctBy { it.lowercase() }
            Album(
                name = name,
                artist = artists.singleOrNull() ?: "various artists",
                tracks = tracks.sortedBy { it.trackNumber },
            )
        }
        .sortedBy { it.name.lowercase() }

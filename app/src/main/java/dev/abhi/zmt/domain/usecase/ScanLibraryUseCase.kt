package dev.abhi.zmt.domain.usecase

import dev.abhi.zmt.domain.model.LibrarySnapshot
import dev.abhi.zmt.domain.model.toAlbums
import dev.abhi.zmt.domain.model.toArtists
import dev.abhi.zmt.domain.model.toFolders
import dev.abhi.zmt.domain.model.toGenres
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ScanLibraryUseCase @Inject constructor(
    private val mediaSourceProvider: MediaSourceProvider,
) {
    /** Emits whenever the tracks of the source in use have changed. */
    fun changes(): Flow<Unit> = flow { emitAll(mediaSourceProvider.current().changes()) }

    suspend operator fun invoke(): LibrarySnapshot =
        withContext(Dispatchers.IO) {
            val tracks = mediaSourceProvider.current().scan()
            LibrarySnapshot(
                tracks = tracks,
                albums = tracks.toAlbums(),
                artists = tracks.toArtists(),
                folders = tracks.toFolders(),
                genres = tracks.toGenres(),
            )
        }
}

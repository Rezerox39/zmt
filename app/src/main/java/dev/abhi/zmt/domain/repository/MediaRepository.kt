package dev.abhi.zmt.domain.repository

import dev.abhi.zmt.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface MediaRepository {
    suspend fun scan(): List<Track>

    fun invalidate() = Unit

    /** Emits whenever the tracks this holds have changed and are worth reading again. */
    fun changes(): Flow<Unit> = emptyFlow()
}

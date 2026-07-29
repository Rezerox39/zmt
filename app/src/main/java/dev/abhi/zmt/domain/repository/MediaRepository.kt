package dev.abhi.zmt.domain.repository

import dev.abhi.zmt.domain.model.Track

interface MediaRepository {
    suspend fun scan(): List<Track>
}

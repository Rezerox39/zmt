package dev.abhi.zmt.domain.usecase

import dev.abhi.zmt.data.remote.jellyfin.JellyfinApi
import dev.abhi.zmt.domain.model.SourceMode
import dev.abhi.zmt.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class JellyfinLoginUseCase @Inject constructor(
    private val api: JellyfinApi,
    private val settingsRepository: PreferencesRepository,
) {
    suspend operator fun invoke(url: String, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val auth = api.authenticate(url, username, password)
                val settings = settingsRepository.settings.first()

                settingsRepository.save(
                    settings.copy(
                        sourceMode = SourceMode.JELLYFIN,
                        jellyfinUrl = url,
                        jellyfinUserId = auth.userId,
                        jellyfinToken = auth.token,
                    ),
                )
            }
        }
}

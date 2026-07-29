package dev.abhi.zmt.domain.usecase

import dev.abhi.zmt.di.JellyfinSource
import dev.abhi.zmt.di.Local
import dev.abhi.zmt.di.TelegramSource
import dev.abhi.zmt.di.YouTubeSource
import dev.abhi.zmt.domain.model.SourceMode
import dev.abhi.zmt.domain.repository.MediaRepository
import dev.abhi.zmt.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaSourceProvider @Inject constructor(
    @Local private val local: MediaRepository,
    @JellyfinSource private val jellyfin: MediaRepository,
    @TelegramSource private val telegram: MediaRepository,
    @YouTubeSource private val youtube: MediaRepository,
    private val settingsRepository: PreferencesRepository,
) {
    suspend fun current(): MediaRepository =
        when (settingsRepository.settings.first().sourceMode) {
            SourceMode.LOCAL -> local
            SourceMode.JELLYFIN -> jellyfin
            SourceMode.TELEGRAM -> telegram
            SourceMode.YOUTUBE -> youtube
        }
}

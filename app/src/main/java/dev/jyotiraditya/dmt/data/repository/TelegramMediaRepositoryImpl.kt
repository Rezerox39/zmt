package dev.jyotiraditya.dmt.data.repository

import dev.jyotiraditya.dmt.data.remote.telegram.TelegramChannelSync
import dev.jyotiraditya.dmt.data.remote.telegram.TelegramClient
import dev.jyotiraditya.dmt.domain.model.Track
import dev.jyotiraditya.dmt.domain.repository.MediaRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramMediaRepositoryImpl @Inject constructor(
    private val telegramClient: TelegramClient,
    private val channelSync: TelegramChannelSync,
    private val settingsRepository: PreferencesRepository,
) : MediaRepository {

    override suspend fun scan(): List<Track> {
        if (!telegramClient.isLoggedIn()) return emptyList()

        val settings = settingsRepository.settings.first()
        val channelId = settings.telegramChannelId ?: return emptyList()

        return channelSync.scanChannel(channelId)
    }
}

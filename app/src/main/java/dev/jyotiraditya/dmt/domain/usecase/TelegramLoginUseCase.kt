package dev.jyotiraditya.dmt.domain.usecase

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jyotiraditya.dmt.data.remote.telegram.TelegramAuthStep
import dev.jyotiraditya.dmt.data.remote.telegram.TelegramClient
import dev.jyotiraditya.dmt.data.remote.telegram.TelegramNativeBridge
import dev.jyotiraditya.dmt.domain.model.SourceMode
import dev.jyotiraditya.dmt.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "TelegramLogin"

class TelegramLoginUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val telegramClient: TelegramClient,
    private val settingsRepository: PreferencesRepository,
) {

    fun initialize(): String? {
        if (telegramClient.isInitialized()) return null
        if (!TelegramNativeBridge.isAvailable()) {
            val err = TelegramNativeBridge.getLoadError() ?: "TDLib library not available"
            Log.e(TAG, "init failed: $err")
            return err
        }
        try {
            telegramClient.initialize(context.filesDir.absolutePath)
            Log.i(TAG, "TelegramClient initialized successfully")
            return null
        } catch (e: Exception) {
            val msg = "Init error: ${e.message}"
            Log.e(TAG, msg, e)
            return msg
        }
    }

    fun observeAuthState(): Flow<String> {
        return telegramClient.authState.map { state ->
            when (state.step) {
                is TelegramAuthStep.NeedPhoneNumber -> "phone"
                is TelegramAuthStep.NeedCode -> "code"
                is TelegramAuthStep.NeedPassword -> "password"
                is TelegramAuthStep.LoggedIn -> "logged_in"
                is TelegramAuthStep.Error -> "error: ${(state.step as TelegramAuthStep.Error).message}"
            }
        }
    }

    suspend fun sendPhoneNumber(phoneNumber: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                telegramClient.requestPhoneNumber(phoneNumber)
            }
        }

    suspend fun submitCode(code: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                telegramClient.submitCode(code)
            }
        }

    suspend fun submitPassword(password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                telegramClient.submitPassword(password)
            }
        }

    suspend fun resolveChannel(channelInput: String): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val channelId = channelInput.trim().toLongOrNull()
                    ?: run {
                        val username = channelInput.trim().removePrefix("https://t.me/").removePrefix("t.me/")
                        val chat = telegramClient.searchChannel(username)
                        chat.id
                    }

                val settings = settingsRepository.settings.first()
                settingsRepository.save(
                    settings.copy(
                        sourceMode = SourceMode.TELEGRAM,
                        telegramChannelId = channelId,
                    ),
                )

                channelId
            }
        }

    suspend fun logout() {
        telegramClient.close()
        val settings = settingsRepository.settings.first()
        settingsRepository.save(
            settings.copy(
                sourceMode = SourceMode.LOCAL,
                telegramChannelId = null,
                telegramAuthState = null,
            ),
        )
    }

    fun currentAuthStep(): TelegramAuthStep {
        return telegramClient.authState.value.step
    }
}

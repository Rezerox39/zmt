package dev.jyotiraditya.dmt.data.remote.telegram

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "TelegramClient"
private const val DEBUG_TAG = "TDLibDebug"
private const val REQUEST_TIMEOUT_MS = 30_000L

@Singleton
class TelegramClient @Inject constructor() {

    private var client: Client? = null
    private var nativeLoaded = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow(TelegramAuthState())
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    @Volatile
    private var authenticated = false

    private var databasePath: String = ""

    fun isInitialized(): Boolean = client != null
    fun isLoggedIn(): Boolean = authenticated

    private val updateHandler = Client.ResultHandler { update ->
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                val stateName = update.authorizationState::class.simpleName ?: "Unknown"
                Log.d(DEBUG_TAG, "Auth state shifted to: $stateName")
                onAuthorizationStateUpdated(update.authorizationState)
            }
            is TdApi.UpdateFile -> {
                Log.d(DEBUG_TAG, "File update: ${update.file?.id}")
            }
            is TdApi.Error -> {
                Log.e(DEBUG_TAG, "TDLib error: ${update.code} - ${update.message}")
            }
            else -> {
                Log.d(DEBUG_TAG, "TDLib update: ${update::class.simpleName}")
            }
        }
    }

    fun initialize(path: String) {
        if (client != null) {
            Log.d(TAG, "Already initialized")
            return
        }
        databasePath = path

        if (!nativeLoaded) {
            try {
                System.loadLibrary("tdjni")
                nativeLoaded = true
                Log.i(TAG, "TDLib native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load TDLib native library", e)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load TDLib native library", e)
                return
            }
        }

        try {
            Client.execute(TdApi.SetLogVerbosityLevel(3))
            Log.d(DEBUG_TAG, "TDLib verbosity set to 3")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set log verbosity: ${e.message}")
        }

        client = Client.create(updateHandler, null, null)
        Log.i(TAG, "TDLib client created successfully")
        Log.d(DEBUG_TAG, "Client instance: $client")
    }

    private fun onAuthorizationStateUpdated(authState: TdApi.AuthorizationState) {
        when (authState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                Log.d(DEBUG_TAG, "Setting TDLib parameters...")
                val dbDir = File(databasePath, "tdb").absolutePath
                val filesDir = File(databasePath, "tdf").absolutePath
                File(dbDir).mkdirs()
                File(filesDir).mkdirs()

                client?.send(TdApi.SetTdlibParameters(
                    false,
                    dbDir,
                    filesDir,
                    null,
                    true,
                    true,
                    true,
                    false,
                    94575,
                    "a3406de891e9015a",
                    "en",
                    "Android",
                    "1.0",
                    "1.0"
                ), updateHandler)
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                Log.d(DEBUG_TAG, "Ready for phone number input")
                authenticated = false
                _authState.value = TelegramAuthState(step = TelegramAuthStep.NeedPhoneNumber)
            }
            is TdApi.AuthorizationStateWaitCode -> {
                Log.d(DEBUG_TAG, "Ready for code input")
                authenticated = false
                _authState.value = _authState.value.copy(step = TelegramAuthStep.NeedCode)
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                Log.d(DEBUG_TAG, "Ready for password input")
                authenticated = false
                _authState.value = _authState.value.copy(step = TelegramAuthStep.NeedPassword)
            }
            is TdApi.AuthorizationStateReady -> {
                Log.d(DEBUG_TAG, "Authorization successful!")
                authenticated = true
                _authState.value = TelegramAuthState(step = TelegramAuthStep.LoggedIn)
                Log.i(TAG, "Telegram authorized successfully")
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                Log.d(DEBUG_TAG, "Logging out")
                authenticated = false
                Log.i(TAG, "Logging out")
            }
            is TdApi.AuthorizationStateClosed -> {
                Log.d(DEBUG_TAG, "Client closed")
                authenticated = false
                Log.i(TAG, "TDLib client closed")
            }
            is TdApi.AuthorizationStateWaitRegistration -> {
                Log.d(DEBUG_TAG, "Need registration")
                authenticated = false
                _authState.value = TelegramAuthState(step = TelegramAuthStep.NeedCode)
            }
            else -> {
                Log.d(DEBUG_TAG, "Unhandled auth state: ${authState::class.simpleName}")
            }
        }
    }

    private fun sanitizePhoneNumber(phoneNumber: String): String {
        val cleaned = phoneNumber.replace(Regex("[\\s\\-()]+"), "")
        return if (cleaned.startsWith("+")) cleaned else "+$cleaned"
    }

    suspend fun requestPhoneNumber(phoneNumber: String) {
        val sanitized = sanitizePhoneNumber(phoneNumber)
        Log.d(DEBUG_TAG, "Sending phone number: $sanitized")
        _authState.value = _authState.value.copy(phoneNumber = sanitized)
        val c = client
        if (c == null) {
            Log.e(DEBUG_TAG, "Client is null! Cannot send phone number.")
            return
        }
        Log.d(DEBUG_TAG, "Client instance is alive: $c")
        c.send(TdApi.SetAuthenticationPhoneNumber(
            sanitized,
            TdApi.PhoneNumberAuthenticationSettings(
                false, false, false, false, false, null, null
            )
        ), updateHandler)
        Log.d(DEBUG_TAG, "Phone number request sent to TDLib")
    }

    suspend fun submitCode(code: String) {
        Log.d(DEBUG_TAG, "Submitting code: ${code.take(2)}**")
        client?.send(TdApi.CheckAuthenticationCode(code), updateHandler)
    }

    suspend fun submitPassword(password: String) {
        Log.d(DEBUG_TAG, "Submitting password")
        client?.send(TdApi.CheckAuthenticationPassword(password), updateHandler)
    }

    suspend fun logout() {
        client?.send(TdApi.LogOut(), updateHandler)
        authenticated = false
        _authState.value = TelegramAuthState()
    }

    suspend fun searchChannel(username: String): TelegramChannelInfo {
        val result = sendRequest(TdApi.SearchPublicChat(username))
        return TelegramChannelInfo(
            id = result.id,
            title = result.title,
            username = username,
        )
    }

    suspend fun getChannelAudioMessages(
        channelId: Long,
        limit: Int = 100,
        fromMessageId: Long = 0,
    ): List<TelegramAudioMessage> {
        val result = sendRequest(
            TdApi.GetChatHistory(
                channelId,
                fromMessageId,
                0,
                limit,
                false,
            )
        )

        return result.messages?.mapNotNull { msg ->
            val content = msg.content ?: return@mapNotNull null
            if (content is TdApi.MessageAudio) {
                val audio = content.audio
                val file = audio.audio
                TelegramAudioMessage(
                    messageId = msg.id,
                    fileId = file.id.toLong(),
                    fileUniqueId = file.remote?.uniqueId ?: "",
                    title = audio.title.ifBlank { "unknown title" },
                    performer = audio.performer.ifBlank { "unknown artist" },
                    durationMs = audio.duration * 1000L,
                    mimeType = audio.mimeType.ifBlank { "audio/unknown" },
                    fileSize = file.expectedSize,
                    thumbnailFileId = audio.albumCoverThumbnail?.file?.id?.toLong(),
                    date = msg.date.toLong(),
                )
            } else {
                null
            }
        } ?: emptyList()
    }

    suspend fun downloadFile(fileId: Long): TdApi.File {
        return sendRequest(TdApi.DownloadFile(fileId.toInt(), 1, 0, 0, true))
    }

    suspend fun getFile(fileId: Long): TdApi.File {
        return sendRequest(TdApi.GetFile(fileId.toInt()))
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : TdApi.Object> sendRequest(function: TdApi.Function<T>): T {
        val c = client ?: throw IllegalStateException("Telegram client not initialized")
        return withTimeout(REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                c.send(function) { result ->
                    if (result is TdApi.Error) {
                        Log.e(DEBUG_TAG, "Request failed: ${result.code} - ${result.message}")
                        continuation.resumeWith(
                            Result.failure(Exception("TDLib error ${result.code}: ${result.message}"))
                        )
                    } else {
                        continuation.resume(result as T)
                    }
                }
            }
        }
    }

    suspend fun close() {
        client?.send(TdApi.Close(), updateHandler)
        client = null
        authenticated = false
        _authState.value = TelegramAuthState()
    }
}

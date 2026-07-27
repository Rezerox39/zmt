package dev.jyotiraditya.dmt.data.remote.telegram

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "TelegramClient"
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
                onAuthorizationStateUpdated(update.authorizationState)
            }
            is TdApi.UpdateFile -> {
                // File download updates
            }
            is TdApi.Error -> {
                Log.e(TAG, "TDLib error: \${update.code} - \${update.message}")
            }
            else -> {}
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
            Client.execute(TdApi.SetLogVerbosityLevel(0))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set log verbosity: \${e.message}")
        }

        client = Client.create(updateHandler, null, null)
        Log.i(TAG, "TDLib client created successfully")
    }

    private fun onAuthorizationStateUpdated(authState: TdApi.AuthorizationState) {
        when (authState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
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
                authenticated = false
                _authState.value = TelegramAuthState(step = TelegramAuthStep.NeedPhoneNumber)
            }
            is TdApi.AuthorizationStateWaitCode -> {
                authenticated = false
                _authState.value = _authState.value.copy(step = TelegramAuthStep.NeedCode)
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                authenticated = false
                _authState.value = _authState.value.copy(step = TelegramAuthStep.NeedPassword)
            }
            is TdApi.AuthorizationStateReady -> {
                authenticated = true
                _authState.value = TelegramAuthState(step = TelegramAuthStep.LoggedIn)
                Log.i(TAG, "Telegram authorized successfully")
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                authenticated = false
                Log.i(TAG, "Logging out")
            }
            is TdApi.AuthorizationStateClosed -> {
                authenticated = false
                Log.i(TAG, "TDLib client closed")
            }
            else -> {}
        }
    }

    suspend fun requestPhoneNumber(phoneNumber: String) {
        _authState.value = _authState.value.copy(phoneNumber = phoneNumber)
        val c = client ?: return
        c.send(TdApi.SetAuthenticationPhoneNumber(
            phoneNumber,
            TdApi.PhoneNumberAuthenticationSettings(
                false, false, false, false, false, null, null
            )
        ), updateHandler)
    }

    suspend fun submitCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code), updateHandler)
    }

    suspend fun submitPassword(password: String) {
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
                        continuation.resumeWith(
                            Result.failure(Exception("TDLib error \${result.code}: \${result.message}"))
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

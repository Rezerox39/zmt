package dev.jyotiraditya.dmt.data.remote.telegram

import android.util.Log
import dev.jyotiraditya.dmt.BuildConfig
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
private const val REQUEST_TIMEOUT_MS = 60_000L

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

    @Volatile private var pendingPhoneNumber: String? = null
    @Volatile private var pendingCode: String? = null
    @Volatile private var pendingPassword: String? = null

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
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.Error("TDLib error ${update.code}: ${update.message}")
                )
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

        // Validate BuildConfig credentials
        val apiId = BuildConfig.TELEGRAM_API_ID
        val apiHash = BuildConfig.TELEGRAM_API_HASH
        Log.d(TAG, "Telegram API ID: $apiId, Hash length: ${apiHash.length}")

        if (apiId == 0 || apiHash.isBlank()) {
            val msg = "Telegram API credentials not configured. Set TELEGRAM_API_ID and TELEGRAM_API_HASH in local.properties"
            Log.e(TAG, msg)
            _authState.value = _authState.value.copy(
                step = TelegramAuthStep.Error(msg)
            )
            return
        }

        client = Client.create(updateHandler, null, null)
        Log.i(TAG, "TDLib client created successfully")
    }

    private fun onAuthorizationStateUpdated(authState: TdApi.AuthorizationState) {
        when (authState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                Log.d(DEBUG_TAG, "Setting TDLib parameters...")
                val dbDir = File(databasePath, "tdb").absolutePath
                val filesDir = File(databasePath, "tdf").absolutePath
                File(dbDir).mkdirs()
                File(filesDir).mkdirs()

                val apiId = BuildConfig.TELEGRAM_API_ID
                val apiHash = BuildConfig.TELEGRAM_API_HASH
                Log.d(DEBUG_TAG, "Using API ID: $apiId")

                client?.send(
                    TdApi.SetTdlibParameters(
                        false, dbDir, filesDir, null,
                        true, true, true, false,
                        apiId, apiHash,
                        "en", "Android", "1.0", "1.0"
                    ), updateHandler
                )
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                Log.d(DEBUG_TAG, "TDLib ready for phone number")
                authenticated = false
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.NeedPhoneNumber
                )
                // Auto-send pending phone number if available
                pendingPhoneNumber?.let { phone ->
                    pendingPhoneNumber = null
                    sendSetPhoneNumber(phone)
                }
            }

            is TdApi.AuthorizationStateWaitCode -> {
                Log.d(DEBUG_TAG, "TDLib waiting for code")
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.NeedCode
                )
                pendingCode?.let { code ->
                    pendingCode = null
                    sendCheckCode(code)
                }
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                Log.d(DEBUG_TAG, "TDLib waiting for password (2FA)")
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.NeedPassword
                )
                pendingPassword?.let { password ->
                    pendingPassword = null
                    sendCheckPassword(password)
                }
            }

            is TdApi.AuthorizationStateReady -> {
                Log.i(DEBUG_TAG, "TDLib authorized successfully!")
                authenticated = true
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.LoggedIn
                )
            }

            is TdApi.AuthorizationStateLoggingOut -> {
                Log.d(DEBUG_TAG, "TDLib logging out...")
                authenticated = false
            }

            is TdApi.AuthorizationStateClosed -> {
                Log.d(DEBUG_TAG, "TDLib client closed")
                authenticated = false
                client = null
            }

            else -> {
                Log.d(DEBUG_TAG, "Unhandled auth state: ${authState::class.simpleName}")
            }
        }
    }

    private fun sendSetPhoneNumber(phoneNumber: String) {
        Log.d(DEBUG_TAG, "Sending phone number: ${phoneNumber.take(4)}****")
        client?.send(
            TdApi.SetAuthenticationPhoneNumber(phoneNumber, null),
            updateHandler
        )
    }

    private fun sendCheckCode(code: String) {
        Log.d(DEBUG_TAG, "Sending code: ${code.take(2)}**")
        client?.send(
            TdApi.CheckAuthenticationCode(code),
            updateHandler
        )
    }

    private fun sendCheckPassword(password: String) {
        Log.d(DEBUG_TAG, "Sending 2FA password")
        client?.send(
            TdApi.CheckAuthenticationPassword(password),
            updateHandler
        )
    }

    private fun sanitizePhoneNumber(phone: String): String {
        val cleaned = phone.replace(Regex("[\\s\\-\\(\\)]"), "")
        return if (cleaned.startsWith("+")) cleaned else "+$cleaned"
    }

    suspend fun requestPhoneNumber(phoneNumber: String) {
        val sanitized = sanitizePhoneNumber(phoneNumber)
        Log.d(DEBUG_TAG, "requestPhoneNumber: ${sanitized.take(6)}****")
        _authState.value = _authState.value.copy(phoneNumber = sanitized)
        pendingPhoneNumber = sanitized

        val currentStep = _authState.value.step
        if (currentStep is TelegramAuthStep.NeedPhoneNumber) {
            pendingPhoneNumber = null
            sendSetPhoneNumber(sanitized)
        }
    }

    suspend fun submitCode(code: String) {
        Log.d(DEBUG_TAG, "submitCode: ${code.take(2)}**")
        pendingCode = code

        val currentStep = _authState.value.step
        if (currentStep is TelegramAuthStep.NeedCode) {
            pendingCode = null
            sendCheckCode(code)
        }
    }

    suspend fun submitPassword(password: String) {
        Log.d(DEBUG_TAG, "submitPassword")
        pendingPassword = password

        val currentStep = _authState.value.step
        if (currentStep is TelegramAuthStep.NeedPassword) {
            pendingPassword = null
            sendCheckPassword(password)
        }
    }

    suspend fun logout() {
        pendingPhoneNumber = null
        pendingCode = null
        pendingPassword = null
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
            TdApi.GetChatHistory(channelId, fromMessageId, 0, limit, false)
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
            } else null
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
        pendingPhoneNumber = null
        pendingCode = null
        pendingPassword = null
        client?.send(TdApi.Close(), updateHandler)
        client = null
        authenticated = false
        _authState.value = TelegramAuthState()
    }
}

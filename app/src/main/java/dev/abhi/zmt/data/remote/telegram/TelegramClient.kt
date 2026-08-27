package dev.abhi.zmt.data.remote.telegram

import android.os.Build
import android.util.Log
import dev.abhi.zmt.BuildConfig
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

private const val TAG = "TelegramAuth"
private const val TAG_CLIENT = "TelegramTDLib"
private const val REQUEST_TIMEOUT_MS = 60_000L

/**
 * Production-grade TDLib client backed by a reactive state machine.
 *
 * Session persistence: TDLib persists the session in [databaseDirectory]/tdb
 * automatically. On re-init after a successful login, TDLib transitions directly
 * to [AuthorizationStateReady] without user input.
 *
 * Reconnection: TDLib handles reconnection internally. [UpdateConnectionState]
 * is surfaced for UI awareness but does NOT reset auth state.
 *
 * Auth flow: WaitTdlibParameters → WaitPhoneNumber → WaitCode → (WaitPassword) → Ready
 * Pending credentials are auto-sent when TDLib reaches the corresponding state.
 */
@Singleton
class TelegramClient @Inject constructor() {

    private var client: Client? = null
    private var nativeLoaded = false
    @Suppress("unused")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow(TelegramAuthState())
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    @Volatile
    private var authenticated = false

    @Volatile
    private var databasePath: String = ""

    @Volatile private var pendingPhoneNumber: String? = null
    @Volatile private var pendingCode: String? = null
    @Volatile private var pendingPassword: String? = null

    fun isInitialized(): Boolean = client != null
    fun isLoggedIn(): Boolean = authenticated

    private val updateHandler = Client.ResultHandler { update ->
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                val state = update.authorizationState
                val stateName = state::class.simpleName ?: "Unknown"
                Log.i(TAG_CLIENT, "Auth: $stateName")
                onAuthorizationStateUpdated(state)
            }
            is TdApi.UpdateConnectionState -> {
                val stateName = update.state::class.simpleName ?: "Unknown"
                Log.d(TAG_CLIENT, "Connection: $stateName")
                // Connection changes do NOT reset authentication
            }
            is TdApi.UpdateFile -> {
                Log.d(TAG_CLIENT, "File: id=${update.file?.id}")
            }
            is TdApi.Error -> {
                val msg = userFacingError(update.code, update.message)
                Log.e(TAG, "Error ${update.code}: ${update.message} -> $msg")
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.Error(msg)
                )
            }
            else -> {
                Log.d(TAG_CLIENT, "Update: ${update::class.simpleName}")
            }
        }
    }

    // ── Initialization ───────────────────────────────────────────────

    fun initialize(path: String) {
        if (client != null) {
            Log.d(TAG, "Already initialized, reusing session")
            return
        }
        databasePath = path

        val apiId = BuildConfig.TELEGRAM_API_ID
        val apiHash = BuildConfig.TELEGRAM_API_HASH
        Log.i(TAG, "Init: apiId=$apiId hashLen=${apiHash.length}")

        if (apiId == 0 || apiHash.isBlank()) {
            val msg = "Telegram API credentials not configured. " +
                "Set TELEGRAM_API_ID and TELEGRAM_API_HASH in local.properties"
            Log.e(TAG, msg)
            _authState.value = _authState.value.copy(
                step = TelegramAuthStep.Error(msg)
            )
            return
        }

        if (!nativeLoaded) {
            try {
                System.loadLibrary("tdjni")
                nativeLoaded = true
                Log.i(TAG_CLIENT, "Native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG_CLIENT, "Native library unavailable: ${e.message}")
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.Error(
                        "TDLib native library not available. " +
                        "Telegram sync requires a compiled TDLib JNI library."
                    )
                )
                return
            } catch (e: Exception) {
                Log.e(TAG_CLIENT, "Native library failed: ${e.message}", e)
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.Error("Init error: ${e.message}")
                )
                return
            }
        }

        try {
            Client.execute(TdApi.SetLogVerbosityLevel(3))
        } catch (_: Exception) { }

        client = Client.create(updateHandler, null, null)
        Log.i(TAG, "Client created — session restores automatically if previously logged in")
    }

    // ── Auth State Machine ───────────────────────────────────────────

    private fun onAuthorizationStateUpdated(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val dbDir = File(databasePath, "tdb").absolutePath
                val filesDir = File(databasePath, "tdf").absolutePath
                File(dbDir).mkdirs()
                File(filesDir).mkdirs()

                val apiId = BuildConfig.TELEGRAM_API_ID
                val apiHash = BuildConfig.TELEGRAM_API_HASH

                Log.i(TAG_CLIENT, "SetTdlibParameters: db=$dbDir apiId=$apiId")

                // NOTE: positional args required — TdApi is Java, no named params
                client?.send(
                    TdApi.SetTdlibParameters(
                        false,
                        dbDir,
                        filesDir,
                        null as ByteArray?,
                        true,
                        true,
                        true,
                        false,
                        apiId,
                        apiHash,
                        "en",
                        "Android",
                        Build.VERSION.RELEASE ?: "14",
                        "1.0",
                    ),
                    updateHandler,
                )
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                authenticated = false
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.NeedPhoneNumber
                )
                Log.i(TAG, "Need phone number")
                pendingPhoneNumber?.let { phone ->
                    pendingPhoneNumber = null
                    Log.d(TAG, "Auto-sending pending phone")
                    sendSetPhoneNumber(phone)
                }
            }

            is TdApi.AuthorizationStateWaitCode -> {
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.NeedCode
                )
                Log.i(TAG, "Need OTP code")
                pendingCode?.let { code ->
                    pendingCode = null
                    Log.d(TAG, "Auto-sending pending code")
                    sendCheckCode(code)
                }
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.NeedPassword
                )
                Log.i(TAG, "Need 2FA password")
                pendingPassword?.let { password ->
                    pendingPassword = null
                    Log.d(TAG, "Auto-sending pending password")
                    sendCheckPassword(password)
                }
            }

            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                val link = state.link?.take(50)
                Log.i(TAG, "Confirm on other device: $link")
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.Error(
                        "Please confirm login on your other device. " +
                        "Link: ${link ?: "check Telegram app"}"
                    )
                )
            }

            is TdApi.AuthorizationStateReady -> {
                authenticated = true
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.LoggedIn
                )
                Log.i(TAG, "Authenticated! Session active.")
            }

            is TdApi.AuthorizationStateLoggingOut -> {
                Log.i(TAG, "Logging out")
                authenticated = false
            }

            is TdApi.AuthorizationStateClosed -> {
                Log.i(TAG, "Closed")
                authenticated = false
                client = null
            }
        }
    }

    // ── Public Auth API ──────────────────────────────────────────────

    suspend fun requestPhoneNumber(phoneNumber: String) {
        val sanitized = sanitizePhoneNumber(phoneNumber)
        Log.i(TAG, "requestPhoneNumber: ${sanitized.take(5)}****")
        _authState.value = _authState.value.copy(phoneNumber = sanitized)
        pendingPhoneNumber = sanitized

        val currentStep = _authState.value.step
        if (currentStep is TelegramAuthStep.NeedPhoneNumber) {
            pendingPhoneNumber = null
            sendSetPhoneNumber(sanitized)
        }
    }

    suspend fun submitCode(code: String) {
        Log.i(TAG, "submitCode: ${code.take(2)}**")
        pendingCode = code

        val currentStep = _authState.value.step
        if (currentStep is TelegramAuthStep.NeedCode) {
            pendingCode = null
            sendCheckCode(code)
        }
    }

    suspend fun submitPassword(password: String) {
        Log.i(TAG, "submitPassword: ****")
        pendingPassword = password

        val currentStep = _authState.value.step
        if (currentStep is TelegramAuthStep.NeedPassword) {
            pendingPassword = null
            sendCheckPassword(password)
        }
    }

    suspend fun logout() {
        Log.i(TAG, "Explicit logout")
        clearPending()
        authenticated = false
        _authState.value = TelegramAuthState()
        client?.send(TdApi.LogOut(), updateHandler)
    }

    suspend fun close() {
        Log.i(TAG, "Closing client")
        clearPending()
        client?.send(TdApi.Close(), updateHandler)
        client = null
        authenticated = false
        _authState.value = TelegramAuthState()
    }

    private fun clearPending() {
        pendingPhoneNumber = null
        pendingCode = null
        pendingPassword = null
    }

    // ── Channel Sync API ─────────────────────────────────────────────

    suspend fun searchChannel(username: String): TelegramChannelInfo {
        Log.d(TAG, "Search channel: $username")
        return sendRequest(TdApi.SearchPublicChat(username)).let {
            TelegramChannelInfo(id = it.id, title = it.title, username = username)
        }
    }

    suspend fun getChannelAudioMessages(
        channelId: Long, limit: Int = 100, fromMessageId: Long = 0,
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

    suspend fun downloadFile(fileId: Long): TdApi.File =
        sendRequest(TdApi.DownloadFile(fileId.toInt(), 1, 0, 0, true))

    suspend fun getFile(fileId: Long): TdApi.File =
        sendRequest(TdApi.GetFile(fileId.toInt()))

    // ── Internals ────────────────────────────────────────────────────

    private fun sendSetPhoneNumber(phoneNumber: String) {
        Log.i(TAG, "Sending phone: ${phoneNumber.take(5)}****")
        client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), updateHandler)
    }

    private fun sendCheckCode(code: String) {
        Log.i(TAG, "Sending code: ${code.take(2)}**")
        client?.send(TdApi.CheckAuthenticationCode(code), updateHandler)
    }

    private fun sendCheckPassword(password: String) {
        Log.i(TAG, "Sending 2FA password")
        client?.send(TdApi.CheckAuthenticationPassword(password), updateHandler)
    }

    private fun sanitizePhoneNumber(phone: String): String {
        val cleaned = phone.replace(Regex("[\\s\\-\\(\\)]"), "")
        return if (cleaned.startsWith("+")) cleaned else "+$cleaned"
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : TdApi.Object> sendRequest(function: TdApi.Function<T>): T {
        val c = client ?: throw IllegalStateException("Telegram client not initialized")
        return withTimeout(REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                c.send(function) { result ->
                    if (result is TdApi.Error) {
                        val msg = userFacingError(result.code, result.message)
                        Log.e(TAG, "Request failed (${result.code}): $msg")
                        continuation.resumeWith(Result.failure(Exception(msg)))
                    } else {
                        continuation.resume(result as T)
                    }
                }
            }
        }
    }

    /**
     * Send an audio file to a Telegram channel.
     * The file must already exist locally at [filePath].
     * Returns the sent message ID on success, or throws on failure.
     */
    suspend fun sendAudioToChannel(
        channelId: Long,
        filePath: String,
        title: String,
        performer: String,
        duration: Int = 0,
        mimeType: String = "audio/mpeg",
    ): Long {
        val inputAudio = TdApi.InputMessageAudio(
            TdApi.InputFileLocal(filePath),
            null,
            duration,
            title,
            performer,
            null,
        )
        // Channels and non-forum chats have no topic/thread; pass null so the
        // message lands in the channel's main feed. MessageTopicThread is only
        // valid for non-forum supergroups and would be rejected here.
        val result = sendRequest(
            TdApi.SendMessage(
                channelId,
                null,
                null,
                null,
                null,
                inputAudio,
            )
        )
        return result.id
    }

    private fun userFacingError(code: Int, message: String): String = when {
        message.contains("PHONE_NUMBER_INVALID") ->
            "Invalid phone number. Include country code (e.g. +1234567890)"
        message.contains("PHONE_CODE_INVALID") ->
            "Invalid verification code. Try again."
        message.contains("PHONE_CODE_EXPIRED") ->
            "Verification code expired. Request a new one."
        message.contains("PASSWORD_HASH_INVALID") ->
            "Invalid password. Try again."
        message.contains("FLOOD") ->
            "Too many attempts. Please wait a few minutes."
        message.contains("NETWORK") || message.contains("Connection") ->
            "Network unavailable. Check your internet connection."
        message.contains("TIMEOUT") || message.contains("timeout") ->
            "Connection timed out. Try again."
        message.contains("API_ID_INVALID") ->
            "Invalid application configuration. Contact support."
        message.contains("AUTH_KEY_UNREGISTERED") || message.contains("AUTH_KEY_INVALID") ->
            "Session expired. Log in again."
        message.contains("USER_DEACTIVATED_BANNED") ->
            "This account has been banned by Telegram."
        else -> message
    }
}

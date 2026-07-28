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
private const val REQUEST_TIMEOUT_MS = 60_000L

/**
 * Reactive TDLib authentication state machine.
 *
 * Pattern inspired by Pixel Player: store pending credentials locally,
 * and auto-send them when TDLib transitions to the corresponding auth state.
 * This eliminates all race conditions from fire-and-forget.
 */
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

    // Pending credentials — stored here, auto-sent when TDLib reaches the right state
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
                // Surface auth errors to UI
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

        client = Client.create(updateHandler, null, null)
        Log.i(TAG, "TDLib client created successfully")
        Log.d(DEBUG_TAG, "Client instance: $client")
    }

    // ─── Reactive Auth State Machine ────────────────────────────────────

    private fun onAuthorizationStateUpdated(authState: TdApi.AuthorizationState) {
        when (authState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                Log.d(DEBUG_TAG, "Setting TDLib parameters...")
                val dbDir = File(databasePath, "tdb").absolutePath
                val filesDir = File(databasePath, "tdf").absolutePath
                File(dbDir).mkdirs()
                File(filesDir).mkdirs()

                client?.send(
                    TdApi.SetTdlibParameters(
                        false, dbDir, filesDir, null,
                        true, true, true, false,
                        94575, "a3406de891e9015a",
                        "en", "Android", "1.0", "1.0"
                    ), updateHandler
                )
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                Log.d(DEBUG_TAG, "TDLib ready for phone number")
                authenticated = false
                _authState.value = _authState.value.copy(step = TelegramAuthStep.NeedPhoneNumber)

                // AUTO-SEND: if phone number was stored before TDLib was ready, send it now
                val phone = pendingPhoneNumber
                if (phone != null) {
                    Log.d(DEBUG_TAG, "Auto-sending pending phone number: ${phone.take(6)}...")
                    pendingPhoneNumber = null
                    sendSetPhoneNumber(phone)
                }
            }

            is TdApi.AuthorizationStateWaitCode -> {
                Log.d(DEBUG_TAG, "TDLib ready for code")
                authenticated = false
                _authState.value = _authState.value.copy(step = TelegramAuthStep.NeedCode)

                // AUTO-SEND: if code was stored, send it now
                val code = pendingCode
                if (code != null) {
                    Log.d(DEBUG_TAG, "Auto-sending pending code")
                    pendingCode = null
                    sendCheckCode(code)
                }
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                Log.d(DEBUG_TAG, "TDLib ready for 2FA password")
                authenticated = false
                _authState.value = _authState.value.copy(step = TelegramAuthStep.NeedPassword)

                // AUTO-SEND: if password was stored, send it now
                val pw = pendingPassword
                if (pw != null) {
                    Log.d(DEBUG_TAG, "Auto-sending pending password")
                    pendingPassword = null
                    sendCheckPassword(pw)
                }
            }

            is TdApi.AuthorizationStateReady -> {
                Log.d(DEBUG_TAG, "Authorization successful!")
                authenticated = true
                _authState.value = TelegramAuthState(step = TelegramAuthStep.LoggedIn)
            }

            is TdApi.AuthorizationStateClosing -> {
                Log.d(DEBUG_TAG, "Closing authorization")
            }

            is TdApi.AuthorizationStateClosed -> {
                Log.d(DEBUG_TAG, "Client closed")
                authenticated = false
                _authState.value = TelegramAuthState()
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

    // ─── Internal Send Helpers ──────────────────────────────────────────

    private fun sendSetPhoneNumber(phoneNumber: String) {
        val c = client ?: run {
            Log.e(TAG, "Client is null, cannot send phone number")
            return
        }
        Log.d(DEBUG_TAG, "Sending SetAuthenticationPhoneNumber for: ${phoneNumber.take(6)}...")
        c.send(
            TdApi.SetAuthenticationPhoneNumber(
                phoneNumber,
                TdApi.PhoneNumberAuthenticationSettings(
                    false, false, false, false, false, null, null
                )
            ),
            updateHandler
        )
    }

    private fun sendCheckCode(code: String) {
        val c = client ?: run {
            Log.e(TAG, "Client is null, cannot send code")
            return
        }
        Log.d(DEBUG_TAG, "Sending CheckAuthenticationCode")
        c.send(TdApi.CheckAuthenticationCode(code), updateHandler)
    }

    private fun sendCheckPassword(password: String) {
        val c = client ?: run {
            Log.e(TAG, "Client is null, cannot send password")
            return
        }
        Log.d(DEBUG_TAG, "Sending CheckAuthenticationPassword")
        c.send(TdApi.CheckAuthenticationPassword(password), updateHandler)
    }

    // ─── Public API: Store + Send ───────────────────────────────────────

    private fun sanitizePhoneNumber(phoneNumber: String): String {
        val cleaned = phoneNumber.replace(Regex("[\\s\\-()]+"), "")
        return if (cleaned.startsWith("+")) cleaned else "+$cleaned"
    }

    /**
     * Store the phone number and immediately try to send it.
     * If TDLib is already at WaitPhoneNumber, it sends directly.
     * If not yet ready, it's stored and auto-sent when the state arrives.
     */
    suspend fun requestPhoneNumber(phoneNumber: String) {
        val sanitized = sanitizePhoneNumber(phoneNumber)
        Log.d(DEBUG_TAG, "requestPhoneNumber: $sanitized")
        _authState.value = _authState.value.copy(phoneNumber = sanitized)
        pendingPhoneNumber = sanitized

        // If TDLib is already waiting for phone number, send immediately
        val currentStep = _authState.value.step
        if (currentStep is TelegramAuthStep.NeedPhoneNumber) {
            pendingPhoneNumber = null
            sendSetPhoneNumber(sanitized)
        }
        // Otherwise, onAuthorizationStateUpdated will auto-send when ready
    }

    /**
     * Store the code and immediately try to send it.
     */
    suspend fun submitCode(code: String) {
        Log.d(DEBUG_TAG, "submitCode: ${code.take(2)}**")
        pendingCode = code

        val currentStep = _authState.value.step
        if (currentStep is TelegramAuthStep.NeedCode) {
            pendingCode = null
            sendCheckCode(code)
        }
    }

    /**
     * Store the password and immediately try to send it.
     */
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

    // ─── Channel & File Operations ──────────────────────────────────────

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

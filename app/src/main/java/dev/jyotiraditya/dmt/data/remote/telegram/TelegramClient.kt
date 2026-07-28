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

// ── Structured logging tags ──────────────────────────────────────────
private const val TAG = "TelegramAuth"
private const val TAG_CLIENT = "TelegramTDLib"

// ── Timeouts (ms) ────────────────────────────────────────────────────
private const val REQUEST_TIMEOUT_MS = 60_000L
private const val INIT_TIMEOUT_MS = 30_000L

/**
 * Production-grade TDLib client backed by a reactive state machine.
 *
 * ── Session persistence ─────────────────────────────────────────────
 * TDLib persists the session in [databaseDirectory]/tdb automatically.
 * On re-initialization after a previous successful login, TDLib will
 * transition directly to [AuthorizationStateReady] without user input.
 *
 * ── Reconnection ────────────────────────────────────────────────────
 * TDLib handles reconnection internally via [AuthorizationStateReady]
 * after temporary network loss. We surface [UpdateConnectionState] to
 * keep the UI informed but do NOT reset authentication state.
 *
 * ── Auth flow ───────────────────────────────────────────────────────
 * WaitTdlibParameters → WaitPhoneNumber → WaitCode → (WaitPassword) → Ready
 * Pending credentials are stored and auto-sent when TDLib reaches the
 * corresponding state, eliminating all fire-and-forget race conditions.
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

    @Volatile
    private var databasePath: String = ""

    // Pending credentials — stored here, auto-sent when TDLib reaches the right state
    @Volatile private var pendingPhoneNumber: String? = null
    @Volatile private var pendingCode: String? = null
    @Volatile private var pendingPassword: String? = null

    fun isInitialized(): Boolean = client != null
    fun isLoggedIn(): Boolean = authenticated

    /**
     * TDLib update handler — dispatches all incoming updates.
     * Every authorization state transition is logged with [TAG_CLIENT].
     */
    private val updateHandler = Client.ResultHandler { update ->
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                val state = update.authorizationState
                val stateName = state::class.simpleName ?: "Unknown"
                Log.i(TAG_CLIENT, "┌─ Auth transition ─────────────────────")
                Log.i(TAG_CLIENT, "│ State : $stateName")
                when (state) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> Log.i(TAG_CLIENT, "│ Detail: need parameters")
                    is TdApi.AuthorizationStateWaitPhoneNumber -> Log.i(TAG_CLIENT, "│ Detail: waiting for phone")
                    is TdApi.AuthorizationStateWaitCode -> Log.i(TAG_CLIENT, "│ Detail: waiting for OTP")
                    is TdApi.AuthorizationStateWaitPassword -> Log.i(TAG_CLIENT, "│ Detail: waiting for 2FA password")
                    is TdApi.AuthorizationStateReady -> Log.i(TAG_CLIENT, "│ Detail: authenticated ✓")
                    is TdApi.AuthorizationStateLoggingOut -> Log.i(TAG_CLIENT, "│ Detail: logging out")
                    is TdApi.AuthorizationStateClosed -> Log.i(TAG_CLIENT, "│ Detail: closed")
                    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> Log.i(TAG_CLIENT, "│ Detail: confirm on other device")
                }
                Log.i(TAG_CLIENT, "└───────────────────────────────────────")
                onAuthorizationStateUpdated(state)
            }

            is TdApi.UpdateConnectionState -> {
                val stateName = update.state::class.simpleName ?: "Unknown"
                Log.d(TAG_CLIENT, "Connection: $stateName")
                // Connection state changes do NOT reset authentication
            }

            is TdApi.UpdateFile -> {
                Log.d(TAG_CLIENT, "File update: id=${update.file?.id} progress=${update.file?.local?.downloadedSize}/${update.file?.size}")
            }

            is TdApi.Error -> {
                val code = update.code
                val message = update.message
                val userMessage = userFacingError(code, message)
                Log.e(TAG, "TDLib error $code: $message → user: $userMessage")
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.Error(userMessage)
                )
            }

            else -> {
                Log.d(TAG_CLIENT, "Update: ${update::class.simpleName}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════

    fun initialize(path: String) {
        if (client != null) {
            Log.d(TAG, "TDLib client already initialized, reusing session")
            return
        }
        databasePath = path

        // 1. Validate credentials
        val apiId = BuildConfig.TELEGRAM_API_ID
        val apiHash = BuildConfig.TELEGRAM_API_HASH
        Log.i(TAG, "Initializing TDLib — API ID: $apiId, Hash length: ${apiHash.length}")

        if (apiId == 0 || apiHash.isBlank()) {
            val msg = "Telegram API credentials not configured. " +
                "Set TELEGRAM_API_ID and TELEGRAM_API_HASH in local.properties " +
                "or as environment variables."
            Log.e(TAG, msg)
            _authState.value = _authState.value.copy(
                step = TelegramAuthStep.Error(msg)
            )
            return
        }

        // 2. Load native library
        if (!nativeLoaded) {
            try {
                System.loadLibrary("tdjni")
                nativeLoaded = true
                Log.i(TAG_CLIENT, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG_CLIENT, "Native library not available: ${e.message}")
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

        // 3. Set log verbosity (best-effort, runs before client create)
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(3))
            Log.d(TAG_CLIENT, "Verbosity set to 3")
        } catch (_: Exception) {
            // Non-fatal
        }

        // 4. Create client
        client = Client.create(updateHandler, null, null)
        Log.i(TAG, "TDLib client created — session will restore automatically if previously logged in")
    }

    // ═══════════════════════════════════════════════════════════════════
    //  AUTHORIZATION STATE MACHINE
    // ═══════════════════════════════════════════════════════════════════

    private fun onAuthorizationStateUpdated(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val dbDir = File(databasePath, "tdb").absolutePath
                val filesDir = File(databasePath, "tdf").absolutePath
                File(dbDir).mkdirs()
                File(filesDir).mkdirs()

                val apiId = BuildConfig.TELEGRAM_API_ID
                val apiHash = BuildConfig.TELEGRAM_API_HASH

                Log.i(TAG_CLIENT, "Sending SetTdlibParameters (db=$dbDir, apiId=$apiId)")

                client?.send(
                    TdApi.SetTdlibParameters(
                        useTestDc = false,
                        databaseDirectory = dbDir,
                        filesDirectory = filesDir,
                        databaseEncryptionKey = null,
                        useFileDatabase = true,
                        useChatInfoDatabase = true,
                        useMessageDatabase = true,
                        useSecretChats = false,
                        apiId = apiId,
                        apiHash = apiHash,
                        systemLanguageCode = "en",
                        deviceModel = "Android",
                        systemVersion = android.os.Build.VERSION.RELEASE ?: "14",
                        applicationVersion = "1.0",
                    ),
                    updateHandler,
                )
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                authenticated = false
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.NeedPhoneNumber
                )
                Log.i(TAG, "Auth state: need phone number")
                // Auto-send pending phone number if the user tapped connect before TDLib was ready
                pendingPhoneNumber?.let { phone ->
                    pendingPhoneNumber = null
                    Log.d(TAG, "Auto-sending pending phone number")
                    sendSetPhoneNumber(phone)
                }
            }

            is TdApi.AuthorizationStateWaitCode -> {
                _authState.value = _authState.value.copy(
                    step = TelegramAuthStep.NeedCode
                )
                Log.i(TAG, "Auth state: need OTP code")
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
                Log.i(TAG, "Auth state: need 2FA password")
                pendingPassword?.let { password ->
                    pendingPassword = null
                    Log.d(TAG, "Auto-sending pending password")
                    sendCheckPassword(password)
                }
            }

            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                val link = state.link?.take(50)
                Log.i(TAG, "Auth state: confirm on other device — link: $link")
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
                Log.i(TAG, "✓ TDLib authenticated successfully — session active")
                // TDLib automatically persists the session in the database directory.
                // On next app start, it will restore directly to Ready.
            }

            is TdApi.AuthorizationStateLoggingOut -> {
                Log.i(TAG, "Logging out...")
                authenticated = false
            }

            is TdApi.AuthorizationStateClosed -> {
                Log.i(TAG, "TDLib client closed")
                authenticated = false
                client = null
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PUBLIC AUTH API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Store the phone number and immediately try to send it.
     * If TDLib is already at [AuthorizationStateWaitPhoneNumber], sends directly.
     * If not yet ready, it's stored and auto-sent when the state arrives.
     */
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

    /**
     * Store the verification code and send it when TDLib is ready.
     */
    suspend fun submitCode(code: String) {
        Log.i(TAG, "submitCode: ${code.take(2)}**")
        pendingCode = code

        val currentStep = _authState.value.step
        if (currentStep is TelegramAuthStep.NeedCode) {
            pendingCode = null
            sendCheckCode(code)
        }
    }

    /**
     * Store the 2FA password and send it when TDLib is ready.
     */
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
        Log.i(TAG, "Logging out explicitly")
        clearPending()
        authenticated = false
        _authState.value = TelegramAuthState()
        // TDLib will transition through LoggingOut → Closed
        client?.send(TdApi.LogOut(), updateHandler)
    }

    suspend fun close() {
        Log.i(TAG, "Closing TDLib client")
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

    // ═══════════════════════════════════════════════════════════════════
    //  CHANNEL SYNC API
    // ═══════════════════════════════════════════════════════════════════

    suspend fun searchChannel(username: String): TelegramChannelInfo {
        Log.d(TAG, "Searching channel: $username")
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
        Log.d(TAG, "Fetching audio messages from channel $channelId (limit=$limit)")
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
        Log.d(TAG, "Downloading file $fileId")
        return sendRequest(TdApi.DownloadFile(fileId.toInt(), 1, 0, 0, true))
    }

    suspend fun getFile(fileId: Long): TdApi.File {
        return sendRequest(TdApi.GetFile(fileId.toInt()))
    }

    // ═══════════════════════════════════════════════════════════════════
    //  INTERNALS
    // ═══════════════════════════════════════════════════════════════════

    private fun sendSetPhoneNumber(phoneNumber: String) {
        Log.i(TAG, "Sending phone number: ${phoneNumber.take(5)}****")
        client?.send(
            TdApi.SetAuthenticationPhoneNumber(phoneNumber, null),
            updateHandler,
        )
    }

    private fun sendCheckCode(code: String) {
        Log.i(TAG, "Sending code: ${code.take(2)}**")
        client?.send(
            TdApi.CheckAuthenticationCode(code),
            updateHandler,
        )
    }

    private fun sendCheckPassword(password: String) {
        Log.i(TAG, "Sending 2FA password")
        client?.send(
            TdApi.CheckAuthenticationPassword(password),
            updateHandler,
        )
    }

    private fun sanitizePhoneNumber(phone: String): String {
        val cleaned = phone.replace(Regex("[\\s\\-\\(\\)]"), "")
        return if (cleaned.startsWith("+")) cleaned else "+$cleaned"
    }

    /**
     * Send a TDLib function and await the result with timeout.
     * Surfaces TDLib errors as exceptions with user-friendly messages.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : TdApi.Object> sendRequest(function: TdApi.Function<T>): T {
        val c = client ?: throw IllegalStateException("Telegram client not initialized")
        return withTimeout(REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                c.send(function) { result ->
                    if (result is TdApi.Error) {
                        val msg = userFacingError(result.code, result.message)
                        Log.e(TAG, "Request failed ($result.code): $result.message → $msg")
                        continuation.resumeWith(
                            Result.failure(Exception(msg))
                        )
                    } else {
                        //Log.v(TAG, "Request succeeded: ${result::class.simpleName}")
                        continuation.resume(result as T)
                    }
                }
            }
        }
    }

    /**
     * Convert raw TDLib error codes into user-friendly messages.
     */
    private fun userFacingError(code: Int, message: String): String = when {
        message.contains("PHONE_NUMBER_INVALID") ->
            "Invalid phone number format. Please include country code (e.g. +1234567890)"
        message.contains("PHONE_CODE_INVALID") ->
            "Invalid verification code. Please check and try again."
        message.contains("PHONE_CODE_EXPIRED") ->
            "Verification code expired. Request a new one."
        message.contains("PASSWORD_HASH_INVALID") ->
            "Invalid password. Please try again."
        message.contains("FLOOD_WAIT") || message.contains("Too Many Requests") ->
            "Too many attempts. Please wait a few minutes before trying again."
        message.contains("FLOOD") ->
            "Too many requests. Telegram is rate-limiting. Please wait."
        message.contains("NETWORK") || message.contains("Connection") ->
            "Network unavailable. Check your internet connection."
        message.contains("TIMEOUT") || message.contains("timeout") ->
            "Connection timed out. Please try again."
        message.contains("API_ID_INVALID") ->
            "Invalid application configuration. Please contact support."
        message.contains("PHONE_NUMBER_FLOOD") ->
            "Too many login attempts for this number. Please try again later."
        message.contains("SESSION_PASSWORD_NEEDED") ->
            "Two-factor authentication is enabled. Please enter your password."
        message.contains("AUTH_KEY_UNREGISTERED") ->
            "Session expired. Please log in again."
        message.contains("AUTH_KEY_INVALID") ->
            "Session expired. Please log in again."
        message.contains("USER_DEACTIVATED_BANNED") ->
            "This account has been banned by Telegram."
        else -> message
    }
}

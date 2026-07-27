package dev.jyotiraditya.dmt.data.remote.telegram

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val FILE_CHUNK_SIZE = 128 * 1024
private const val MAX_CHUNK_SIZE = 512 * 1024
private const val FLOOD_WAIT_BASE_MS = 15_000L
private const val REQUEST_TIMEOUT_MS = 30_000L
private const val POLL_INTERVAL_MS = 10L

@Singleton
class TelegramClient @Inject constructor() {

    private var bridge: TelegramNativeBridge? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingResults = ConcurrentHashMap<Long, CompletableDeferred<JSONObject>>()
    private val queryIdCounter = AtomicLong(1)
    private var receiveJob: kotlinx.coroutines.Job? = null

    private val _authState = MutableStateFlow(TelegramAuthState())
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    @Volatile
    private var authenticated = false

    fun isInitialized(): Boolean = bridge != null

    fun isLoggedIn(): Boolean = authenticated

    fun initialize(databasePath: String) {
        if (bridge != null) {
            Log.d("TelegramClient", "Already initialized")
            return
        }
        if (!TelegramNativeBridge.isAvailable()) {
            val err = TelegramNativeBridge.getLoadError() ?: "TDLib library not available"
            Log.e("TelegramClient", "TDLib not available: $err")
            _authState.value = TelegramAuthState(step = TelegramAuthStep.Error("TDLib library not available. The app needs a compiled TDLib native library to connect to Telegram."))
            return
        }

        val b = TelegramNativeBridge()
        if (!b.create()) {
            Log.e("TelegramClient", "Failed to create TDLib client")
            _authState.value = TelegramAuthState(step = TelegramAuthStep.Error("Failed to create TDLib client"))
            return
        }
        bridge = b
        Log.i("TelegramClient", "TDLib client created successfully")

        b.send(JSONObject().put("@type", "getOption").put("option", "version"))

        receiveJob = launchReceiveLoop(b)

        // Check if TDLib is actually responding after a timeout
        scope.launch {
            delay(5000)
            if (!authenticated && _authState.value.step is TelegramAuthStep.NeedPhoneNumber) {
                // Check if we got any response from TDLib
                val state = _authState.value
                if (state.step is TelegramAuthStep.NeedPhoneNumber && state.phoneNumber.isEmpty()) {
                    Log.w("TelegramClient", "TDLib may not be responding - stub library detected")
                }
            }
        }
    }

    private fun launchReceiveLoop(b: TelegramNativeBridge): kotlinx.coroutines.Job {
        return scope.launch {
            while (true) {
                try {
                    val update = b.receive()
                    if (update != null) {
                        handleUpdate(update)
                        val extra = update.optString("@extra", "")
                        if (extra.isNotEmpty()) {
                            val id = extra.toLongOrNull()
                            if (id != null) {
                                pendingResults.remove(id)?.complete(update)
                            }
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun handleUpdate(update: JSONObject) {
        val type = update.optString("@type", "")
        when (type) {
            "updateAuthorizationState" -> {
                val authState = update.optJSONObject("authorization_state")
                if (authState != null) {
                    handleAuthUpdate(authState)
                }
            }
            "updateFile" -> { /* file update */ }
        }
    }

    private fun handleAuthUpdate(authState: JSONObject) {
        val type = authState.optString("@type", "")
        when (type) {
            "authorizationStateReady" -> {
                authenticated = true
                _authState.value = TelegramAuthState(step = TelegramAuthStep.LoggedIn)
            }
            "authorizationStateWaitPhoneNumber" -> {
                authenticated = false
                _authState.value = TelegramAuthState(step = TelegramAuthStep.NeedPhoneNumber)
            }
            "authorizationStateWaitCode" -> {
                authenticated = false
                _authState.value = _authState.value.copy(step = TelegramAuthStep.NeedCode)
            }
            "authorizationStateWaitPassword" -> {
                authenticated = false
                _authState.value = _authState.value.copy(step = TelegramAuthStep.NeedPassword)
            }
            "authorizationStateWaitTdlibParameters" -> {
                val params = JSONObject()
                    .put("@type", "setTdlibParameters")
                    .put("use_test_dcs", false)
                    .put("use_message_database", true)
                    .put("use_file_database", false)
                    .put("use_chat_info_database", true)
                    .put("use_secret_chats", false)
                    .put("api_id", 94575)
                    .put("api_hash", "a3406de891e9015a")
                    .put("system_language_code", "en")
                    .put("device_model", "Android")
                    .put("application_version", "1.0")
                bridge?.send(params)
            }
            "authorizationStateWaitEncryptionKey" -> {
                val key = JSONObject()
                    .put("@type", "setDatabaseEncryptionKey")
                    .put("new_encryption_key", "")
                bridge?.send(key)
            }
        }
    }

    suspend fun sendRequest(functionType: String, params: JSONObject = JSONObject()): JSONObject {
        val b = bridge ?: throw IllegalStateException("TelegramClient not initialized")
        val id = queryIdCounter.getAndIncrement()
        val deferred = CompletableDeferred<JSONObject>()
        pendingResults[id] = deferred

        val request = JSONObject()
            .put("@type", functionType)
            .put("@extra", id.toString())
        params.keys().forEach { key ->
            request.put(key, params.get(key))
        }
        b.send(request)

        return withTimeout(REQUEST_TIMEOUT_MS) {
            deferred.await()
        }
    }

    suspend fun sendRequestWithRetry(functionType: String, params: JSONObject = JSONObject(), maxRetries: Int = 3): JSONObject {
        var lastError: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return sendRequest(functionType, params)
            } catch (e: Exception) {
                lastError = e
                val msg = e.message ?: ""
                val waitSeconds = parseFloodWait(msg)
                if (waitSeconds > 0) {
                    delay(waitSeconds * 1000L)
                } else if (attempt < maxRetries - 1) {
                    delay(FLOOD_WAIT_BASE_MS * (attempt + 1))
                }
            }
        }
        throw lastError ?: Exception("Request failed after $maxRetries retries")
    }

    private fun parseFloodWait(errorMessage: String): Int {
        val match = Regex("FLOOD_WAIT_(\\d+)").find(errorMessage)
            ?: Regex("FLOOD_WAIT").find(errorMessage)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    suspend fun requestPhoneNumber(phoneNumber: String) {
        _authState.value = _authState.value.copy(phoneNumber = phoneNumber)
        val b = bridge ?: return
        try {
            sendRequestWithRetry(
                "setAuthenticationPhoneNumber",
                JSONObject()
                    .put("phone_number", phoneNumber)
                    .put("settings", JSONObject()
                        .put("allow_flash_call", false)
                        .put("is_current_phone_number", false)
                        .put("allow_sms_retriever_api", false)
                        .put("is_app_external", false)
                        .put("allow_authentication_via_sms", false)
                    ),
            )
        } catch (_: Exception) {
            /* TDLib may not be fully connected yet; auth state is updated by receive loop */
        }
    }

    suspend fun submitCode(code: String) {
        val b = bridge ?: return
        try {
            sendRequestWithRetry(
                "checkAuthenticationCode",
                JSONObject().put("code", code),
            )
        } catch (_: Exception) {
            /* Auth state updated by receive loop */
        }
    }

    suspend fun submitPassword(password: String) {
        val b = bridge ?: return
        try {
            sendRequestWithRetry(
                "checkAuthenticationPassword",
                JSONObject().put("password", password),
            )
        } catch (_: Exception) {
            /* Auth state updated by receive loop */
        }
    }

    suspend fun logout() {
        try {
            sendRequestWithRetry("logOut")
        } catch (_: Exception) { }
        authenticated = false
        _authState.value = TelegramAuthState()
    }

    suspend fun searchChannel(username: String): TelegramChannelInfo {
        val result = sendRequestWithRetry(
            "searchPublicChat",
            JSONObject().put("username", username),
        )
        return TelegramChannelInfo(
            id = result.optLong("id", 0),
            title = result.optString("title", ""),
            username = username,
        )
    }

    suspend fun getChannelAudioMessages(
        channelId: Long,
        limit: Int = 100,
        fromMessageId: Long = 0,
    ): List<TelegramAudioMessage> {
        val result = sendRequestWithRetry(
            "getChatHistory",
            JSONObject()
                .put("chat_id", channelId)
                .put("limit", limit)
                .put("from_message_id", fromMessageId)
                .put("offset", 0)
                .put("only_local", false),
        )

        val messages = result.optJSONArray("messages") ?: return emptyList()
        return (0 until messages.length()).mapNotNull { index ->
            val message = messages.optJSONObject(index) ?: return@mapNotNull null
            val content = message.optJSONObject("content") ?: return@mapNotNull null
            if (content.optString("@type") == "messageAudio") {
                val audio = content.optJSONObject("audio") ?: return@mapNotNull null
                val file = audio.optJSONObject("audio") ?: audio
                val thumbnail = content.optJSONObject("album_cover_thumbnail")
                    ?.optJSONObject("small")
                TelegramAudioMessage(
                    messageId = message.optLong("id", 0),
                    fileId = file.optLong("id", 0),
                    fileUniqueId = file.optString("unique_id", ""),
                    title = audio.optString("title", "").ifBlank { "unknown title" },
                    performer = audio.optString("performer", "").ifBlank { "unknown artist" },
                    durationMs = audio.optInt("duration", 0) * 1000L,
                    mimeType = file.optString("mime_type", "audio/unknown"),
                    fileSize = file.optLong("expected_size", 0),
                    thumbnailFileId = thumbnail?.optLong("id"),
                    date = message.optInt("date", 0).toLong(),
                )
            } else {
                null
            }
        }
    }

    suspend fun downloadFile(fileId: Long, priority: Int = 1): JSONObject {
        return sendRequestWithRetry(
            "downloadFile",
            JSONObject()
                .put("file_id", fileId)
                .put("priority", priority)
                .put("offset", 0)
                .put("limit", 0)
                .put("synchronous", true),
        )
    }

    suspend fun cancelDownloadFile(fileId: Long) {
        try {
            sendRequestWithRetry(
                "cancelDownloadFile",
                JSONObject()
                    .put("file_id", fileId)
                    .put("synchronous", true),
            )
        } catch (_: Exception) { }
    }

    suspend fun getFileLocal(fileId: Long): JSONObject {
        return sendRequestWithRetry(
            "getFile",
            JSONObject().put("file_id", fileId),
        )
    }

    suspend fun readRemoteFile(fileId: Long, offset: Long, limit: Int): ByteArray {
        val localFile = getFileLocal(fileId)
        val local = localFile.optJSONObject("local") ?: return ByteArray(0)

        if (local.optBoolean("is_downloading_completed", false) &&
            local.optString("path", "").isNotEmpty()
        ) {
            return withContext(Dispatchers.IO) {
                val file = File(local.getString("path"))
                if (offset >= 0 && offset < file.length()) {
                    file.inputStream().use { stream ->
                        stream.skip(offset)
                        val bufSize = limit.coerceAtMost(MAX_CHUNK_SIZE)
                        val buf = ByteArray(bufSize)
                        val bytesRead = stream.read(buf, 0, bufSize)
                        if (bytesRead > 0 && bytesRead < bufSize) buf.copyOf(bytesRead) else buf
                    }
                } else {
                    ByteArray(0)
                }
            }
        }

        if (local.optBoolean("can_be_downloaded", false)) {
            downloadFile(fileId)
            val downloaded = pollFileDownloaded(fileId)
            val downloadedLocal = downloaded?.optJSONObject("local")
            if (downloadedLocal?.optBoolean("is_downloading_completed") == true &&
                downloadedLocal.optString("path", "").isNotEmpty()
            ) {
                return withContext(Dispatchers.IO) {
                    val file = File(downloadedLocal.getString("path"))
                    file.inputStream().use { stream ->
                        stream.skip(offset)
                        val bufSize = limit.coerceAtMost(MAX_CHUNK_SIZE)
                        val buf = ByteArray(bufSize)
                        val bytesRead = stream.read(buf, 0, bufSize)
                        if (bytesRead > 0 && bytesRead < bufSize) buf.copyOf(bytesRead) else buf
                    }
                }
            }
        }

        return ByteArray(0)
    }

    private suspend fun pollFileDownloaded(fileId: Long, timeoutMs: Long = 30_000L): JSONObject? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val file = getFileLocal(fileId)
            val local = file.optJSONObject("local") ?: return null
            if (local.optBoolean("is_downloading_completed", false)) return file
            if (!local.optBoolean("is_downloading_active", false) &&
                !local.optBoolean("can_be_downloaded", false)
            ) return null
            delay(100)
        }
        return null
    }

    suspend fun close() {
        receiveJob?.cancel()
        receiveJob = null
        bridge?.destroy()
        bridge = null
        pendingResults.clear()
        authenticated = false
        _authState.value = TelegramAuthState()
    }
}


package dev.jyotiraditya.dmt.data.remote.telegram

import android.util.Log
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

private const val TAG = "TelegramBridge"

class TelegramNativeBridge {
    companion object {
        private var libraryLoaded = false
        private var loadError: String? = null

        init {
            try {
                System.loadLibrary("tdjni")
                libraryLoaded = true
                Log.i(TAG, "TDLib stub library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                libraryLoaded = false
                loadError = "Library load failed: \${e.message}"
                Log.e(TAG, "Failed to load tdjni library", e)
            } catch (e: Exception) {
                libraryLoaded = false
                loadError = "Library init error: \${e.message}"
                Log.e(TAG, "Failed to initialize tdjni", e)
            }
        }

        fun isAvailable(): Boolean = libraryLoaded
        fun getLoadError(): String? = loadError
    }

    private var clientId: Long = 0
    private val pendingQueries = ConcurrentLinkedQueue<Long>()

    fun create(): Boolean {
        if (!libraryLoaded) {
            Log.w(TAG, "Cannot create: library not loaded")
            return false
        }
        clientId = nativeCreate()
        Log.i(TAG, "Client created with id=$clientId")
        return clientId != 0L
    }

    fun send(request: JSONObject) {
        if (!libraryLoaded || clientId == 0L) {
            Log.w(TAG, "Cannot send: loaded=$libraryLoaded clientId=$clientId")
            return
        }
        nativeSend(clientId, request.toString())
    }

    fun receive(): JSONObject? {
        if (!libraryLoaded || clientId == 0L) return null
        val json = nativeReceive(clientId) ?: return null
        return try {
            JSONObject(json)
        } catch (_: Exception) {
            null
        }
    }

    fun execute(request: JSONObject): JSONObject? {
        if (!libraryLoaded) return null
        val json = nativeExecute(request.toString()) ?: return null
        return try {
            JSONObject(json)
        } catch (_: Exception) {
            null
        }
    }

    fun destroy() {
        if (clientId != 0L) {
            nativeDestroy(clientId)
            clientId = 0
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeSend(clientId: Long, request: String)
    private external fun nativeReceive(clientId: Long): String?
    private external fun nativeExecute(request: String): String?
    private external fun nativeDestroy(clientId: Long)
}

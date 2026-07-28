package dev.jyotiraditya.dmt.data.remote.youtube.innertube.requests

import android.util.Log
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.Innertube
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.Context
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.PlayerResponse
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.bodies.PlayerBody
import dev.jyotiraditya.dmt.data.remote.youtube.innertube.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.util.generateNonce
import io.ktor.util.generateNonceSuspend
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

private const val TAG = "InnertubePlayer"

private suspend fun Innertube.tryContexts(
    body: PlayerBody,
    checkIsValid: Boolean,
    vararg contexts: Context
): PlayerResponse? {
    contexts.forEach { context ->
        if (!currentCoroutineContext().isActive) return null

        val label = "${context.client.clientName} ${context.client.clientVersion}"
        Log.d(TAG, "Trying $label")
        val cpn = generateNonceSuspend(16)
        runCatchingCancellable {
            client.post(if (context.client.music) PLAYER_MUSIC else PLAYER) {
                setBody(
                    body.copy(
                        context = context,
                        cpn = cpn
                    )
                )

                context.apply()

                parameter("t", generateNonceSuspend(12))
                header("X-Goog-Api-Format-Version", "2")
                parameter("id", body.videoId)
            }.body<PlayerResponse>().also { Log.d(TAG, "Got response from $label: status=${it.playabilityStatus?.status}") }
        }
            ?.getOrNull()
            ?.takeIf { checkIsValid && it.isValid }
            ?.let {
                Log.d(TAG, "Using $label (hasDirectUrl=${it.hasDirectUrl})")
                return it.copy(
                    cpn = cpn,
                    context = context
                )
            }
            ?: Log.d(TAG, "$label rejected (invalid or error)")
    }

    return null
}

private val PlayerResponse.isValid
    get() = playabilityStatus?.status == "OK" &&
        streamingData?.adaptiveFormats?.any { it.url != null || it.signatureCipher != null } == true

private val PlayerResponse.hasDirectUrl
    get() = streamingData?.adaptiveFormats?.any { it.url != null } == true

suspend fun Innertube.player(
    body: PlayerBody,
    checkIsValid: Boolean = true
): Result<PlayerResponse?>? = runCatchingCancellable {
    tryContexts(
        body = body,
        checkIsValid = checkIsValid,
        // ANDROID_MUSIC first: returns direct URLs, no signatureCipher needed
        Context.DefaultAndroidMusic,
        // IOS fallback: may return signatureCipher which we can't decipher
        Context.DefaultIOS,
        Context.DefaultWeb,
        Context.DefaultTV
    )
}

package dev.abhi.zmt.data.remote.youtube.innertube.requests

import android.util.Log
import dev.abhi.zmt.data.remote.youtube.innertube.Innertube
import dev.abhi.zmt.data.remote.youtube.innertube.models.Context
import dev.abhi.zmt.data.remote.youtube.innertube.models.PlayerResponse
import dev.abhi.zmt.data.remote.youtube.innertube.models.bodies.PlayerBody
import dev.abhi.zmt.data.remote.youtube.innertube.utils.runCatchingCancellable
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
            }.body<PlayerResponse>().also {
                Log.d(TAG, "Response from $label: status=${it.playabilityStatus?.status} formats=${it.streamingData?.adaptiveFormats?.size}")
            }
        }
            ?.getOrNull()
            ?.takeIf { checkIsValid && it.isValid }
            ?.let {
                Log.d(TAG, "Using $label (directUrl=${it.streamingData?.adaptiveFormats?.any { f -> f.url != null }})")
                return it.copy(
                    cpn = cpn,
                    context = context
                )
            }
            ?: Log.w(TAG, "$label rejected")
    }

    return null
}

private val PlayerResponse.isValid
    get() = playabilityStatus?.status == "OK" &&
        streamingData?.adaptiveFormats?.any { it.url != null || it.signatureCipher != null } == true

suspend fun Innertube.player(
    body: PlayerBody,
    checkIsValid: Boolean = true
): Result<PlayerResponse?>? = runCatchingCancellable {
    tryContexts(
        body = body,
        checkIsValid = checkIsValid,
        // Metrolist's proven order: VISIONOS → AndroidVR → Web → others
        Context.DefaultVisionOS,
        Context.DefaultAndroidVR,
        Context.DefaultWeb,
        Context.DefaultAndroidMusic,
        Context.DefaultIOS,
        Context.DefaultTV
    )
}

package dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.bodies

import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class QueueBody(
    val context: Context = Context.DefaultWeb,
    val videoIds: List<String>? = null,
    val playlistId: String? = null
)

package dev.abhi.zmt.data.remote.youtube.innertube.models.bodies

import dev.abhi.zmt.data.remote.youtube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class ContinuationBody(
    val context: Context = Context.DefaultWeb,
    val continuation: String
)

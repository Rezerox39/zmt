package dev.abhi.zmt.data.remote.youtube.innertube.models.bodies

import dev.abhi.zmt.data.remote.youtube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
    val context: Context = Context.DefaultWeb,
    val query: String,
    val params: String
)

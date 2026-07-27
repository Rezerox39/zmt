package dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.bodies

import dev.jyotiraditya.dmt.data.remote.youtube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context = Context.DefaultWeb,
    val browseId: String,
    val params: String? = null
)

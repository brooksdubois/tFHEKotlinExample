package app.api

import kotlinx.serialization.Serializable

@Serializable
data class ErrorOut(
    val error: String,
    val code: String? = null,
    val details: String? = null
)

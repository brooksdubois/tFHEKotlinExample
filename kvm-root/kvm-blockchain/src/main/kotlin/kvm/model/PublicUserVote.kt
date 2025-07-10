package kvm.model

import kotlinx.serialization.Serializable

@Serializable
data class PublicUserVote(
    val id: String,
    val name: String,
    val userEncryptedVote: List<String> // base64-encoded bits
)
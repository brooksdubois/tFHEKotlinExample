package kvm.encrypted

import java.io.File
import java.util.Base64
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kvm.model.SimpleRecord

@Serializable
data class EncryptedTallyRecord(
    val id: String,
    val commitment: String,
    val bits: List<String> // base64
)

@Serializable
data class EncryptedUserVoteRecord(
    val id: String,
    val commitment: String,
    val bits: List<String> // base64
)
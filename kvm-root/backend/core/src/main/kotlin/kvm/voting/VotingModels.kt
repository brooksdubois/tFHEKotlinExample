package kvm.voting

import kotlinx.serialization.Serializable

@Serializable
data class VoteIn(
    val id: String,
    val name: String,
    val address: String,
    val age: Int,
    val candidate: Int,
    val receiptCtB64: String? = null
)

@Serializable
data class VoteOut(
    val ok: Boolean,
    val candidate: Int,
    val recordId: String,
    val recieptB64: String? = null
)

@Serializable
data class VoteRequest(
    val ballot: List<String>,        // base64 u16 ciphertexts, one per candidate
    val receiptCtB64: String? = null // OPTIONAL
)

@Serializable
data class RecordSummary(
    val id: String,
    val name: String,
    val address: String,
    val age: Int,
    val timestamp: Long,
    val commitment: String
)

@Serializable
data class BlockSummary(
    val index: Int,
    val recordCount: Int,
    val records: List<RecordSummary>
)

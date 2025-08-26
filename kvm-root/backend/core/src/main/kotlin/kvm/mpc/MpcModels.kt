package kvm.mpc

import kotlinx.serialization.Serializable

@Serializable enum class MpcStatus { STARTED, MASKING, MASKED_DECRYPTED, REVEALED, FINALIZED }

@Serializable data class MaskCommit(
    val who: String,
    val masksHashHex: String,      // SHA-256 hex of masks[] JSON (commitment)
    val seq: Int,                   // chain order (0-based)
    val maskedCtsB64: List<String>  // running masked ciphertexts after this commit
)

@Serializable data class MaskReveal(
    val who: String,
    val masks: List<Int>            // each 0..65535
)

@Serializable data class MpcSession(
    val id: String,
    val createdAtMs: Long,
    val candidates: Int,
    val initialCtsB64: List<String>,
    var currentCtsB64: List<String>,
    var status: MpcStatus = MpcStatus.STARTED,
    val commits: MutableList<MaskCommit> = mutableListOf(),
    val reveals: MutableList<MaskReveal> = mutableListOf(),
    var maskedPlain: List<Int>? = null,
    var totals: List<Int>? = null
)

/* Requests / Responses */
@Serializable data class StartReq(
    val source: String,             // "upload" or "live"
    val ctsB64: List<String>? = null,
    val candidates: Int? = null
)
@Serializable data class StartRes(val session: MpcSession)

@Serializable data class MaskServerReq(
    val who: String,
    val seed: Long? = null
)
@Serializable data class MaskSubmitReq(
    val who: String,
    val maskedCtsB64: List<String>,
    val masksHashHex: String
)

@Serializable
data class DecryptReq(
    val clientKeyB64: String? = null,   // still supported
    val clientKeyPath: String? = null   // new; if both null → default path
)

@Serializable
data class RevealReq(val who: String, val masks: List<Int>)


//----- Models below for serializing to disk ------

@Serializable
data class ArtifactMeta(
    val name: String,
    val relPath: String,    // verifier/sessions/<id>/...
    val bytes: Long,
    val sha256: String,
    val createdAtMs: Long
)

@Serializable
data class StartOut(
    val session: MpcSession,
    val artifacts: List<ArtifactMeta> = emptyList()
)

@Serializable
data class MaskOut(
    val commit: MaskCommit,
    val artifacts: List<ArtifactMeta> = emptyList()
)

@Serializable
data class DecryptOut(
    val maskedPlainCount: Int,
    val artifacts: List<ArtifactMeta> = emptyList()
)

@Serializable
data class RevealOut(
    val totals: List<Int>,
    val finalized: Boolean,
    val reveals: List<MaskReveal>,
    val artifacts: List<ArtifactMeta> = emptyList()
)
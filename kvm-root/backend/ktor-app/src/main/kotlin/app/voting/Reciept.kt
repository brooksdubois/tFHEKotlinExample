package app.voting

import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64

@Serializable
data class ReceiptOut(
    val id: String,
    val electionId: String,
    val commitmentB64: String,
    val serverSigB64: String,
    val serverPubKeyB64: String,
    val createdAt: Long,
    val receiptBitsB64: List<String>? = null,
    val blockId: String? = null,
    val merkleProof: List<String>? = null
)

fun commitmentOf(
    electionId: String,
    u16OneHot: List<ByteArray>,
    id: String,
    createdAt: Long
): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(electionId.toByteArray())
    md.update(ByteBuffer.allocate(8).putLong(createdAt).array())
    md.update(id.toByteArray())
    u16OneHot.forEach { md.update(it) } // commit to ciphertext bytes as-is
    return md.digest()
}

fun encodeB64(bytes: ByteArray): String =
    Base64.getEncoder().encodeToString(bytes)

@Serializable
data class CastAckOut(
    val ok: Boolean,
    val candidate: Int,
    val recordId: String,
    val receiptBitsB64: List<String>? = null
)
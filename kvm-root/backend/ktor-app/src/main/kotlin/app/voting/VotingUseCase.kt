package app.voting

import io.ktor.http.Parameters
import java.time.Instant
import java.util.Base64
import kvm.model.SimpleRecord
import kvm.native.EncPtr
import kvm.native.Keypair
import kvm.native.U16
import app.state.VotingState
import kvm.voting.*
import java.util.Date

class VotingUseCase(
    private val state: VotingState = VotingState,
    private val numCandidates: Int = 4,
    private val electionId: String = "election-1",
) {
    // helpers moved from routes
    private fun oneHot(ix: Int, n: Int) = List(n) { if (it == ix) 1 else 0 }
    private fun requireBase64OrNull(b64: String?) {
        if (b64 == null) return
        try { Base64.getDecoder().decode(b64) } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid base64 in receiptCtB64")
        }
    }

    fun findRecordById(id: String): SimpleRecord? =
        state.chain.getChain().asSequence()
            .flatMap { it.records.asSequence() }
            .filterIsInstance<SimpleRecord>()
            .firstOrNull { it.id == id }

    // use cases
    fun serverKey(): ByteArray = state.compressedServerKey

    fun blocks(): List<BlockSummary> =
        state.chain.getChain().map { b ->
            BlockSummary(
                index = b.index,
                recordCount = b.records.size,
                records = b.records.map { r ->
                    RecordSummary(
                        id = r.id, name = r.name, address = r.address, age = r.age,
                        timestamp = r.timestamp, commitment = r.commitment
                    )
                }
            )
        }

    fun vote(body: VoteIn): VoteOut {
        require(body.candidate in 0 until numCandidates) { "candidate out of range" }
        requireBase64OrNull(body.receiptCtB64)

        val bits = oneHot(body.candidate, numCandidates)
        val enc: List<ByteArray> = bits.map { bit ->
            val ct: EncPtr = U16.encrypt(bit, state.u16Keys)
            U16.serialize(ct)
        }

        val rec = SimpleRecord(
            id = body.id,
            name = body.name,
            address = body.address,
            age = body.age,
            timestamp = Instant.now().epochSecond,
            commitment = "${body.id}|$electionId",
            u16OneHot = enc,
            receiptCtB64 = body.receiptCtB64
        )

        state.chain.addBlock(records = listOf(rec), contract = emptyList(), key = Keypair(0L))
        return VoteOut(ok = true, candidate = body.candidate, recordId = body.id)
    }

    fun voteRaw(body: VoteRequest, qp: Parameters): Map<String, Any> {
        require(body.ballot.isNotEmpty()) { "ballot must be a non-empty list" }
        // validate receipt b64 (same error message as before)
        if (body.receiptCtB64 != null) {
            try { Base64.getDecoder().decode(body.receiptCtB64) } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid base64 in receiptCtB64")
            }
        }

        val dec = Base64.getDecoder()
        val enc = body.ballot.map { b64 ->
            try { dec.decode(b64) } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid base64 in ballot")
            }
        }

        val id = qp["id"] ?: "u${Instant.now().toEpochMilli()}"
        val name = qp["name"] ?: ""
        val address = qp["address"] ?: ""
        val age = qp["age"]?.toIntOrNull() ?: 0

        val rec = SimpleRecord(
            id = id, name = name, address = address, age = age,
            timestamp = Instant.now().epochSecond,
            commitment = "$id|$electionId",
            u16OneHot = enc,
            receiptCtB64 = body.receiptCtB64
        )

        state.chain.addBlock(records = listOf(rec), contract = emptyList(), key = Keypair(0L))
        return mapOf("ok" to true, "recordId" to id)
    }

    fun userVotes(): List<List<String>> {
        val b64 = Base64.getEncoder()
        return state.chain.getChain()
            .flatMap { it.records }
            .filterIsInstance<SimpleRecord>()
            .map { rec -> rec.u16OneHot.map { bytes -> b64.encodeToString(bytes) } }
    }

    fun receiptFor(id: String): String? = findRecordById(id)?.receiptCtB64
}



// inside the same package as Receipt.kt
fun receiptForRecord(
    rec: SimpleRecord,            // ← use your actual record type name
    electionId: String
): ReceiptOut {
    val createdAt = Date().toInstant().epochSecond
    val commit = commitmentOf(electionId, rec.u16OneHot, rec.id, createdAt)
    return ReceiptOut(
        id = rec.id,
        electionId = electionId,
        commitmentB64 = encodeB64(commit),
        serverSigB64 = encodeB64(ReceiptSigning.sign(commit)),
        serverPubKeyB64 = ReceiptSigning.pubKeyB64,
        createdAt = createdAt,
        // nice-to-have for your Verify UI:
        receiptBitsB64 = rec.u16OneHot.map(::encodeB64)
    )
}
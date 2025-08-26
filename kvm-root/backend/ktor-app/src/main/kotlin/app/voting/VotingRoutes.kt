package app.voting

import app.api.ErrorOut
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import java.io.File
import java.time.Instant
import java.util.Base64
import kvm.core.Blockchain
import kvm.model.SimpleRecord
import kvm.native.EncPtr
import kvm.native.Keypair
import kvm.native.U16
import kvm.voting.*

private const val NUM_CANDIDATES = 4
private const val ELECTION_ID = "election-1"

private fun oneHot(ix: Int, n: Int) = List(n) { if (it == ix) 1 else 0 }

private object VotingState {
    val chain = Blockchain().apply { mineGenesis() }
    val publicDir = File("public").also { it.mkdirs() }

    // Integer (u16) election keypair (in-memory)
    val u16Keys = U16.generateKeypair()

    // Compressed ServerKey (write once so verifier can fetch)
    val compressedServerKey: ByteArray = U16.exportCompressedServerKey(u16Keys).also {
        File(publicDir, "u16_server_key.bin").writeBytes(it)
        // DEV-ONLY: write client key so the verifier can decrypt tallies locally
        File(publicDir, "u16_client_key.bin").writeBytes(U16.exportClientKey(u16Keys))
    }
}

private fun requireBase64OrNull(b64: String?) {
    if (b64 == null) return
    try { Base64.getDecoder().decode(b64) } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid base64 in receiptCtB64")
    }
}

private fun findRecordById(id: String): SimpleRecord? =
    VotingState.chain.getChain().asSequence()
        .flatMap { it.records.asSequence() }
        .filterIsInstance<SimpleRecord>()
        .firstOrNull { it.id == id }

fun Application.votingRoutes() {
    routing {
        // Publish compressed server key for the offline verifier
        get("/server-key") {
            call.respondBytes(VotingState.compressedServerKey, ContentType.Application.OctetStream)
        }

        // Summaries for client-side membership confirmation
        get("/blocks") {
            val blocks = VotingState.chain.getChain().map { b ->
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
            call.respond(blocks)
        }

        // POST /vote → encrypt one-hot (u16) under election key; store optional per-voter receipt
        post("/vote") {
            val body = call.receive<VoteIn>()
            require(body.candidate in 0 until NUM_CANDIDATES) { "candidate out of range" }
            requireBase64OrNull(body.receiptCtB64)

            val bits = oneHot(body.candidate, NUM_CANDIDATES)
            val enc: List<ByteArray> = bits.map { bit ->
                val ct: EncPtr = U16.encrypt(bit, VotingState.u16Keys)
                U16.serialize(ct)
            }

            val rec = SimpleRecord(
                id = body.id,
                name = body.name,
                address = body.address,
                age = body.age,
                timestamp = Instant.now().epochSecond,
                commitment = "${body.id}|$ELECTION_ID",
                u16OneHot = enc,
                receiptCtB64 = body.receiptCtB64
            )

            VotingState.chain.addBlock(
                records = listOf(rec),
                contract = emptyList(),
                key = Keypair(0L) // not used since contract is empty
            )

            call.respond(HttpStatusCode.OK, VoteOut(ok = true, candidate = body.candidate, recordId = body.id))
        }

        // (Optional) POST /vote/raw → accept pre-encrypted one-hot (u16) + optional receipt
        post("/vote/raw") {
            val body = call.receive<VoteRequest>()
            require(body.ballot.isNotEmpty()) { "ballot must be a non-empty list" }
            requireBase64OrNull(body.receiptCtB64)

            val dec = Base64.getDecoder()
            val enc = body.ballot.map { b64 ->
                try { dec.decode(b64) } catch (_: IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid base64 in ballot")
                }
            }

            // Minimal identity (caller can pass their own via query)
            val id = call.request.queryParameters["id"] ?: "u${Instant.now().toEpochMilli()}"
            val name = call.request.queryParameters["name"] ?: ""
            val address = call.request.queryParameters["address"] ?: ""
            val age = call.request.queryParameters["age"]?.toIntOrNull() ?: 0

            val rec = SimpleRecord(
                id = id, name = name, address = address, age = age,
                timestamp = Instant.now().epochSecond,
                commitment = "$id|$ELECTION_ID",
                u16OneHot = enc,
                receiptCtB64 = body.receiptCtB64
            )

            VotingState.chain.addBlock(records = listOf(rec), contract = emptyList(), key = Keypair(0L))
            call.respond(HttpStatusCode.OK, mapOf("ok" to true, "recordId" to id))
        }

        // Verifier feed: all ballots as base64 u16 ciphertexts (one-hot per record)
        get("/user-votes") {
            val b64 = Base64.getEncoder()
            val votes: List<List<String>> =
                VotingState.chain.getChain()
                    .flatMap { it.records }
                    .filterIsInstance<SimpleRecord>()
                    .map { rec -> rec.u16OneHot.map { bytes -> b64.encodeToString(bytes) } }
            call.respond(votes)
        }

        // Fetch the stored per-voter receipt ciphertext for a given record id
        get("/receipt/{id}") {
            val id = call.parameters["id"]!!
            val rec = findRecordById(id) ?: return@get call.respond(HttpStatusCode.NotFound, ErrorOut("not found"))
            call.respond(mapOf("id" to id, "receiptCtB64" to rec.receiptCtB64))
        }
    }
}

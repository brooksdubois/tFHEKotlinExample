package app

import io.ktor.http.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.util.Base64
import kvm.core.Blockchain
import kvm.model.SimpleRecord
import kvm.native.EncPtr
import kvm.native.Keypair
import kvm.native.NativeLoader
import kvm.native.U16

private const val NUM_CANDIDATES = 4
private const val ELECTION_ID = "election-1"

private fun oneHot(ix: Int, n: Int) = List(n) { if (it == ix) 1 else 0 }

// ----------------------------
// State: chain + election keys
// ----------------------------
private object State {
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

// ----------------------------
// DTOs
// ----------------------------
@Serializable
data class VoteIn(
    val id: String,
    val name: String,
    val address: String,
    val age: Int,
    val candidate: Int,
    val receiptCtB64: String? = null // OPTIONAL: per-voter receipt ciphertext (under voter's key)
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

@Serializable
data class VoteOut(
    val ok: Boolean,
    val candidate: Int,
    val recordId: String
)

@Serializable
data class ErrorOut(
    val error: String,
    val code: String? = null,
    val details: String? = null
)

// Optional: raw ballot ingestion (pre-encrypted one-hot u16s), if/when you want it
@Serializable
data class VoteRequest(
    val ballot: List<String>,        // base64 u16 ciphertexts, one per candidate
    val receiptCtB64: String? = null // OPTIONAL
)

// ----------------------------
// Helpers
// ----------------------------
private fun requireBase64OrNull(b64: String?) {
    if (b64 == null) return
    try { Base64.getDecoder().decode(b64) } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid base64 in receiptCtB64")
    }
}

private fun findRecordById(id: String): SimpleRecord? =
    State.chain.getChain().asSequence()
        .flatMap { it.records.asSequence() }
        .filterIsInstance<SimpleRecord>()
        .firstOrNull { it.id == id }

// ----------------------------
// Ktor module
// ----------------------------
fun Application.module() {
    NativeLoader.load()

    install(CORS) {
        allowHost("localhost:3000", schemes = listOf("http"))
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowNonSimpleContentTypes = true
        allowCredentials = true
        maxAgeInSeconds = 86400
    }
    install(ContentNegotiation) { json() }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorOut(cause.message ?: "invalid input"))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorOut("server error"))
            throw cause
        }
    }

    routing {
        // Publish compressed server key for the offline verifier
        get("/server-key") {
            call.respondBytes(State.compressedServerKey, ContentType.Application.OctetStream)
        }

        // Summaries for client-side membership confirmation
        get("/blocks") {
            val blocks = State.chain.getChain().map { b ->
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

            // Build one-hot and encrypt each slot as u16 (0 or 1)
            val bits = oneHot(body.candidate, NUM_CANDIDATES)
            val enc: List<ByteArray> = bits.map { bit ->
                val ct: EncPtr = U16.encrypt(bit, State.u16Keys)
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
                receiptCtB64 = body.receiptCtB64 // NEW: persisted as-is (already base64-checked)
            )

            // Append to chain (no contract checks yet)
            State.chain.addBlock(
                records = listOf(rec),
                contract = emptyList(),
                key = Keypair(0L) // not used since contract is empty
            )

            call.respond(
                HttpStatusCode.OK,
                VoteOut(ok = true, candidate = body.candidate, recordId = body.id)
            )
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

            // Minimal identity (caller must provide their own id in headers or query if desired)
            val id = call.request.queryParameters["id"] ?: "u${Instant.now().toEpochMilli()}"
            val name = call.request.queryParameters["name"] ?: ""
            val address = call.request.queryParameters["address"] ?: ""
            val age = call.request.queryParameters["age"]?.toIntOrNull() ?: 0

            val rec = SimpleRecord(
                id = id,
                name = name,
                address = address,
                age = age,
                timestamp = Instant.now().epochSecond,
                commitment = "$id|$ELECTION_ID",
                u16OneHot = enc,
                receiptCtB64 = body.receiptCtB64
            )

            State.chain.addBlock(
                records = listOf(rec),
                contract = emptyList(),
                key = Keypair(0L)
            )

            call.respond(HttpStatusCode.OK, mapOf("ok" to true, "recordId" to id))
        }

        // Verifier feed: all ballots as base64 u16 ciphertexts (one-hot per record)
        get("/user-votes") {
            val b64 = Base64.getEncoder()
            val votes: List<List<String>> =
                State.chain.getChain()
                    .flatMap { it.records }
                    .filterIsInstance<SimpleRecord>()
                    .map { rec -> rec.u16OneHot.map { bytes -> b64.encodeToString(bytes) } }

            call.respond(votes)
        }

        // Fetch the stored per-voter receipt ciphertext for a given record id
        get("/receipt/{id}") {
            val id = call.parameters["id"]!!
            val rec = findRecordById(id) ?: return@get call.respond(HttpStatusCode.NotFound, ErrorOut("not found"))
            call.respond(mapOf("id" to rec.id, "receiptCtB64" to rec.receiptCtB64))
        }
    }
}

/** Block-body main avoids the “main() should return Unit” false positive in some IDE states. */
fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

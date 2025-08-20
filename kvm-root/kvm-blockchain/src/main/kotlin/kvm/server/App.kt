package server

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
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kvm.core.Blockchain
import kvm.model.SimpleRecord
import kvm.native.TfheBridge
import kvm.encrypted.EncryptedInt
import kvm.encrypted.writeUserVotesJson
import kvm.native.NativeLoader
import mpc.oneHot
import mpc.additiveShares
import mpc.appendShareRow
import mpc.localAggregate

private const val NUM_CANDIDATES = 4
private const val NUM_PARTIES = 3

@Serializable
data class VoteIn(val id: String, val name: String, val address: String, val age: Int, val vote: Int)

@Serializable
data class ReceiptOut(val id: String, val commitment: String, val clientKeyB64: String, val receiptBitsB64: List<String>)

@Serializable
data class TallyOut(val counts: Map<Int, Int>)

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
data class ChainOut(val blocks: List<BlockSummary>)

private object State {
    val chain = Blockchain().apply { mineGenesis() }
    val publicDir = File("public").also { it.mkdirs() }
    val mpcDir = File("public/mpc").also { it.mkdirs() }
}

private fun resetMpcFiles(dir: File, parties: Int) {
    dir.mkdirs()
    repeat(parties) { pid -> File(dir, "party_${pid}.jsonl").writeText("") }
}

private fun commitment(id: String, electionId: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("$id|$electionId".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

fun Application.module() {
    NativeLoader.load()
    install(CORS) {
        allowHost("localhost:3000", schemes = listOf("http"))
        allowMethod(HttpMethod.Options); allowMethod(HttpMethod.Get); allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization); allowHeader(HttpHeaders.ContentType)
        allowNonSimpleContentTypes = true; allowCredentials = true; maxAgeInSeconds = 86400
    }
    install(ContentNegotiation) { json() }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, mapOf("error" to cause.message))
        }
        exception<Throwable> { call, cause ->
            // last-resort safety
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "server error"))
            throw cause  // or log it
        }
    }

    resetMpcFiles(State.mpcDir, NUM_PARTIES) // dev: clear previous shares


    routing {
        get("/") { call.respond(mapOf("ok" to true)) }

        post("/vote") {
            val v = call.receive<VoteIn>()
            require(v.vote in 0 until NUM_CANDIDATES) { "vote out of range" }

            val now = Instant.now().epochSecond
            val userKey = TfheBridge.generateKeypair()
            val clientKeyB64 = Base64.getEncoder().encodeToString(userKey.exportClientKey())

            // TFHE receipt (user-only decrypt)
            val userEncrypted = EncryptedInt.fromInt(v.vote, userKey)
            val receiptBitsB64 = userEncrypted.serialize().map { Base64.getEncoder().encodeToString(it) }

            val cmt = commitment(v.id, "election2025")

            // Build record first
            val rec = SimpleRecord(
                id = v.id, name = v.name, address = v.address, age = v.age,
                vote = userEncrypted, userEncryptedVote = userEncrypted.serialize(),
                timestamp = now, commitment = cmt
            )

            // ✅ Try to add block BEFORE writing shares
            State.chain.addBlock(listOf(rec), emptyList(), TfheBridge.generateKeypair())

            // ⬇️ Only after success, create and append shares
            val shares = additiveShares(oneHot(v.vote, NUM_CANDIDATES), NUM_PARTIES)
            shares.forEachIndexed { partyId, shareVec ->
                appendShareRow(State.mpcDir, partyId, v.id, shareVec)
            }

            // Rewrite user receipts export (optional, but do it after accept)
            val all = State.chain.getChain().flatMap { it.records }
            writeUserVotesJson(all, File(State.publicDir, "encrypted_user_votes.json").path)

            call.respond(ReceiptOut(v.id, cmt, clientKeyB64, receiptBitsB64))
        }

        get("/tally") {
            val totals = localAggregate(State.mpcDir, NUM_PARTIES, NUM_CANDIDATES)
            call.respond(TallyOut((0 until NUM_CANDIDATES).associateWith { totals[it] }))
        }

        get("/mpc/party/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondText("bad id")
            val f = File(State.mpcDir, "party_${id}.jsonl")
            if (!f.exists()) return@get call.respondText("not found")
            call.respondFile(f)
        }

        get("/chain") {
            val blocks = State.chain.getChain().map { b ->
                BlockSummary(
                    index = b.index,
                    recordCount = b.records.size,
                    records = b.records.map { r ->
                        RecordSummary(
                            id = r.id,
                            name = r.name,
                            address = r.address,
                            age = r.age,
                            timestamp = r.timestamp,
                            commitment = r.commitment
                        )
                    }
                )
            }
            call.respond(ChainOut(blocks))
        }
    }
}

/** Block-body main avoids the “main() should return Unit” false positive in some IDE states. */
fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

package kvm.server

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
import kvm.native.EncPtr
import kvm.native.Keypair
import kvm.native.NativeLoader
import kvm.native.U16

private const val NUM_CANDIDATES = 4
private fun oneHot(ix: Int, n: Int) = List(n) { if (it == ix) 1 else 0 }

// Holds the chain and the election keys for u16
private object State {
    val chain = Blockchain().apply { mineGenesis() }  // chain exists here. :contentReference[oaicite:0]{index=0}
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

@Serializable
data class VoteIn(
    val id: String,
    val name: String,
    val address: String,
    val age: Int,
    val candidate: Int
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
    val error: String
)

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

        // POST /vote → encrypt a one-hot vector (u16), store on chain
        post("/vote") {
            val body = call.receive<VoteIn>()
            require(body.candidate in 0 until NUM_CANDIDATES) { "candidate out of range" }

            // Build one-hot and encrypt each position as a u16 (0 or 1)
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
                commitment = "${body.id}|election-1",
                u16OneHot = enc
            )

            // Chain has addBlock(records, contract, key). We pass no contract checks and a dummy key.
            State.chain.addBlock(
                records = listOf(rec),
                contract = emptyList(),                 // no KVE checks for now
                key = Keypair(0L)                      // never used since contract is empty
            )                                          // API shown here. :contentReference[oaicite:1]{index=1}

            call.respond(HttpStatusCode.OK, VoteOut(
                ok = true,
                candidate = body.candidate,
                recordId = body.id
            ))
        }

        // Verifier feed: all ballots as base64 u16 ciphertexts (one-hot per record)
        get("/user-votes") {
            val b64 = Base64.getEncoder()
            val votes: List<List<String>> =
                State.chain.getChain()                 // current chain API. :contentReference[oaicite:2]{index=2}
                    .flatMap { it.records }
                    .map { rec -> rec.u16OneHot.map { b -> b64.encodeToString(b) } }

            call.respond(votes)
        }
    }
}

/** Block-body main avoids the “main() should return Unit” false positive in some IDE states. */
fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

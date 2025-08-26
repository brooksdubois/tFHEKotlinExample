package app.mpc

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.io.File
import kvm.mpc.*
import kvm.native.U16
import kvm.native.U16Server

private val json = Json { prettyPrint = true }

private fun b64Decode(s: String) = Base64.getDecoder().decode(s)
private fun b64Encode(b: ByteArray) = Base64.getEncoder().encodeToString(b)
private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
private fun requireSizeEq(a: Int, b: Int, msg: String) { require(a == b) { "$msg (got $a vs $b)" } }
private fun modU16(x: Int) = x and 0xFFFF

private fun loadServerCtx() =
    U16Server.fromCompressed(File("public/u16_server_key.bin").readBytes())

private fun importClientKeyB64(b64: String) =
    U16.importClientKey(b64Decode(b64))

private fun masksCommitHex(masks: List<Int>): String =
    sha256Hex(json.encodeToString(masks).encodeToByteArray())

private fun addMasksWithServer(ctsB64: List<String>, masks: List<Int>): List<String> {
    val srv = loadServerCtx()
    val cts = ctsB64.map { U16.deserialize(b64Decode(it)) }
    requireSizeEq(cts.size, masks.size, "masks length must match ciphertext vector length")
    val out = cts.zip(masks).map { (ct, m) -> U16Server.addClear(srv, ct, m) }
    return out.map { b64Encode(U16.serialize(it)) }
}

private fun decryptMasked(ctsB64: List<String>, clientKeyB64: String): List<Int> {
    val ck = importClientKeyB64(clientKeyB64)
    val cts = ctsB64.map { U16.deserialize(b64Decode(it)) }
    return cts.map { U16.decrypt(it, ck) }
}

private fun clientKeyB64FromFile(path: String): String =
    Base64.getEncoder().encodeToString(File(path).readBytes())


fun Application.mpcRoutes() {
    routing {
        route("/mpc/sessions") {

            /* 1) Start a session; persist initial_cts.json; return tiny StartOut */
            post("/start") {
                val req = call.receive<StartReq>()
                require(req.source in listOf("upload", "live")) { "source must be 'upload' or 'live'" }

                val (initial, candidates) = when (req.source) {
                    "upload" -> {
                        val cts: List<String> = req.ctsB64
                            ?: throw IllegalArgumentException("ctsB64 required for source=upload")
                        val c = req.candidates ?: cts.size
                        cts to c
                    }
                    "live" -> error("source=live not wired: compute fold server-side or use 'upload'")
                    else -> error("unsupported source")
                }

                val id = MpcStore.newId()
                val session = MpcSession(
                    id = id,
                    createdAtMs = Instant.now().toEpochMilli(),
                    candidates = candidates,
                    initialCtsB64 = initial,
                    currentCtsB64 = initial.toList()
                )
                MpcStore.write(session)
                val a1 = MpcStore.writeText(id, "initial_cts.json", json.encodeToString(initial))
                call.respond(StartOut(session, listOf(a1)))
            }

            /* 2a) Dev-only masker: server generates masks, applies, and persists artifacts */
            post("/{id}/mask:server") {
                val id = call.parameters["id"]!!
                val req = call.receive<MaskServerReq>()
                val session = MpcStore.read(id)

                val seed = req.seed
                val rnd = seed?.let { kotlin.random.Random(it) } ?: kotlin.random.Random.Default
                val masks = List(session.currentCtsB64.size) { rnd.nextInt(0, 1 shl 16) }
                val masked = addMasksWithServer(session.currentCtsB64, masks)
                val commitHex = masksCommitHex(masks)

                val commit = MaskCommit(req.who, commitHex, seq = session.commits.size, maskedCtsB64 = masked)
                session.currentCtsB64 = masked
                session.commits += commit
                session.status = MpcStatus.MASKING
                MpcStore.write(session)

                val wrote = buildList {
                    add(MpcStore.writeText(id, "commit_${commit.seq}.json", json.encodeToString(commit)))
                    add(MpcStore.writeText(id, "masked_cts_seq${commit.seq}.json", json.encodeToString(masked)))
                    // DEV ONLY: persist masks for convenience
                    add(MpcStore.writeText(id, "masks_${req.who}.json", json.encodeToString(masks)))
                }
                call.respond(MaskOut(commit, wrote))
            }

            /* 2b) Prod-style masker: client posts maskedCts + commitment; server persists */
            post("/{id}/mask") {
                val id = call.parameters["id"]!!
                val req = call.receive<MaskSubmitReq>()
                val session = MpcStore.read(id)
                requireSizeEq(session.currentCtsB64.size, req.maskedCtsB64.size, "masked vector length mismatch")

                val commit = MaskCommit(req.who, req.masksHashHex, seq = session.commits.size, maskedCtsB64 = req.maskedCtsB64)
                session.currentCtsB64 = req.maskedCtsB64
                session.commits += commit
                session.status = MpcStatus.MASKING
                MpcStore.write(session)

                val wrote = listOf(
                    MpcStore.writeText(id, "commit_${commit.seq}.json", json.encodeToString(commit)),
                    MpcStore.writeText(id, "masked_cts_seq${commit.seq}.json", json.encodeToString(req.maskedCtsB64))
                )
                call.respond(MaskOut(commit, wrote))
            }

            /* 3) Decrypt masked totals (dev-only); persist masked_plain.json; return count + meta */
            post("/{id}/decrypt") {
                val id = call.parameters["id"]!!
                val req = call.receive<DecryptReq>()
                val session = MpcStore.read(id)

                // Choose key source: B64 → path → default dev path
                val keyB64 = req.clientKeyB64
                    ?: req.clientKeyPath?.let { clientKeyB64FromFile(it) }
                    ?: clientKeyB64FromFile("public/u16_client_key.bin")

                val maskedPlain = decryptMasked(session.currentCtsB64, keyB64)
                session.maskedPlain = maskedPlain
                session.status = MpcStatus.MASKED_DECRYPTED
                MpcStore.write(session)

                val wrote = listOf(
                    MpcStore.writeText(id, "masked_plain.json", json.encodeToString(maskedPlain))
                )
                call.respond(DecryptOut(maskedPlain.size, wrote))
            }

            /* 4) Reveal masks; persist reveal & totals; return minimal RevealOut */
            post("/{id}/reveal") {
                val id = call.parameters["id"]!!
                val req = call.receive<RevealReq>()
                val session = MpcStore.read(id)

                require(session.maskedPlain != null) { "Call /decrypt first to publish masked plaintext totals" }
                require(req.masks.size == session.candidates) { "masks length must equal candidates" }

                val commit = session.commits.find { it.who == req.who }
                    ?: error("no prior commit by '${req.who}'")
                val computedHex = masksCommitHex(req.masks)
                require(computedHex == commit.masksHashHex) { "masks commitment mismatch for '${req.who}'" }

                session.reveals.removeIf { it.who == req.who }
                session.reveals += MaskReveal(req.who, req.masks)

                val sumMasks = IntArray(session.candidates) { 0 }
                for (r in session.reveals) r.masks.forEachIndexed { i, m -> sumMasks[i] = modU16(sumMasks[i] + m) }
                val masked = session.maskedPlain!!
                val totals = List(session.candidates) { i -> modU16(masked[i] - sumMasks[i]) }

                session.totals = totals
                session.status = if (session.reveals.size >= session.commits.size) MpcStatus.FINALIZED else MpcStatus.REVEALED
                MpcStore.write(session)

                val wrote = listOf(
                    MpcStore.writeText(id, "reveal_${req.who}.json", json.encodeToString(req)),
                    MpcStore.writeText(id, "totals.json", json.encodeToString(totals))
                )
                call.respond(RevealOut(totals, session.status == MpcStatus.FINALIZED, session.reveals, wrote))
            }

            /* 5) Inspect transcript (can be large) */
            get("/{id}") {
                val id = call.parameters["id"]!!
                call.respond(MpcStore.read(id))
            }

            /* 6) List artifacts (small summary; perfect for .http) */
            get("/{id}/artifacts") {
                val id = call.parameters["id"]!!
                call.respond(MpcStore.listArtifacts(id))
            }

            /* 7) Download a zip of the session directory */
            get("/{id}/zip") {
                val id = call.parameters["id"]!!
                val zip = MpcStore.zipSession(id)
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${zip.fileName}\"")
                call.respondFile(zip.toFile())
            }
        }
    }
}

package app.mpc

import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kvm.mpc.*
import kvm.native.U16
import kvm.native.U16Server
import app.state.VotingState
import kvm.model.SimpleRecord

class MpcUseCase(
    private val json: Json = Json { prettyPrint = true },
    private val store: MpcStore = MpcStore,
) {
    // --- helpers (moved from routes) ---
    private fun b64Decode(s: String) = Base64.getDecoder().decode(s)
    private fun b64Encode(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun requireSizeEq(a: Int, b: Int, msg: String) { require(a == b) { "$msg (got $a vs $b)" } }
    private fun modU16(x: Int) = x and 0xFFFF

    private fun loadServerCtx() = U16Server.fromCompressed(File("public/u16_server_key.bin").readBytes())
    private fun importClientKeyB64(b64: String) = U16.importClientKey(b64Decode(b64))
    private fun masksCommitHex(masks: List<Int>) = sha256Hex(json.encodeToString(masks).encodeToByteArray())

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

    private fun clientKeyB64FromFile(path: String) =
        Base64.getEncoder().encodeToString(File(path).readBytes())

    private fun foldLiveTalliesB64(): Pair<List<String>, Int> {
        val srv = loadServerCtx()
        val records = VotingState.chain.getChain()
            .flatMap { it.records }
            .filterIsInstance<SimpleRecord>()

        val candidates = records.firstOrNull()?.u16OneHot?.size ?: 4
        val acc = Array<kvm.native.EncPtr?>(candidates) { null }

        for (rec in records) {
            if (rec.u16OneHot.size != candidates) continue
            for (i in 0 until candidates) {
                val ct = U16.deserialize(rec.u16OneHot[i])
                acc[i] = acc[i]?.let { U16Server.add(srv, it, ct) } ?: ct
            }
        }
        val filled = acc.map { it ?: U16.encrypt(0, VotingState.u16Keys) }
        val b64 = filled.map { Base64.getEncoder().encodeToString(U16.serialize(it)) }
        return b64 to candidates
    }

    // --- use cases ---
    fun start(req: StartReq): StartOut {
        require(req.source in listOf("upload", "live")) { "source must be 'upload' or 'live'" }

        val (initial, candidates) = when (req.source) {
            "upload" -> {
                val cts = req.ctsB64 ?: throw IllegalArgumentException("ctsB64 required for source=upload")
                val c = req.candidates ?: cts.size
                cts to c
            }
            "live" -> {
                val (b64, c) = foldLiveTalliesB64()
                b64 to (req.candidates ?: c)
            }
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
        store.write(session)
        val a1 = store.writeText(id, "initial_cts.json", json.encodeToString(initial))
        return StartOut(session, listOf(a1))
    }

    fun maskServer(id: String, req: MaskServerReq): MaskOut {
        val session = store.read(id)
        val rnd = req.seed?.let { kotlin.random.Random(it) } ?: kotlin.random.Random.Default
        val masks = List(session.currentCtsB64.size) { rnd.nextInt(0, 1 shl 16) }
        val masked = addMasksWithServer(session.currentCtsB64, masks)
        val commitHex = masksCommitHex(masks)

        val commit = MaskCommit(req.who, commitHex, seq = session.commits.size, maskedCtsB64 = masked)
        session.currentCtsB64 = masked
        session.commits += commit
        session.status = MpcStatus.MASKING
        store.write(session)

        val wrote = buildList {
            add(store.writeText(id, "commit_${commit.seq}.json", json.encodeToString(commit)))
            add(store.writeText(id, "masked_cts_seq${commit.seq}.json", json.encodeToString(masked)))
            add(store.writeText(id, "masks_${req.who}.json", json.encodeToString(masks))) // DEV ONLY
        }
        return MaskOut(commit, wrote)
    }

    fun mask(id: String, req: MaskSubmitReq): MaskOut {
        val session = store.read(id)
        requireSizeEq(session.currentCtsB64.size, req.maskedCtsB64.size, "masked vector length mismatch")

        val commit = MaskCommit(req.who, req.masksHashHex, seq = session.commits.size, maskedCtsB64 = req.maskedCtsB64)
        session.currentCtsB64 = req.maskedCtsB64
        session.commits += commit
        session.status = MpcStatus.MASKING
        store.write(session)

        val wrote = listOf(
            store.writeText(id, "commit_${commit.seq}.json", json.encodeToString(commit)),
            store.writeText(id, "masked_cts_seq${commit.seq}.json", json.encodeToString(req.maskedCtsB64))
        )
        return MaskOut(commit, wrote)
    }

    fun decrypt(id: String, req: DecryptReq): DecryptOut {
        val session = store.read(id)
        val keyB64 = req.clientKeyB64
            ?: req.clientKeyPath?.let { clientKeyB64FromFile(it) }
            ?: clientKeyB64FromFile("public/u16_client_key.bin")

        val maskedPlain = decryptMasked(session.currentCtsB64, keyB64)
        session.maskedPlain = maskedPlain
        session.status = MpcStatus.MASKED_DECRYPTED
        store.write(session)

        val wrote = listOf(store.writeText(id, "masked_plain.json", json.encodeToString(maskedPlain)))
        return DecryptOut(maskedPlain.size, wrote)
    }

    fun reveal(id: String, req: RevealReq): RevealOut {
        val session = store.read(id)
        require(session.maskedPlain != null) { "Call /decrypt first to publish masked plaintext totals" }
        require(req.masks.size == session.candidates) { "masks length must equal candidates" }

        val commit = session.commits.find { it.who == req.who } ?: error("no prior commit by '${req.who}'")
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
        store.write(session)

        val wrote = listOf(
            store.writeText(id, "reveal_${req.who}.json", json.encodeToString(req)),
            store.writeText(id, "totals.json", json.encodeToString(totals))
        )
        return RevealOut(totals, session.status == MpcStatus.FINALIZED, session.reveals, wrote)
    }

    fun read(id: String): MpcSession = store.read(id)
    fun listArtifacts(id: String) = store.listArtifacts(id)
    fun zipSession(id: String) = store.zipSession(id)
}

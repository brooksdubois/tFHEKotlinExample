package app.mpc

import app.voting.VotingUseCase
import kvm.mpc.StartSessionReq
import kvm.mpc.StartSessionRes
import kvm.mpc.startMpcSession
import kvm.mpc.InitialCtsJson
import kvm.mpc.MaskCommit
import kvm.mpc.MaskOut
import kvm.mpc.MaskServerReq
import kvm.mpc.DecryptReq
import kvm.mpc.DecryptOut
import kvm.mpc.RevealReq
import kvm.mpc.RevealOut
import java.nio.file.Files
import kvm.native.EncPtr
import kvm.native.U16
import java.nio.file.Path
import java.util.Base64
import kvm.native.U16Server // adjust import to your JNI wrapper if named differently
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MpcUseCase(
    private val mpcBaseDir: Path,
    private val voting: VotingUseCase = VotingUseCase()
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; explicitNulls = false }
    private fun dir(id: String) = mpcBaseDir.resolve(id).also { Files.createDirectories(it) }
    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun b64d(s: String) = Base64.getDecoder().decode(s)
    private fun sha256Hex(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun readCurrent(id: String): InitialCtsJson {
        val d = dir(id)
        val cur = d.resolve("current_cts.json")
        val init = d.resolve("initial_cts.json")
        return when {
            cur.exists() -> json.decodeFromString<InitialCtsJson>(cur.readText())
            else         -> json.decodeFromString<InitialCtsJson>(init.readText())
        }
    }

    fun start(req: StartSessionReq): StartSessionRes {
        // Defer fold to a lambda (core stays framework-agnostic)
        val fold = {
            // Run the heavy fold on a background dispatcher
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Default) { foldFromLedger() }
        }
        return startMpcSession(req, fold, mpcBaseDir)
    }

    /** Fold all stored votes into a per-candidate encrypted total vector (ctsB64). */
    fun foldFromLedger(): Pair<List<String>, Int> {
        val srv = app.state.VotingState.u16ServerCtx
        val records = app.state.VotingState.chain.getChain()
            .flatMap { it.records }
            .filterIsInstance<kvm.model.SimpleRecord>()

        val candidates = records.firstOrNull()?.u16OneHot?.size ?: 4
        val acc = Array<EncPtr?>(candidates) { null }

        for (rec in records) {
            if (rec.u16OneHot.size != candidates) continue
            for (j in 0 until candidates) {
                val ct: EncPtr = U16.deserialize(rec.u16OneHot[j])        // bytes -> EncPtr
                acc[j] = acc[j]?.let { U16Server.add(srv, it, ct) } ?: ct  // EncPtr + ctx -> EncPtr
            }
        }

        // Fill empty slots with Enc(0) and serialize to base64 for transport
        val filled: List<EncPtr> = acc.map { it ?: U16.encrypt(0, app.state.VotingState.u16Keys) }
        val outB64 = filled.map { Base64.getEncoder().encodeToString(U16.serialize(it)) }
        return outB64 to candidates
    }

    /* ---------- MPC steps ---------- */
    fun maskServer(id: String, req: MaskServerReq): MaskOut {
        val d = dir(id)
        val (ctsB64, n) = readCurrent(id).let { it.ctsB64 to it.candidateCount }
        val srv = app.state.VotingState.u16ServerCtx
        // deterministic masks if seed provided (dev: default uses req.seed)
        val rnd = java.util.Random(req.seed ?: System.currentTimeMillis())
        val masks: List<Int> = List(n) { rnd.nextInt(0x10000) } // 0..65535
        val masksJson = json.encodeToString(masks)
        val masksHash = sha256Hex(masksJson.toByteArray())
        val curPtrs: List<EncPtr> = ctsB64.map { U16.deserialize(b64d(it)) }
        val newPtrs: List<EncPtr> = curPtrs.zip(masks).map { (ct, m) -> U16Server.addClear(srv, ct, m) }
        val newB64: List<String> = newPtrs.map { b64(U16.serialize(it)) }
        // persist
        d.resolve("current_cts.json").writeText(json.encodeToString(InitialCtsJson(newB64, n)))
        d.resolve("masks_${req.who}.json").writeText(masksJson)  // dev convenience for the ZIP step
        val seq = Files.list(d).use { s ->
            s.filter { it.fileName.toString().startsWith("commit_") }.count().toInt()
        }
        val commit = MaskCommit(who = req.who, masksHashHex = masksHash, seq = seq, maskedCtsB64 = newB64)
        d.resolve("commit_${seq}_${req.who}.json").writeText(json.encodeToString(commit))
        return MaskOut(commit = commit, artifacts = listOf(
            kvm.mpc.ArtifactMeta("current_cts.json", "mpc/$id/current_cts.json", 0, masksHash, System.currentTimeMillis()),
            kvm.mpc.ArtifactMeta("masks_${req.who}.json", "mpc/$id/masks_${req.who}.json", 0, masksHash, System.currentTimeMillis()),
            kvm.mpc.ArtifactMeta("commit_${seq}_${req.who}.json", "mpc/$id/commit_${seq}_${req.who}.json", 0, masksHash, System.currentTimeMillis()),
        ))
    }
    fun decrypt(id: String, req: DecryptReq): DecryptOut {
        val d = dir(id)
        val (ctsB64, _) = readCurrent(id).let { it.ctsB64 to it.candidateCount }
        val keys = when {
            !req.clientKeyB64.isNullOrBlank() -> U16.importClientKey(Base64.getDecoder().decode(req.clientKeyB64))
            !req.clientKeyPath.isNullOrBlank() -> U16.importClientKey(File(req.clientKeyPath).readBytes())
            else -> U16.importClientKey(File("public/u16_client_key.bin").readBytes())
        }
        val pts: List<Int> = ctsB64
            .map { U16.deserialize(b64d(it)) }
            .map { U16.decrypt(it, keys) }
        d.resolve("masked_plain.json").writeText(json.encodeToString(pts))
        return DecryptOut(maskedPlainCount = pts.size)
    }
    fun reveal(id: String, req: RevealReq): RevealOut {
        val d = dir(id)
        val maskedPlain: List<Int> = json.decodeFromString(d.resolve("masked_plain.json").readText())
        // write this reveal
        val revealPath = d.resolve("reveal_${req.who}.json")
        revealPath.writeText(json.encodeToString(req))
        // combine all reveals so far
        val allReveals: List<kvm.mpc.MaskReveal> = Files.list(d).use { s ->
            s.filter { it.fileName.toString().startsWith("reveal_") }
                .map { json.decodeFromString<kvm.mpc.MaskReveal>(it.readText()) }
                .toList()
        }
        val commitsCount = Files.list(d).use { s ->
            s.filter { it.fileName.toString().startsWith("commit_") }.count().toInt()
        }
        // sum masks per index
        val sumMasks = IntArray(maskedPlain.size)
        for (rev in allReveals) {
            for (i in rev.masks.indices) sumMasks[i] = (sumMasks[i] + (rev.masks[i] and 0xFFFF)) and 0xFFFF
        }
        val totals = maskedPlain.mapIndexed { i, mp -> ((mp - sumMasks[i]) and 0xFFFF) }
        d.resolve("totals.json").writeText(json.encodeToString(totals))
        val finalized = allReveals.size >= commitsCount && commitsCount > 0
        return RevealOut(
            totals = totals,
            finalized = finalized,
            reveals = allReveals
        )
    }
    fun status(id: String): Map<String, Any?> {
        val d = dir(id)
        val commits = Files.list(d).use { s -> s.filter { it.fileName.toString().startsWith("commit_") }.count().toInt() }
        val reveals = Files.list(d).use { s -> s.filter { it.fileName.toString().startsWith("reveal_") }.count().toInt() }
        val hasMasked = d.resolve("masked_plain.json").exists()
        val hasTotals = d.resolve("totals.json").exists()
        val cand = readCurrent(id).candidateCount
        val status = when {
            hasTotals && reveals >= commits && commits > 0 -> "FINALIZED"
            hasTotals -> "REVEALED"
            hasMasked -> "MASKED_DECRYPTED"
            commits > 0 -> "MASKING"
            else -> "STARTED"
        }
        return mapOf("id" to id, "candidateCount" to cand, "commits" to commits, "reveals" to reveals, "status" to status)
    }
    fun zip(id: String): ByteArray {
        val d = dir(id)
        val bos = java.io.ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            Files.list(d).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .forEach {
                        zos.putNextEntry(ZipEntry(it.fileName.toString()))
                        Files.newInputStream(it).use { inp -> inp.copyTo(zos) }
                        zos.closeEntry()
                    }
            }
        }
        return bos.toByteArray()
    }
}

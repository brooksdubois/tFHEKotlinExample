package app.mpc

import app.voting.VotingUseCase
import kvm.mpc.StartSessionReq
import kvm.mpc.StartSessionRes
import kvm.mpc.startMpcSession
import kvm.native.EncPtr
import kvm.native.U16
import java.nio.file.Path
import java.util.Base64
import kvm.native.U16Server // adjust import to your JNI wrapper if named differently
import java.io.File

class MpcUseCase(
    private val mpcBaseDir: Path,
    private val voting: VotingUseCase = VotingUseCase()
) {
    fun start(req: StartSessionReq): StartSessionRes {
        // Defer fold to a lambda (core stays framework-agnostic)
        val fold = { foldFromLedger() }
        return startMpcSession(req, fold, mpcBaseDir)
    }

    /** Fold all stored votes into a per-candidate encrypted total vector (ctsB64). */
    fun foldFromLedger(): Pair<List<String>, Int> {
        val srv = U16Server.fromCompressed(File("public/u16_server_key.bin").readBytes())
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
}

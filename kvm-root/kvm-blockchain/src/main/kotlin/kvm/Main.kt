package kvm

import kvm.native.EncPtr
import kvm.native.ServerCtx
import kvm.native.U16
import kvm.native.U16Server

fun main() {
    // 1) Election keys for test casting
    val kp = U16.generateKeypair()

// 2) Export compressed server key and build server-only context (verifier)
    val csk = U16.exportCompressedServerKey(kp)      // bytes you can ship/store
    val srv = U16Server.fromCompressed(csk)          // no client key present

// 3) Cast a few ballots (here just numbers for the demo)
    val votes = listOf(1, 2, 3, 4, 5, 6).map { U16.encrypt(it, kp) }

    // 4) Fold-sum entirely with server-only context
    fun homSum(srv: ServerCtx, cts: List<EncPtr>): EncPtr =
        cts.reduce { acc, ct -> U16Server.add(srv, acc, ct) }

    val encTotal = homSum(srv, votes)

// 5) Decrypt totals with the original client key (trusted side)
    println("total = " + U16.decrypt(encTotal, kp)) // -> 21
}

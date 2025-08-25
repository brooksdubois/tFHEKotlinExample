package kvm.core

import kvm.instruction.KVEInstruction
import kvm.model.SimpleRecord
import kvm.native.Keypair   // typealias -> IntKeypair
import kvm.native.U16

class KVE {

    // Decrypts the one-hot vector and returns the selected candidate index (0..N-1),
    // or -1 if no "1" was found (shouldn't happen if ballots are well-formed).
    private fun decryptVoteIndex(record: SimpleRecord, key: Keypair): Int {
        var found = -1
        var hits = 0
        record.u16OneHot.forEachIndexed { i, bytes ->
            val v = U16.decrypt(U16.deserialize(bytes), key)
            if (v != 0) {
                found = i
                hits++
            }
        }
        require(hits <= 1) { "Invalid ballot: multiple 1s in one-hot vector" }
        return found
    }

    fun execute(instruction: KVEInstruction, record: SimpleRecord, key: Keypair): Boolean {
        return when (instruction) {
            is KVEInstruction.VoteEquals -> {
                // For backward compatibility: treat "vote != 0" as "chose a non-zero option"
                val actual = decryptVoteIndex(record, key) != 0
                println("🔎 VoteEquals: expected=${instruction.expected}, actual=$actual")
                actual == instruction.expected
            }
            is KVEInstruction.AddressEquals -> {
                val ok = (record.address == instruction.expected)
                println("🔎 AddressEquals: expected=${instruction.expected}, actual=${record.address}, ok=$ok")
                ok
            }
            is KVEInstruction.VoteEqualsInt -> {
                val actual = decryptVoteIndex(record, key)
                println("🔎 VoteEqualsInt: expected=${instruction.expected}, actual=$actual")
                actual == instruction.expected
            }
        }
    }

    fun validateWithContract(record: SimpleRecord, contract: List<KVEInstruction>, key: Keypair): Boolean {
        val result = contract.all { execute(it, record, key) }
        println("🔎 Record ${record.id} validation result: $result")
        return result
    }

    fun validateBatchWithContract(records: List<SimpleRecord>, contract: List<KVEInstruction>, key: Keypair): Boolean {
        return records.all { validateWithContract(it, contract, key) }
    }
}

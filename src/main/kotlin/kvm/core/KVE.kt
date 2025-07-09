package kvm.core

import kvm.encrypted.EncryptedInt
import kvm.model.SimpleRecord
import kvm.instruction.KVEInstruction

class KVE {

    // Original simple validation (still usable)
    fun validateRecord(record: SimpleRecord): Int {
        return record.vote.decrypt() // eventually encrypted contract logic
    }

    fun execute(instruction: KVEInstruction, record: SimpleRecord): Boolean {
        return when (instruction) {
            is KVEInstruction.VoteEquals -> {
                val actual = record.vote.decrypt() != 0
                println("🔎 Executing VoteEquals: expected=${instruction.expected}, actual=$actual")
                actual == instruction.expected
            }
            is KVEInstruction.AddressEquals -> {
                record.address == instruction.expected
            }
            is KVEInstruction.VoteEqualsInt -> {
                val actual = record.vote.decrypt()
                println("🔎 Executing VoteEqualsInt: expected=${instruction.expected}, actual=$actual")
                actual == instruction.expected
            }
        }
    }

    fun validateBatchWithContract(records: List<SimpleRecord>, contract: List<KVEInstruction>): Boolean {
        return records.all { validateWithContract(it, contract) }
    }

    fun validateWithContract(record: SimpleRecord, contract: List<KVEInstruction>): Boolean {
        val result = contract.all { execute(it, record) }
        println("🔎 Record ${record.id} validation result: $result")
        return result
    }
}

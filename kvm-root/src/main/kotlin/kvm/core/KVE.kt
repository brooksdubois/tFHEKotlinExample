package kvm.core

import kvm.encrypted.EncryptedInt
import kvm.model.SimpleRecord
import kvm.instruction.KVEInstruction

class KVE {

    // Original simple validation (still usable)
//    fun validateRecord(record: SimpleRecord): Long {
//        return record.age.greaterThan(17).decrypt()
//    }

    // New: execute a single instruction on a record
    fun execute(instruction: KVEInstruction, record: SimpleRecord): Long {
        return when (instruction) {
            is KVEInstruction.GreaterThan -> {
                when (instruction.field) {
                    "age" -> record.age.greaterThan(instruction.value).decrypt()
                    else -> false
                }
            }

            is KVEInstruction.Equals -> {
                when (instruction.field) {
                    "name" -> record.name == instruction.value
                    "address" -> record.address == instruction.value
                    else -> false
                }
            }
        } as Long
    }

//    fun validateBatch(records: List<SimpleRecord>): Boolean {
//        return records.all { validateRecord(it) }
//    }
//
//    fun validateBatchWithContract(records: List<SimpleRecord>, contract: List<KVEInstruction>): Boolean {
//        return records.all { validateWithContract(it, contract) }
//    }

//    fun tallyVotes(records: List<SimpleRecord>): EncryptedInt {
//        return records.map { it.vote }
//            .reduce { acc, vote -> acc.add(vote.decrypt()) } // simulate homomorphic sum
//    }

//    // New: evaluate a list of instructions as a "contract"
//    fun validateWithContract(record: SimpleRecord, instructions: List<KVEInstruction>): Boolean {
//        return instructions.all { execute(it, record) }
//    }
}

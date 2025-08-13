import kvm.instruction.KVEInstruction
import kvm.model.SimpleRecord
import kvm.native.Keypair

class KVE {

    fun execute(instruction: KVEInstruction, record: SimpleRecord, key: Keypair): Boolean {
        return when (instruction) {
            is KVEInstruction.VoteEquals -> {
                val actual = record.vote.decrypt(key) != 0
                println("🔎 Executing VoteEquals: expected=${instruction.expected}, actual=$actual")
                actual == instruction.expected
            }
            is KVEInstruction.AddressEquals -> {
                record.address == instruction.expected
            }
            is KVEInstruction.VoteEqualsInt -> {
                val actual = record.vote.decrypt(key)
                println("🔎 Executing VoteEqualsInt: expected=${instruction.expected}, actual=$actual")
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

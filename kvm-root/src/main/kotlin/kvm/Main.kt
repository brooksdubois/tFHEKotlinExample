package kvm

import kvm.core.Blockchain
import kvm.core.KVE
import kvm.encrypted.EncryptedInt
import kvm.instruction.KVEInstruction
import kvm.model.SimpleRecord
import java.time.Instant

fun main() {
    val blockchain = Blockchain()
    blockchain.mineGenesis()

    val now = Instant.now().epochSecond

    // Records: one eligible, one ineligible (based on age)
    val record1 = SimpleRecord(
        id = "abc123",
        name = "Alice Smith",
        address = "123 Main St",
        age = EncryptedInt(30),
        timestamp = now,
        vote = EncryptedInt(1)
    )

    val record2 = SimpleRecord(
        id = "def456",
        name = "Bob Johnson",
        address = "456 Elm St",
        age = EncryptedInt(19),
        timestamp = now,
        vote = EncryptedInt(1)
    )

    // Define a contract to validate eligibility
    val contract = listOf(
        KVEInstruction.GreaterThan("age", 18)
    )

    // Add the block using the contract validator
    blockchain.addBlock(listOf(record1, record2), contract)

    val kve = KVE()

    // Pull out all records from all blocks (flatMap)
    val allRecords = blockchain.getChain().flatMap { it.records }

    // Filter for contract-valid records only
    val eligibleRecords = allRecords.filter { kve.validateWithContract(it, contract) }

    println("Eligible voters:")
    eligibleRecords.forEach { println("  ${it.name}") }

    // Tally the encrypted votes from eligible voters
    val totalVotes = eligibleRecords
        .map { it.vote }
        .reduceOrNull { acc, vote -> acc.add(vote.decrypt()) }

    println("Encrypted vote tally (mock): ${totalVotes?.decrypt() ?: 0}")

    println("\nBlockchain contents:")
    blockchain.getChain().forEach { println(it) }
}

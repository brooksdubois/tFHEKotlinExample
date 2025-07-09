package kvm

import kvm.encrypted.EncryptedBool
import kvm.native.TfheBridge
import kvm.core.Blockchain
import kvm.encrypted.EncryptedInt
import kvm.instruction.KVEInstruction
import kvm.model.SimpleRecord
import java.time.Instant

fun main() {
    println("Library path: " + System.getProperty("java.library.path"))
    TfheBridge.init()

    val blockchain = Blockchain()
    blockchain.mineGenesis()

    val now = Instant.now().epochSecond

    val record1 = SimpleRecord(
        id = "abc123",
        name = "Alice",
        address = "123 Main St",
        age = 30,
        vote = EncryptedInt.fromInt(3),
        timestamp = now
    )

    val record2 = SimpleRecord(
        id = "def456",
        name = "Bob",
        address = "456 Elm St",
        age = 22,
        vote = EncryptedInt.fromInt(2),
        timestamp = now
    )

    val contract = listOf(
        KVEInstruction.VoteEquals(true)
    )

    try {
        val block = blockchain.addBlock(listOf(record1, record2), contract)
        println("✅ Block accepted with ${block.records.size} records")
    } catch (e: IllegalArgumentException) {
        println("❌ Block rejected: ${e.message}")
    }

    println("\nBlockchain contents:")
    blockchain.getChain().forEach { println(it) }

    val voteRange = 0..3
    val allVotes = blockchain.getChain().flatMap { it.records }.map { it.vote }

    val encryptedHistogram: Map<Int, EncryptedInt> = voteRange.associateWith { candidate ->
        allVotes.map { vote ->
            vote.equals(candidate).toInt()
        }.reduce { acc, bitAsInt -> acc.add(bitAsInt) }
    }

    println("\n📊 Homomorphic vote tally:")
    encryptedHistogram.forEach { (candidate, count) ->
        println("Candidate $candidate: ${count.decrypt()} votes")
    }
}


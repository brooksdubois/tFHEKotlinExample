package kvm

import java.io.File
import kvm.native.TfheBridge
import kvm.core.Blockchain
import kvm.encrypted.EncryptedInt
import kvm.encrypted.writeTallyToJson
import kvm.instruction.KVEInstruction
import kvm.model.SimpleRecord
import java.time.Instant

fun main() {
    println("Library path: " + System.getProperty("java.library.path"))
    TfheBridge.init()

    val clientKeyBytes = TfheBridge.exportClientKey()
    File("../verifier/client.key").writeBytes(clientKeyBytes)
    println("🔑 ClientKey exported to client.key")

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

    val encryptedVotes = blockchain.getChain()
        .flatMap { it.records }
        .map { it.vote }

    encryptedVotes.forEachIndexed { i, encVote ->
        val serialized = encVote.serialize()
        println("🗃️ Encrypted vote [$i]: ${serialized.joinToString { it.joinToString(",") }}")
    }

    val histogram: Map<Int, EncryptedInt> = (0..3).associateWith { candidate ->
        encryptedVotes.map { vote ->
            vote.equals(candidate).toInt()
        }.reduce { acc, e -> acc.add(e) }
    }

    histogram.forEach { (candidate, encCount) ->
        println("Candidate $candidate tally: 🔒 ${encCount.serialize()}")
    }

    writeTallyToJson(histogram, "../verifier/encrypted_tally.json")
    println("📤 Encrypted tally written to encrypted_tally.json")
}


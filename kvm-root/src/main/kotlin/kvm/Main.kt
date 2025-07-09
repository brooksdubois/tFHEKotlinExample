package kvm

import kvm.encrypted.EncryptedBool
import kvm.native.TfheBridge
import kvm.core.Blockchain
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
        vote = EncryptedBool.fromBoolean(true),
        timestamp = now
    )

    val record2 = SimpleRecord(
        id = "def456",
        name = "Bob",
        address = "456 Elm St",
        age = 22,
        vote = EncryptedBool.fromBoolean(false),
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
}


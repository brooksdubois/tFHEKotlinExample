package kvm

import kvm.core.Blockchain
import kvm.encrpted.EncryptedInt
import kvm.model.SimpleRecord
import java.time.Instant

//
//fun main() {
//    println("KVM starting up...")
//    val blockchain = Blockchain()
//    blockchain.mineGenesis()
//    println(blockchain)
//}

fun main() {
    val chain = Blockchain()
    chain.mineGenesis()

    val record = SimpleRecord(
        id = "abc123",
        name = "Alice Smith",
        address = "123 Main St",
        age = EncryptedInt(30),
        timestamp = Instant.now().epochSecond
    )

    chain.addBlock(listOf(record))

    println("Blockchain contents:")
    chain.getChain().forEach {
        println(it)
    }
}
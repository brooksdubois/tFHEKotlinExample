package kvm.core

import kvm.model.Block
import kvm.model.SimpleRecord
import java.time.Instant
import java.security.MessageDigest

class Blockchain {
    private val chain = mutableListOf<Block>()

    fun getChain(): List<Block> = chain.toList()

    fun getLatestBlock(): Block? = chain.lastOrNull()

    fun mineGenesis(): Block {
        require(chain.isEmpty()) { "Genesis block already exists" }
        val genesis = createBlock(index = 0, previousHash = "0", records = emptyList())
        chain.add(genesis)
        return genesis
    }

    fun addBlock(records: List<SimpleRecord>): Block {
        val kve = KVE()
        if (!kve.validateBatch(records)) {
            throw IllegalArgumentException("Record batch failed validation")
        }

        val previousBlock = getLatestBlock() ?: throw IllegalStateException("Genesis block must be mined first")
        val newBlock = createBlock(
            index = previousBlock.index + 1,
            previousHash = previousBlock.hash,
            records = records
        )
        chain.add(newBlock)
        return newBlock
    }

    private fun createBlock(index: Int, previousHash: String, records: List<SimpleRecord>): Block {
        val timestamp = Instant.now().epochSecond
        val hash = computeHash(index, previousHash, timestamp, records)
        return Block(index, previousHash, timestamp, records, hash)
    }

    private fun computeHash(index: Int, previousHash: String, timestamp: Long, records: List<SimpleRecord>): String {
        val input = "$index$previousHash$timestamp${records.joinToString()}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

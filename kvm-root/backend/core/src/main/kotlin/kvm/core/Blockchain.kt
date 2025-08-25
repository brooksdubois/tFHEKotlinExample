package kvm.core

import kvm.instruction.KVEInstruction
import kvm.model.Block
import kvm.model.SimpleRecord
import kvm.native.Keypair
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

    fun addBlock(records: List<SimpleRecord>, contract: List<KVEInstruction>, key: Keypair): Block {
        val kve = KVE()
        val validRecords = records.filter { kve.validateWithContract(it, contract, key) }

        val commitments = validRecords.map { it.commitment }
        if (commitments.size != commitments.toSet().size) {
            throw IllegalArgumentException("Duplicate commitments detected")
        }

        val existingCommitments = chain.flatMap { it.records }.map { it.commitment }.toSet()
        if (validRecords.any { it.commitment in existingCommitments }) {
            throw IllegalArgumentException("Duplicate vote detected (existing commitment)")
        }

        if (validRecords.isEmpty()) {
            throw IllegalArgumentException("No valid records to add")
        }

        val previousBlock = getLatestBlock() ?: throw IllegalStateException("Genesis block must be mined first")
        val newBlock = createBlock(
            index = previousBlock.index + 1,
            previousHash = previousBlock.hash,
            records = validRecords
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

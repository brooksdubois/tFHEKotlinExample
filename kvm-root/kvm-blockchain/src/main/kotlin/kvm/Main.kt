package kvm

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.Instant
import java.util.Base64
import kvm.core.Blockchain
import kvm.encrypted.EncryptedInt
import kvm.encrypted.writeTallyToJson
import kvm.instruction.KVEInstruction
import kvm.model.PublicUserVote
import kvm.model.SimpleRecord
import kvm.native.TfheBridge

data class Voter(val id: String, val name: String, val address: String, val age: Int, val voteValue: Int)

fun main() {
    println("Library path: " + System.getProperty("java.library.path"))

    // === 1. Global tally keypair (shared across all votes) ===
    TfheBridge.init()
    val globalClientKey = TfheBridge.exportClientKey()
    val globalCloudKey = TfheBridge.exportCloudKey()

    File("../verifier/global_client.key").writeBytes(globalClientKey)
    File("../verifier/global_cloud.key").writeBytes(globalCloudKey)
    println("🔑 Global client/cloud keys exported")

    val now = Instant.now().epochSecond

    // === 2. Define voters ===
    val voters = listOf(
        Voter("abc123", "Alice", "123 Main St", 30, 3),
        Voter("def456", "Bob", "456 Elm St", 22, 2),
        Voter("ghi789", "Carol", "789 Oak Ave", 28, 1)
    )

    val records = voters.map { voter ->
        // Generate per-user keypair
        TfheBridge.init()
        val userClientKey = TfheBridge.exportClientKey()
        val userCloudKey = TfheBridge.exportCloudKey()

        File("../verifier/${voter.id}_client.key").writeBytes(userClientKey)
        File("../verifier/${voter.id}_cloud.key").writeBytes(userCloudKey)

        // Encrypt user's vote under their key
        val userEncryptedVote = EncryptedInt.fromInt(voter.voteValue)

        // Re-import global key to encrypt for tallying
        TfheBridge.importClientKey(globalClientKey)
        TfheBridge.importCloudKey(globalCloudKey)
        val tallyEncryptedVote = EncryptedInt.fromInt(voter.voteValue)

        // Build SimpleRecord
        SimpleRecord(
            id = voter.id,
            name = voter.name,
            address = voter.address,
            age = voter.age,
            userEncryptedVote = userEncryptedVote,
            tallyEncryptedVote = tallyEncryptedVote,
            timestamp = now
        )
    }

    // === 3. Blockchain setup ===
    val blockchain = Blockchain()
    blockchain.mineGenesis()

    val contract = listOf(KVEInstruction.VoteEquals(true))

    try {
        val block = blockchain.addBlock(records, contract)
        println("✅ Block accepted with ${block.records.size} records")

        val allRecords = blockchain.getChain().flatMap { it.records }
        writeUserVotesToJson(allRecords, "../verifier/encrypted_user_votes.json")
        println("📤 User-encrypted votes written to encrypted_user_votes.json")
    } catch (e: IllegalArgumentException) {
        println("❌ Block rejected: ${e.message}")
    }

    println("\nBlockchain contents:")
    blockchain.getChain().forEach { println(it) }

    // === 4. Homomorphic tally ===
    val encryptedVotes = blockchain.getChain()
        .flatMap { it.records }
        .map { it.tallyEncryptedVote }

    encryptedVotes.forEachIndexed { i, encVote ->
        val serialized = encVote.serialize()
        println("🗃️ Tally Encrypted vote [$i]: ${serialized.joinToString { it.joinToString(",") }}")
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

fun writeUserVotesToJson(records: List<SimpleRecord>, outputFile: String) {
    val base64 = Base64.getEncoder()
    val votes = records.map { record ->
        PublicUserVote(
            id = record.id,
            name = record.name,
            userEncryptedVote = record.userEncryptedVote.serialize().map { base64.encodeToString(it) }
        )
    }

    val json = Json { prettyPrint = true }
    File(outputFile).writeText(json.encodeToString(votes))
}

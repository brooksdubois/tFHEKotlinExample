package kvm

import java.io.File
import java.time.Instant
import java.security.MessageDigest
import kvm.core.Blockchain
import kvm.encrypted.EncryptedInt
import kvm.encrypted.writeUserVotesJson
import kvm.model.SimpleRecord
import kvm.native.NativeLoader
import kvm.native.TfheBridge
import mpc.oneHot
import mpc.additiveShares
import mpc.appendShareRow

data class Voter(
    val id: String,
    val name: String,
    val address: String,
    val age: Int,
    val voteValue: Int
)

private const val NUM_CANDIDATES = 4
private const val NUM_PARTIES = 3

fun generateCommitment(secret: String, electionId: String): String {
    val message = "$secret|$electionId"
    val digest = MessageDigest.getInstance("SHA-256").digest(message.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

private fun resetMpcFiles(dir: File, parties: Int) {
    dir.mkdirs()
    for (pid in 0 until parties) {
        File(dir, "party_${pid}.jsonl").writeText("") // truncate
    }
}

fun main() {
    NativeLoader.load()
    println("java.library.path = " + System.getProperty("java.library.path"))
    println("TFHE_BRIDGE_PATH = " + System.getenv("TFHE_BRIDGE_PATH"))
    println("tfhe.bridge.path = " + System.getProperty("tfhe.bridge.path"))
    val now = Instant.now().epochSecond

    // === 1) Define voters (same as before) ===
    val voters = listOf(
        Voter("abc123", "Alice", "123 Main St", 30, 3),
        Voter("def456", "Bob", "456 Elm St", 22, 3),
        Voter("ghi789", "Carol", "789 Oak Ave", 28, 1),
    )

    // Directory for MPC party inputs (dev/local)
    val mpcDir = File("../verifier/mpc").also { it.mkdirs() }
    resetMpcFiles(mpcDir, NUM_PARTIES)
    // === 2) Build records ===
    val records = voters.map { voter ->
        // Per-user TFHE receipt keypair (user-only decrypt)
        val userKey = TfheBridge.generateKeypair()
        File("../verifier/${voter.id}_client.key").writeBytes(userKey.exportClientKey())

        // Per-user receipt ciphertext (so user can verify their own vote)
        val userEncrypted = EncryptedInt.fromInt(voter.voteValue, userKey)

        // MPC: one-hot encode vote and secret-share to parties
        val oneHotVote = oneHot(voter.voteValue, NUM_CANDIDATES)
        val shares = additiveShares(oneHotVote, NUM_PARTIES)
        shares.forEachIndexed { partyId, shareVec ->
            appendShareRow(mpcDir, partyId, voter.id, shareVec)
        }

        // Commitment as before
        val commitment = generateCommitment(voter.id, "election2025")

        // Store the receipt bytes in the record (tally uses MPC shares, not this field)
        SimpleRecord(
            id = voter.id,
            name = voter.name,
            address = voter.address,
            age = voter.age,
            vote = userEncrypted,                 // kept for user receipt & any local checks
            userEncryptedVote = userEncrypted.serialize(),
            timestamp = now,
            commitment = commitment
        )
    }

    // === 3) Blockchain & (optional) contract validation ===
    val blockchain = Blockchain()
    blockchain.mineGenesis()

    // If your KVE contract decrypts `record.vote`, it would need a matching key.
    // Since we're no longer re-encrypting to a global tally key, use an empty contract
    // (or switch your contract to non-decrypting predicates like AddressEquals, etc).
    val contract = emptyList<kvm.instruction.KVEInstruction>()

    // Dummy key (not used when contract is empty)
    val unusedKey = TfheBridge.generateKeypair()

    try {
        val block = blockchain.addBlock(records, contract, unusedKey)
        println("✅ Block accepted with ${block.records.size} records")
    } catch (e: IllegalArgumentException) {
        println("❌ Block rejected: ${e.message}")
    }

    println("\nBlockchain contents:")
    blockchain.getChain().forEach { println(it) }

    // === 4) Export per-user receipt file (public) ===
    val allRecords = blockchain.getChain().flatMap { it.records }
    writeUserVotesJson(allRecords, "../verifier/encrypted_user_votes.json")

    println("\n📤 Exports written:")
    println("    - verifier/mpc/party_*.jsonl      (MPC shares, one file per party)")
    println("    - encrypted_user_votes.json        (per-user receipts)")
    println("\nℹ️  Next: run the verifier’s MPC path to produce the public tally from shares.")
}

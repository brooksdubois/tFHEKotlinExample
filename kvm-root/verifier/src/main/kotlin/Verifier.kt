
import kotlinx.serialization.json.*
import java.io.File
import java.util.Base64
import kvm.native.TfheBridge
import kvm.model.PublicUserVote
import kvm.encrypted.EncryptedInt

fun decryptInt(bits: List<ByteArray>): Int =
    bits.mapIndexed { i, b -> if (TfheBridge.decryptSerialized(b)) 1 shl i else 0 }.sum()

fun main(args: Array<String>) {
    val userId = args.firstOrNull() ?: run {
        println("❌ Missing user ID. Usage: `VerifierKt <userId>`")
        return
    }

    val decoder = Base64.getDecoder()

    // Load votes
    val inputFile = File("encrypted_user_votes.json")
    val json = inputFile.readText()
    val votes: List<PublicUserVote> = Json.decodeFromString(json)

    // Load matching client.key
    val clientKeyFile = File("${userId}_client.key")
    if (!clientKeyFile.exists()) {
        println("❌ No client key found for ID $userId")
        return
    }
    val clientKeyBytes = clientKeyFile.readBytes()
    TfheBridge.importClientKey(clientKeyBytes)

    // Decrypt that user's vote
    val userVote = votes.find { it.id == userId }
    if (userVote == null) {
        println("❌ No vote found for user ID $userId")
        return
    }

    val decodedBits = userVote.userEncryptedVote.map { decoder.decode(it) }
    val decrypted = decryptInt(decodedBits)
    println("🔓 Your vote: Candidate $decrypted")

    // === Switch to global cloud key for tallying ===
    val globalCloudKeyBytes = File("global_cloud.key").readBytes()
    TfheBridge.importCloudKey(globalCloudKeyBytes)

    val reencryptedVotes = votes.map { vote ->
        val bits = vote.userEncryptedVote.map { decoder.decode(it) }
        val value = decryptInt(bits)
        EncryptedInt.fromInt(value)
    }

    // === Homomorphic tally ===
    val histogram: Map<Int, EncryptedInt> = (0..3).associateWith { candidate ->
        reencryptedVotes.map { it.equals(candidate).toInt() }.reduce { acc, e -> acc.add(e) }
    }

    println("\n📊 Public tally (computed from individual encrypted votes):")
    histogram.forEach { (candidate, encCount) ->
        val decryptedCount = encCount.decrypt()
        println("Candidate $candidate: $decryptedCount")
    }
}

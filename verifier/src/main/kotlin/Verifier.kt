import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.Base64
import kvm.native.TfheBridge // ✅ make sure this path matches your actual one

@Serializable
data class EncryptedTally(val candidate: Int, val bits: List<String>)

fun decryptBit(bytes: ByteArray): Boolean =
    TfheBridge.decryptSerialized(bytes) // ✅ this should exist in TfheBridge

fun decryptInt(bits: List<ByteArray>): Int =
    bits.mapIndexed { i, b -> if (decryptBit(b)) 1 shl i else 0 }.sum()

fun main() {
    // 🔐 Load the matching key
    val clientKeyBytes = File("client.key").readBytes()
    TfheBridge.importClientKey(clientKeyBytes)
    println("🔑 ClientKey loaded from client.key")

    // 📥 Load tally JSON
    val inputFile = File("encrypted_tally.json")
    val json = inputFile.readText()
    val tallies = Json.decodeFromString<List<EncryptedTally>>(json)

    val decoder = Base64.getDecoder()

    val decrypted = tallies.associate { entry ->
        val decodedBits = entry.bits.map { decoder.decode(it) }
        entry.candidate to decryptInt(decodedBits)
    }

    println("📊 Final vote tally:")
    decrypted.forEach { (candidate, count) ->
        println("Candidate $candidate: $count")
    }
}

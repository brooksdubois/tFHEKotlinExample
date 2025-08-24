import java.io.File
import java.util.Base64
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kvm.native.*

private typealias VotesJson = List<List<String>> // per-record one-hot u16 ciphertexts (b64)

private val json = Json { prettyPrint = true }

fun main(args: Array<String>) {
    val keyPath   = args.firstOrNull { it.startsWith("--csk=") }?.substringAfter("=")
        ?: "public/u16_server_key.bin"
    val votesPath = args.firstOrNull { it.startsWith("--votes=") }?.substringAfter("=")
        ?: "encrypted_user_votes.json" // grab from your existing dump

    val csk = File(keyPath).readBytes()
    val srv = U16Server.fromCompressed(csk)

    val votesJson = File(votesPath).readText()
    val votes: VotesJson = Json.decodeFromString(votesJson)
    require(votes.isNotEmpty()) { "No votes" }

    val dec = Base64.getDecoder()
    val cols = votes.first().size
    val perCol = Array(cols) { mutableListOf<EncPtr>() }
    votes.forEach { row ->
        require(row.size == cols) { "Inconsistent ballot width" }
        row.forEachIndexed { i, b64 ->
            perCol[i] += U16.deserialize(dec.decode(b64))
        }
    }

    fun sumCol(list: List<EncPtr>) = list.reduce { acc, ct -> U16Server.add(srv, acc, ct) }
    val encTotals = perCol.map(::sumCol)

    val outB64 = encTotals.map { Base64.getEncoder().encodeToString(U16.serialize(it)) }
    File("u16_tally_ciphertexts.json").writeText(json.encodeToString(outB64))
    println("Wrote encrypted totals -> u16_tally_ciphertexts.json")
}

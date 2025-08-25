import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kvm.native.EncPtr
import kvm.native.Keypair
import kvm.native.ServerCtx
import kvm.native.U16
import kvm.native.U16Server

// ------------------------
// JSON / DTOs
// ------------------------
private val json = Json { prettyPrint = true }

private typealias VotesJson = List<List<String>> // per-record one-hot u16 ciphertexts (b64)

@Serializable
data class RecordSummary(
    val id: String,
    val name: String,
    val address: String,
    val age: Int,
    val timestamp: Long,
    val commitment: String
)

@Serializable
data class BlockSummary(
    val index: Int,
    val recordCount: Int,
    val records: List<RecordSummary>
)

@Serializable
data class ReceiptOut(
    val candidate: Int,
    val ciphertextB64: String,
    val clientKeyPath: String
)

@Serializable
data class VerifiedVote(
    val index: Int,
    val candidate: Int,
    val id: String? = null,
    val commitment: String? = null,
    val timestamp: Long? = null
)

// ------------------------
// Helpers
// ------------------------
private fun b64ToCt(b64: String): EncPtr =
    U16.deserialize(Base64.getDecoder().decode(b64))

private fun ctToB64(ct: EncPtr): String =
    Base64.getEncoder().encodeToString(U16.serialize(ct))

private fun readVotes(path: String): VotesJson =
    json.decodeFromString(File(path).readText())

private fun readBlocksOrNull(path: String?): List<RecordSummary>? =
    path?.let { p ->
        val blocks = json.decodeFromString<List<BlockSummary>>(File(p).readText())
        blocks.flatMap { it.records }
    }

private fun autoFindClientKey(): String? {
    // Fast checks
    val candidates = listOf(
        "public/u16_client_key.bin",
        "verifier/u16_client_key.bin",
        "u16_client_key.bin"
    )
    candidates.firstOrNull { File(it).exists() }?.let { return it }

    // Shallow scan repo root
    return Files.walk(Path.of("."))
        .limit(3_000)
        .filter { Files.isRegularFile(it) }
        .map { it.fileName.toString() to it }
        .filter { (name, _) ->
            name.contains("client", ignoreCase = true) &&
                    name.endsWith(".bin", ignoreCase = true)
        }
        .map { (_, p) -> p.toString() }
        .findFirst().orElse(null)
}

private fun sumEncryptedColumn(srv: ServerCtx, cts: List<EncPtr>): EncPtr =
    cts.reduce { acc, ct -> U16Server.add(srv, acc, ct) }

// ------------------------
// Commands
// ------------------------
private fun cmdFold(args: List<String>) {
    val serverKeyPath = args.firstOrNull { it.startsWith("--server-key=") }?.substringAfter("=")
        ?: "public/u16_server_key.bin"
    val votesPath = args.firstOrNull { it.startsWith("--votes=") }?.substringAfter("=")
        ?: "verifier/encrypted_user_votes.json"
    val outPath = args.firstOrNull { it.startsWith("--out=") }?.substringAfter("=")
        ?: "u16_tally_ciphertexts.json"
    val blocksPath = args.firstOrNull { it.startsWith("--blocks=") }?.substringAfter("=")
    val verifyId = args.firstOrNull { it.startsWith("--id=") }?.substringAfter("=")

    // 1) Load server key + votes
    val srv = U16Server.fromCompressed(File(serverKeyPath).readBytes())
    val votes = readVotes(votesPath)
    require(votes.isNotEmpty()) { "No votes found in $votesPath" }

    // 2) Rebuild columns and fold
    val cols = votes.first().size
    require(votes.all { it.size == cols }) { "Inconsistent ballot widths in $votesPath" }

    val perCol = Array(cols) { mutableListOf<EncPtr>() }
    votes.forEach { row ->
        row.forEachIndexed { i, b64 -> perCol[i] += b64ToCt(b64) }
    }
    val encTotals = perCol.map { sumEncryptedColumn(srv, it) }

    // 3) Write encrypted totals (for later MPC decryption)
    val outB64 = encTotals.map(::ctToB64)
    File(outPath).writeText(json.encodeToString(outB64))
    println("Wrote encrypted totals -> $outPath")

    // 4) Dev-only plaintext reveal if a client key is available
    val clientKeyPath = args.firstOrNull { it.startsWith("--client-key=") }?.substringAfter("=")
        ?: autoFindClientKey()

    if (clientKeyPath == null) {
        println("No client key found automatically; skipping plaintext tallies.")
        return
    }

    val ck = U16.importClientKey(File(clientKeyPath).readBytes())
    println("\nPlaintext tally (dev-only):")
    encTotals.forEachIndexed { i, ct -> println("  Candidate $i: ${U16.decrypt(ct, ck)}") }

    // Optional: verify a single record's one-hot (if blocks provided)
    val flat = readBlocksOrNull(blocksPath)
    val indexToVerify: Int? = when {
        verifyId != null && flat != null -> {
            val ix = flat.indexOfFirst { it.id == verifyId }
            if (ix < 0) error("Record id '$verifyId' not found in $blocksPath") else ix
        }
        verifyId == null -> votes.size - 1 // default: most recent row
        else -> null
    }

    if (indexToVerify != null) {
        val row = votes[indexToVerify].map(::b64ToCt)
        val decRow = row.map { U16.decrypt(it, ck) }
        val chosen = decRow.indexOfFirst { it == 1 }
        require(chosen >= 0) { "Row $indexToVerify is not a valid one-hot vector" }

        val rec = flat?.getOrNull(indexToVerify)
        val label = rec?.let { "id=${it.id}, commitment=${it.commitment}" } ?: "row=$indexToVerify"
        println("\nVerified: $label → candidate $chosen")

        val payload = VerifiedVote(
            index = indexToVerify,
            candidate = chosen,
            id = rec?.id,
            commitment = rec?.commitment,
            timestamp = rec?.timestamp
        )
        File("verified_vote.json").writeText(json.encodeToString(payload))
        println("Wrote verified_vote.json")
    }
}

private fun cmdReceiptGen(args: List<String>) {
    val candidate = args.firstOrNull { it.startsWith("--candidate=") }?.substringAfter("=")?.toIntOrNull()
        ?: error("receipt-gen requires --candidate=N")
    require(candidate >= 0) { "candidate must be >= 0" }
    val out = args.firstOrNull { it.startsWith("--out=") }?.substringAfter("=") ?: "receipt.json"

    // Generate a fresh per-voter keypair; use it only for the receipt.
    val kp: Keypair = U16.generateKeypair()
    val ct: EncPtr = U16.encrypt(candidate, kp)
    val ctB64 = ctToB64(ct)

    File("receipts").mkdirs()
    val keyPath = "receipts/${System.currentTimeMillis()}_u16_client_key.bin"
    File(keyPath).writeBytes(U16.exportClientKey(kp))

    val payload = ReceiptOut(candidate = candidate, ciphertextB64 = ctB64, clientKeyPath = keyPath)
    File(out).writeText(json.encodeToString(payload))

    println("Wrote receipt JSON -> $out")
    println("Per-voter client key -> $keyPath (keep private)")
}

private fun cmdReceiptDecrypt(args: List<String>) {
    val keyPath = args.firstOrNull { it.startsWith("--key=") }?.substringAfter("=")
        ?: error("receipt-decrypt requires --key=PATH")
    val ctB64 = args.firstOrNull { it.startsWith("--ct=") }?.substringAfter("=")
        ?: error("receipt-decrypt requires --ct=BASE64")

    val ck = U16.importClientKey(File(keyPath).readBytes())
    val ct = b64ToCt(ctB64)
    val p = U16.decrypt(ct, ck)
    println("Decrypted receipt → candidate $p")
}

// ------------------------
// Main
// ------------------------
fun main(rawArgs: Array<String>) {
    val args = rawArgs.toList()
    when (args.firstOrNull()) {
        null, "fold" -> cmdFold(args.drop(1))
        "receipt-gen" -> cmdReceiptGen(args.drop(1))
        "receipt-decrypt" -> cmdReceiptDecrypt(args.drop(1))
        else -> {
            println(
                """
                Usage:
                  # Fold encrypted votes and write encrypted totals
                  verifier fold [--server-key=public/u16_server_key.bin] [--votes=verifier/encrypted_user_votes.json] [--out=u16_tally_ciphertexts.json] [--client-key=PATH] [--blocks=verifier/blocks.json] [--id=<recordId>]

                  # Generate a per-voter receipt under a fresh private key
                  verifier receipt-gen --candidate=N [--out=receipt.json]

                  # Decrypt a stored receipt with your private key
                  verifier receipt-decrypt --key=receipts/...bin --ct=<B64>
                """.trimIndent()
            )
        }
    }
}

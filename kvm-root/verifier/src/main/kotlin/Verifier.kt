import java.io.File
import java.util.Base64
import kotlinx.serialization.json.Json
import kvm.encrypted.EncryptedUserVoteRecord
import kvm.native.Keypair
import kvm.native.TfheBridge
import mpc.localAggregate

private const val NUM_PARTIES = 3
private const val NUM_CANDIDATES = 4

private fun decryptInt(bits: List<ByteArray>, key: Keypair): Int {
    var out = 0
    bits.forEachIndexed { i, b ->
        if (TfheBridge.decryptSerialized(b, key)) out = out or (1 shl i)
    }
    return out
}

private fun usage(): Nothing {
    println("Usage: VerifierKt <userId> [--mpc-dir=PATH]")
    println("  - expects encrypted_user_votes.json and <userId>_client.key in CWD")
    println("  - MPC shares default to ./mpc; override with --mpc-dir or MPC_DIR env")
    kotlin.system.exitProcess(1)
}

fun main(args: Array<String>) {
    if (args.isEmpty()) usage()
    val userId = args[0]
    val mpcDirArg = args.firstOrNull { it.startsWith("--mpc-dir=") }?.substringAfter("=")
    val mpcDirPath = mpcDirArg ?: System.getenv("MPC_DIR") ?: "mpc"

    val json = Json { ignoreUnknownKeys = true }
    val b64 = Base64.getDecoder()

    // 1) Load user receipt bundle and decrypt user's vote
    val userVoteFile = File("encrypted_user_votes.json")
    require(userVoteFile.exists()) {
        "Could not find encrypted_user_votes.json in ${File(".").absolutePath}"
    }
    val userVotes: List<EncryptedUserVoteRecord> = json.decodeFromString(userVoteFile.readText())
    val userRecord = userVotes.find { it.id == userId }
        ?: error("No vote found for user ID $userId")

    val keyFile = File("${userId}_client.key")
    require(keyFile.exists()) { "Missing ${userId}_client.key in ${File(".").absolutePath}" }
    val userKey = TfheBridge.importClientKey(keyFile.readBytes())

    val userBits = userRecord.bits.map { b64.decode(it) }
    val myVote = decryptInt(userBits, userKey)
    println("🔓 Your vote: Candidate $myVote")

    // 2) MPC tally (dev): sum secret shares from party files
    val mpcDir = File(mpcDirPath)
    require(mpcDir.exists()) { "MPC dir not found: ${mpcDir.absolutePath}" }

    // small sanity print to catch double-appends early
    val perPartyCounts = (0 until NUM_PARTIES).map { pid ->
        val f = File(mpcDir, "party_${pid}.jsonl")
        if (!f.exists()) 0 else f.useLines { it.count() }
    }
    println("ℹ️  share rows per party = $perPartyCounts")

    val totals = localAggregate(mpcDir, parties = NUM_PARTIES, candidates = NUM_CANDIDATES)
    println("\n📊 MPC Tally Result:")
    totals.forEachIndexed { i, c -> println("Candidate $i: $c") }
}

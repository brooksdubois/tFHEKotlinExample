package kvm.mpc

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.io.path.writeBytes
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { prettyPrint = true }

fun startMpcSession(
    req: StartSessionReq,
    foldFromLedger: () -> Pair<List<String>, Int>,
    baseDir: Path
): StartSessionRes {
    val (cts, n) = when (req.source) {
        MpcSource.live   -> foldFromLedger()
        MpcSource.upload -> {
            val vec = requireNotNull(req.ctsB64) { "ctsB64 required for source=upload" }
            require(vec.isNotEmpty()) { "ctsB64 cannot be empty" }
            vec to vec.size
        }
    }

    val id  = "mpc-" + UUID.randomUUID().toString().replace("-", "").take(12)
    val dir = baseDir.resolve(id).also { Files.createDirectories(it) }

    // ----- initial_cts.json (typed)
    val initialCtsObj = InitialCtsJson(ctsB64 = cts, candidateCount = n)
    val initialCtsBytes = json.encodeToString(initialCtsObj).encodeToByteArray()
    val initialCtsPath = dir.resolve("initial_cts.json")
    initialCtsPath.writeBytes(initialCtsBytes)

    // ----- MANIFEST.json (typed)
    val manifestObj = ManifestJson(
        id = id,
        createdAt = Instant.now().toString(),
        source = req.source.name,
        candidateCount = n,
        initialCtsSha256 = sha256Hex(initialCtsBytes)
    )
    dir.resolve("MANIFEST.json")
        .writeBytes(json.encodeToString(manifestObj).encodeToByteArray())

    return StartSessionRes(
        id = id,
        candidateCount = n,
        artifacts = listOf("$id/initial_cts.json", "$id/MANIFEST.json")
    )
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream
import kotlin.random.Random

// ---------- Config ----------
val BASE = System.getenv("MPC_URL") /*?: (args.getOrNull(0) */?:"http://localhost:8080"//)
val WHO  = System.getenv("MPC_WHO") /*?: (args.getOrNull(1) */?: "verifier-1"//)
val SEED = System.getenv("MPC_SEED")?.toLongOrNull() ?: 1337L
val SOURCE = System.getenv("MPC_SOURCE") ?: "live" // "live" or "upload"

// ---------- HTTP client ----------
val json = Json { ignoreUnknownKeys = true; prettyPrint = true; explicitNulls = false }
val client = HttpClient(Java) { install(ContentNegotiation) { json(json) } }

@Serializable data class StartReq(val source: String, val ctsB64: List<String>? = null)
@Serializable data class StartRes(val id: String, val candidateCount: Int, val artifacts: List<String> = emptyList())
@Serializable data class MaskServerReq(val who: String, val seed: Long? = null)
@Serializable data class Commit(val seq: Int, val who: String, val masksHashHex: String)
@Serializable data class MaskOut(val commit: Commit)
@Serializable data class DecryptReq(val clientKeyB64: String? = null, val clientKeyPath: String? = null)
@Serializable data class RevealReq(val who: String, val masks: List<Int>)
@Serializable data class RevealOut(val totals: List<Int>, val finalized: Boolean = false)
@Serializable data class VoteIn(val id: String, val candidate: Int, val name: String = "", val address: String = "", val age: Int = 0, val receiptCtB64: String? = null)
@Serializable data class VoteOut(val ok: Boolean, val candidate: Int, val recordId: String)

// ---------- Helpers ----------
suspend inline fun <reified Req : Any, reified Res : Any> postJson(url: String, body: Req): Res {
    val r = client.post(url) { contentType(ContentType.Application.Json); setBody(body) }
    if (!r.status.isSuccess()) error("POST $url failed: ${r.status} ${r.bodyAsText()}")
    return r.body()
}
suspend inline fun <reified Res : Any> postJson(url: String): Res {
    val r = client.post(url) { contentType(ContentType.Application.Json) }
    if (!r.status.isSuccess()) error("POST $url failed: ${r.status} ${r.bodyAsText()}")
    return r.body()
}

suspend fun getBytes(url: String): ByteArray {
    val r = client.get(url)
    if (!r.status.isSuccess()) error("GET $url failed: ${r.status} ${r.bodyAsText()}")
    return r.body<ByteArray>()   // ✅ idiomatic in Ktor 2.x
}
// Extract masks from ZIP entry named "masks_<WHO>.json"
fun extractMasksFromZip(zipBytes: ByteArray, who: String = WHO): List<Int> {
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
        var e = zis.nextEntry
        while (e != null) {
            if (!e.isDirectory && e.name.matches(Regex("""masks_${Regex.escape(who)}\.json$"""))) {
                val buf = zis.readAllBytes().decodeToString()
                return json.decodeFromString(buf)
            }
            e = zis.nextEntry
        }
    }
    error("masks_${who}.json not found in ZIP")
}


//---- vote generation helpers ----
private val FIRST = arrayOf(
    "Ava","Liam","Noah","Olivia","Mia","Ethan","Sophia","Lucas","Isabella","Mason",
    "Amelia","James","Emma","Henry","Harper","Elijah","Charlotte","Benjamin","Evelyn","Jack"
)
private val LAST = arrayOf(
    "Smith","Johnson","Williams","Brown","Jones","Garcia","Miller","Davis","Rodriguez","Martinez",
    "Hernandez","Lopez","Gonzalez","Wilson","Anderson","Thomas","Taylor","Moore","Jackson","Martin",
    "Nguyen","Kim","Patel","Singh","Lee"
)

private val STREETS = arrayOf("Maple","Oak","Pine","Cedar","Elm","Birch","Willow","Walnut","Cherry","Ash","River","Hill","Lake","Sunset","Highland")
private val TYPES = arrayOf("St","Ave","Rd","Blvd","Ln","Dr","Ct","Pl","Ter","Way")
private val CITIES = arrayOf("Norwalk","Bridgeport","Detroit","Ann Arbor","Stamford","Grand Rapids","Cleveland","New Haven","Toledo","Rochester")
private val STATES = arrayOf("CT","MI","NY","MA","PA","OH","NJ","IL","RI","NH")

private fun randomName(r: Random) = "${FIRST.random(r)} ${LAST.random(r)}"
private fun randomAddress(r: Random): String {
    val num = r.nextInt(100, 9999)
    val street = STREETS.random(r)
    val type = TYPES.random(r)
    val city = CITIES.random(r)
    val state = STATES.random(r)
    val zip = "%05d".format(r.nextInt(10000, 99999))
    return "$num $street $type, $city, $state $zip"
}
private fun randomAge(r: Random) = r.nextInt(18, 83)
suspend fun castVote(id: String, candidate: Int, name: String, address: String, age: Int): VoteOut =
    postJson("$BASE/vote", VoteIn(id = id, candidate = candidate, name = name, address = address, age = age))

suspend fun castRandomVotes(count: Int, candidates: Int, seed: Long): IntArray {
    require(candidates > 0) { "candidates must be > 0" }
    val rnd = Random(seed)
    val hist = IntArray(candidates)
    repeat(count) { i ->
        val c = rnd.nextInt(candidates)
        val name = randomName(rnd)
        val addr = randomAddress(rnd)
        val age  = randomAge(rnd)
        val uid  = "u${seed}-${i}"  // deterministic and readable

        val out = castVote(uid, c, name, addr, age)
        hist[c]++
        println("• cast vote #${i + 1}: candidate=$c id=${out.recordId} ($name; $age; $addr)")
    }
    return hist
}

suspend fun runE2E() {
    println("\n=== MPC E2E (base=$BASE, who=$WHO, source=$SOURCE, seed=$SEED) ===")

    val votesToCast   = (System.getenv("MPC_VOTE_COUNT") ?: "5").toInt()
    val candidateCount= (System.getenv("MPC_CANDIDATES") ?: "4").toInt()
    val voteSeed      = System.getenv("MPC_VOTE_SEED")?.toLongOrNull() ?: (SEED + 1000)

    if (SOURCE == "live" && votesToCast > 0) {
        println("Seeding $votesToCast random vote(s) across $candidateCount candidate(s) (seed=$voteSeed)…")
        val hist = castRandomVotes(votesToCast, candidateCount, voteSeed)
        println("Expected increment from seeded votes: ${hist.toList()}")
    }
    val startRes: StartRes = postJson("$BASE/mpc/sessions/start", StartReq(source = SOURCE))
    val id = startRes.id
    println("• started session: $id")

    val maskOut: MaskOut = postJson("$BASE/mpc/sessions/$id/mask:server", MaskServerReq(WHO, SEED))
    println("• masked (seq=${maskOut.commit.seq}, who=${maskOut.commit.who}, hash=${maskOut.commit.masksHashHex.take(12)}…)")

    val decryptResp: JsonElement = postJson("$BASE/mpc/sessions/$id/decrypt", DecryptReq())
    println("• decrypted masked totals (resp size=${decryptResp.toString().length})")

    val zip = getBytes("$BASE/mpc/sessions/$id/zip")
    val masks = extractMasksFromZip(zip, WHO)
    println("• extracted ${masks.size} masks from ZIP")

    val revealOut: RevealOut = postJson("$BASE/mpc/sessions/$id/reveal", RevealReq(WHO, masks))
    println("• reveal → totals=${revealOut.totals} finalized=${revealOut.finalized}")

    println("✓ done.\n")
}

// ---------- Entry ----------
suspend fun main() {
    try { runE2E() } finally { client.close() }
}

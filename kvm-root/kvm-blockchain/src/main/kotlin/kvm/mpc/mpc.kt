package mpc

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

@Serializable
data class ShareRow(val voterId: String, val share: List<Int>)

/**
 * Simple one-hot generator for C candidates.
 */
fun oneHot(vote: Int, candidates: Int): IntArray =
    IntArray(candidates) { if (it == vote) 1 else 0 }

/**
 * Additive secret sharing over Z (dev mode). For prod, switch to mod prime/ring.
 * Returns P lists (one per party), each list has the share vector for this one vote.
 */
fun additiveShares(vec: IntArray, parties: Int, rnd: Random = Random.Default): List<IntArray> {
    val pShares = Array(parties) { IntArray(vec.size) }
    for (i in vec.indices) {
        var sum = 0
        for (p in 0 until parties - 1) {
            val s = rnd.nextInt(-1_000_000, 1_000_000)
            pShares[p][i] = s
            sum += s
        }
        pShares[parties - 1][i] = vec[i] - sum
    }
    return pShares.toList()
}

/**
 * Append JSONL share rows for a given party.
 * Each line is ShareRow(voterId, shareVector)
 */
fun appendShareRow(dir: File, partyId: Int, voterId: String, share: IntArray) {
    dir.mkdirs()
    val f = File(dir, "party_$partyId.jsonl")
    val row = ShareRow(voterId, share.toList())
    f.appendText(Json.encodeToString(row) + "\n")
}

/**
 * Local dev "MPC": load each party's JSONL and sum shares coordinate-wise.
 * This simulates the revealed output of an MPC secure-sum.
 */
fun localAggregate(partiesDir: File, parties: Int, candidates: Int): IntArray {
    val totals = IntArray(candidates)
    val json = Json { ignoreUnknownKeys = true }
    val perParty = (0 until parties).map { pid ->
        File(partiesDir, "party_$pid.jsonl").readLines().map { json.decodeFromString<ShareRow>(it) }
    }
    // assume identical voter ordering across files (we control the writes)
    perParty[0].indices.forEach { idx ->
        val cols = IntArray(candidates)
        for (pid in 0 until parties) {
            val v = perParty[pid][idx].share
            for (i in 0 until candidates) cols[i] += v[i]
        }
        for (i in 0 until candidates) totals[i] += cols[i]
    }
    return totals
}

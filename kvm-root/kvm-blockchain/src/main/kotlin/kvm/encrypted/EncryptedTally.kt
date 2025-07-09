package kvm.encrypted

import java.io.File
import java.util.Base64
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class EncryptedTally(
    val candidate: Int,
    val bits: List<String> // base64-encoded
)

fun writeTallyToJson(histogram: Map<Int, EncryptedInt>, outputFile: String) {
    val base64 = Base64.getEncoder()

    val entries = histogram.map { (candidate, encInt) ->
        val bitStrings = encInt.serialize().map { base64.encodeToString(it) }
        EncryptedTally(candidate, bitStrings)
    }

    val json = Json { prettyPrint = true }
    File(outputFile).writeText(json.encodeToString(entries))
}

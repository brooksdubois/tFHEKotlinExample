package kvm.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

@Serializable
data class PublicUserVote(
    val id: String,
    val name: String,
    val userEncryptedVote: List<String> // base64-encoded bits
)

fun writeUserVotesToJson(records: List<SimpleRecord>, outputFile: String) {
    val base64 = Base64.getEncoder()
    val votes = records.map { record ->
        PublicUserVote(
            id = record.id,
            name = record.name,
            userEncryptedVote = record.userEncryptedVote.serialize().map { base64.encodeToString(it) }
        )
    }

    val json = Json { prettyPrint = true }
    File(outputFile).writeText(json.encodeToString(votes))
}

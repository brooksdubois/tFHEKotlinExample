package kvm.encrypted

import java.io.File
import java.util.Base64
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kvm.model.SimpleRecord

@Serializable
data class EncryptedTallyRecord(
    val id: String,
    val commitment: String,
    val bits: List<String> // base64
)

@Serializable
data class EncryptedUserVoteRecord(
    val id: String,
    val commitment: String,
    val bits: List<String> // base64
)

fun writeTallyVotesJson(records: List<SimpleRecord>, outputFile: String) {
    val base64 = Base64.getEncoder()

    val entries = records.map { record ->
        val serializedBits = record.vote.serialize().map { base64.encodeToString(it) }
        EncryptedTallyRecord(
            id = record.id,
            commitment = record.commitment,
            bits = serializedBits
        )
    }

    File(outputFile).writeText(Json { prettyPrint = true }.encodeToString(entries))
}

fun writeUserVotesJson(records: List<SimpleRecord>, outputFile: String) {
    val base64 = Base64.getEncoder()

    val entries = records.map { record ->
        val serializedBits = record.userEncryptedVote.map { base64.encodeToString(it) }
        EncryptedUserVoteRecord(
            id = record.id,
            commitment = record.commitment,
            bits = serializedBits
        )
    }

    File(outputFile).writeText(Json { prettyPrint = true }.encodeToString(entries))
}

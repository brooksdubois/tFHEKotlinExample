#!/usr/bin/env kotlin
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.Base64
import java.io.File

@Serializable
data class EncryptedTally(
    val candidate: Int,
    val bits: List<String>
)

val jsonFile = File("encrypted_tally.json")
val json = jsonFile.readText()
val tallies = Json.decodeFromString<List<EncryptedTally>>(json)

val base64 = Base64.getDecoder()
val decodedTallies = tallies.associate { tally ->
    tally.candidate to tally.bits.map { base64.decode(it) }
}

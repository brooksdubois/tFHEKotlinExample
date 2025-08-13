package kvm.model

import kvm.encrypted.EncryptedInt

data class SimpleRecord(
    val id: String,
    val name: String,
    val address: String,
    val age: Int,
    val vote: EncryptedInt,                   // for tally (global key)
    val userEncryptedVote: List<ByteArray>,   // raw bytes from per-user encryption
    val timestamp: Long,
    val commitment: String
)
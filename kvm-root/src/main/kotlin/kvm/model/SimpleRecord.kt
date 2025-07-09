package kvm.model

import kvm.encrypted.EncryptedInt

data class SimpleRecord(
    val id: String,
    val name: String,
    val address: String,
    val age: EncryptedInt,
    val timestamp: Long,
    val vote: EncryptedInt,
)
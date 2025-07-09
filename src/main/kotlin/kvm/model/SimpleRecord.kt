package kvm.model

import kvm.encrypted.EncryptedBool

data class SimpleRecord(
    val id: String,
    val name: String,
    val address: String,
    val age: Int, // leave as is for now
    val vote: EncryptedBool,
    val timestamp: Long
)
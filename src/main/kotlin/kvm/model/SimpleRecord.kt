package kvm.model

import kvm.encrpted.EncryptedInt

data class SimpleRecord(
    val id: String,
    val name: String,
    val address: String,
    //val age: Int,
    val age: EncryptedInt,
    val timestamp: Long
)
package kvm.model

import kvm.encrypted.EncryptedInt

data class SimpleRecord(
    val id: String,
    val name: String,
    val address: String,
    val age: Int,
    val userEncryptedVote: EncryptedInt,   // private to user
    val tallyEncryptedVote: EncryptedInt,  // public for homomorphic tally
    val timestamp: Long
)
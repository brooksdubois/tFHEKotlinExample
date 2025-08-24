package kvm.model

data class SimpleRecord(
    val id: String,
    val name: String,
    val address: String,
    val age: Int,
    val timestamp: Long,
    val commitment: String,
    val u16OneHot: List<ByteArray> //per-candidate u16 one-hot ciphertexts (serialized bytes)
)
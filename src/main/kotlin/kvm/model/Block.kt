package kvm.model

data class Block(
    val index: Int,
    val previousHash: String,
    val timestamp: Long,
    val records: List<SimpleRecord>,
    val hash: String
)
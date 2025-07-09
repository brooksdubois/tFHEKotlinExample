package kvm.instruction

sealed class KVEInstruction {
    data class VoteEquals(val expected: Boolean) : KVEInstruction()
    data class AddressEquals(val expected: String) : KVEInstruction()
    // later: GreaterThanEncrypted(val field: String, val value: Int)
}
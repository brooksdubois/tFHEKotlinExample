package kvm.instruction

sealed class KVEInstruction {
    data class VoteEquals(val expected: Boolean) : KVEInstruction()
    data class AddressEquals(val expected: String) : KVEInstruction()
    data class VoteEqualsInt(val expected: Int) : KVEInstruction()
}
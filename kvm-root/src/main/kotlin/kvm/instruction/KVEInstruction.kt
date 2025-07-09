package kvm.instruction

sealed class KVEInstruction {
    data class GreaterThan(val field: String, val value: Long) : KVEInstruction()
    data class Equals(val field: String, val value: String) : KVEInstruction()
}
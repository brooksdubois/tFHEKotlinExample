package kvm.core

sealed class KVEInstruction {
    data class GreaterThan(val field: String, val value: Int) : KVEInstruction()
    data class Equals(val field: String, val value: String) : KVEInstruction()
}
package kvm.encrypted

class EncryptedBool(private val value: Boolean) {
    fun decrypt(): Boolean = value
    override fun toString(): String = "🔒($value)"
}
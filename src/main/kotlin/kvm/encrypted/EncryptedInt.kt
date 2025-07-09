package kvm.encrypted

class EncryptedInt(private val enc: EncryptedBool) {
    fun add(other: EncryptedInt): EncryptedInt =
        EncryptedInt(enc.or(other.enc)) // boolean addition (OR for now, placeholder)

    fun greaterThan(value: Int): EncryptedBool {
        return when (value) {
            0 -> enc // 1 > 0 → true, 0 > 0 → false
            else -> EncryptedBool.fromBoolean(false)
        }
    }

    fun decrypt(): Int = if (enc.decrypt()) 1 else 0

    override fun toString(): String = "🔒(${decrypt()})"

    companion object {
        fun fromInt(value: Int): EncryptedInt =
            EncryptedInt(EncryptedBool.fromBoolean(value != 0))
    }
}

package kvm.encrpted

class EncryptedInt(private val plaintext: Int) {
    fun greaterThan(value: Int): EncryptedBool {
        return EncryptedBool(plaintext > value)
    }

    fun lessThan(value: Int): EncryptedBool {
        return EncryptedBool(plaintext < value)
    }

    fun add(value: Int): EncryptedInt {
        return EncryptedInt(plaintext + value)
    }

    fun decrypt(): Int = plaintext

    override fun toString(): String = "🔒(${plaintext})" // good for debug

}
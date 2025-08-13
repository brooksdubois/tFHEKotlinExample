package kvm.encrypted

import kvm.native.EncPtr
import kvm.native.Keypair
import kvm.native.TfheBridge

@JvmInline
value class EncryptedBool(val ptr: EncPtr) {

    fun not(key: Keypair): EncryptedBool =
        EncryptedBool(TfheBridge.not(ptr, key))

    fun and(other: EncryptedBool, key: Keypair): EncryptedBool =
        EncryptedBool(TfheBridge.and(ptr, other.ptr, key))

    fun or(other: EncryptedBool, key: Keypair): EncryptedBool =
        EncryptedBool(TfheBridge.or(ptr, other.ptr, key))

    fun xor(other: EncryptedBool, key: Keypair): EncryptedBool =
        EncryptedBool(TfheBridge.xor(ptr, other.ptr, key))

    fun decrypt(key: Keypair): Boolean =
        TfheBridge.decrypt(ptr, key)

    fun toInt(key: Keypair): EncryptedInt =
        EncryptedInt(listOf(this) + List(7) { fromBoolean(false, key) })

    fun serialize(): ByteArray =
        TfheBridge.serialize(ptr)

    override fun toString(): String = "🔒(?)" // don't auto-decrypt in toString

    companion object {
        fun fromBoolean(value: Boolean, key: Keypair): EncryptedBool =
            EncryptedBool(TfheBridge.encrypt(value, key))
    }
}

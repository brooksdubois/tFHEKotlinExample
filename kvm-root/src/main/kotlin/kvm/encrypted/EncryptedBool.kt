package kvm.encrypted

import kvm.native.EncPtr
import kvm.native.TfheBridge
import kvm.native.xor

@JvmInline
value class EncryptedBool(val ptr: EncPtr) {
    fun not(): EncryptedBool = EncryptedBool(TfheBridge.not(ptr))
    fun and(other: EncryptedBool): EncryptedBool = EncryptedBool(TfheBridge.and(ptr, other.ptr))
    fun or(other: EncryptedBool): EncryptedBool = EncryptedBool(TfheBridge.or(ptr, other.ptr))
    fun decrypt(): Boolean = TfheBridge.decrypt(ptr)

    fun xor(other: EncryptedBool): EncryptedBool =
        EncryptedBool(TfheBridge.xor(ptr, other.ptr))

    override fun toString(): String = "🔒(${decrypt()})"

    companion object {
        fun fromBoolean(value: Boolean): EncryptedBool =
            EncryptedBool(TfheBridge.encrypt(value))
    }

    fun toInt(): EncryptedInt = EncryptedInt(listOf(this) + List(7) { fromBoolean(false) })

}
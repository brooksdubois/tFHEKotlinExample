package kvm.encrypted

import kvm.native.EncPtr
import kvm.native.TfheBridge

@JvmInline
value class EncryptedBool(val ptr: EncPtr) {
    fun not(): EncryptedBool = EncryptedBool(TfheBridge.not(ptr))
    fun and(other: EncryptedBool): EncryptedBool = EncryptedBool(TfheBridge.and(ptr, other.ptr))
    fun or(other: EncryptedBool): EncryptedBool = EncryptedBool(TfheBridge.or(ptr, other.ptr))
    fun decrypt(): Boolean = TfheBridge.decrypt(ptr)

    override fun toString(): String = "🔒(${decrypt()})"

    companion object {
        fun fromBoolean(value: Boolean): EncryptedBool =
            EncryptedBool(TfheBridge.encrypt(value))
    }
}
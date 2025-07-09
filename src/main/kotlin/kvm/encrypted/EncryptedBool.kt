package kvm.encrypted

import kvm.native.TfheBridge

class EncryptedBool(private val ptr: Long) {
    fun not(): EncryptedBool = EncryptedBool(TfheBridge.tfhe_not(ptr))
    fun and(other: EncryptedBool): EncryptedBool = EncryptedBool(TfheBridge.tfhe_and(ptr, other.ptr))
    fun or(other: EncryptedBool): EncryptedBool = EncryptedBool(TfheBridge.tfhe_or(ptr, other.ptr))
    fun decrypt(): Boolean = TfheBridge.tfhe_decrypt_bit(ptr)

    override fun toString(): String = "🔒(${decrypt()})"

    companion object {
        fun fromBoolean(value: Boolean): EncryptedBool =
            EncryptedBool(TfheBridge.tfhe_encrypt_bit(value))
    }
}

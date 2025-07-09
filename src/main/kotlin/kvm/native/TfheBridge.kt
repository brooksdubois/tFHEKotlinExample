package kvm.native

import jniNative.TfheBridgeJNI

object TfheBridge {
    fun init() = TfheBridgeJNI.tfhe_init_keys()

    fun encrypt(value: Boolean): EncPtr {
        val byteArray = byteArrayOf(if (value) 1 else 0)
        return EncPtr(TfheBridgeJNI.tfhe_encrypt_byte(byteArray))
    }

    fun decrypt(ptr: EncPtr): Boolean =
        TfheBridgeJNI.tfhe_decrypt_bit(ptr.raw)

    fun and(a: EncPtr, b: EncPtr): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_and(a.raw, b.raw))

    fun or(a: EncPtr, b: EncPtr): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_or(a.raw, b.raw))

    fun not(ptr: EncPtr): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_not(ptr.raw))

    fun xor(a: EncPtr, b: EncPtr): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_xor(a.raw, b.raw))

    fun echo(ptr: EncPtr): EncPtr =
        EncPtr(TfheBridgeJNI.echo_ptr(ptr.raw))

    fun serialize(ptr: EncPtr): ByteArray =
        TfheBridgeJNI.serialize_ciphertext(ptr.raw)
}

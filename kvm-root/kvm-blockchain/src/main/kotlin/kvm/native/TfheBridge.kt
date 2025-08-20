package kvm.native

import jniNative.TfheBridgeJNI

class Keypair(val ptr: Long) {
    fun free() = TfheBridgeJNI.tfhe_free_keypair(ptr)

    fun exportClientKey(): ByteArray =
        TfheBridgeJNI.export_client_key(ptr)

    fun exportCloudKey(): ByteArray =
        TfheBridgeJNI.export_cloud_key(ptr)
}

object TfheBridge {
    init { NativeLoader.load() }

    fun generateKeypair(): Keypair =
        Keypair(TfheBridgeJNI.tfhe_generate_keys())

    fun encrypt(value: Boolean, key: Keypair): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_encrypt_with(key.ptr, if (value) 1 else 0))

    fun decrypt(ct: EncPtr, key: Keypair): Boolean =
        TfheBridgeJNI.tfhe_decrypt_with(key.ptr, ct.raw).toInt() != 0

    fun and(a: EncPtr, b: EncPtr, key: Keypair): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_and_with(key.ptr, a.raw, b.raw))

    fun or(a: EncPtr, b: EncPtr, key: Keypair): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_or_with(key.ptr, a.raw, b.raw))

    fun xor(a: EncPtr, b: EncPtr, key: Keypair): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_xor_with(key.ptr, a.raw, b.raw))

    fun not(a: EncPtr, key: Keypair): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_not_with(key.ptr, a.raw))

    fun serialize(ct: EncPtr): ByteArray =
        TfheBridgeJNI.serialize_ciphertext(ct.raw)

    fun decryptSerialized(bytes: ByteArray, key: Keypair): Boolean =
        TfheBridgeJNI.tfhe_decrypt_serialized_with(key.ptr, bytes).toInt() != 0

    fun importClientKey(bytes: ByteArray): Keypair =
        Keypair(TfheBridgeJNI.import_client_key(bytes))

    fun importCloudKey(bytes: ByteArray): Keypair =
        Keypair(TfheBridgeJNI.import_cloud_key(bytes))

    fun deserialize(bytes: ByteArray): EncPtr =
        EncPtr(TfheBridgeJNI.deserialize_ciphertext(bytes))
}

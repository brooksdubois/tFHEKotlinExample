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

class IntKeypair(val ptr: Long)

object U16 {
    fun generateKeypair(): IntKeypair =
        IntKeypair(TfheBridgeJNI.tfhe_int_generate_keys())

    fun encrypt(value: Int, keys: IntKeypair): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_int_encryptU16(keys.ptr, value))

    fun add(a: EncPtr, b: EncPtr, keys: IntKeypair): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_int_add(keys.ptr, a.raw, b.raw))

    fun decrypt(ct: EncPtr, keys: IntKeypair): Int =
        TfheBridgeJNI.tfhe_int_decryptU16(keys.ptr, ct.raw)

    fun serialize(ct: EncPtr): ByteArray =
        TfheBridgeJNI.tfhe_int_serialize(ct.raw)

    fun deserialize(bytes: ByteArray): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_int_deserialize(bytes))

    fun exportCompressedServerKey(keys: IntKeypair): ByteArray =
        TfheBridgeJNI.tfhe_int_exportCompressedServerKey(keys.ptr)

    fun importCompressedServerKey(bytes: ByteArray): IntKeypair =
        IntKeypair(TfheBridgeJNI.tfhe_int_importCompressedServerKey(bytes))
}

data class ServerCtx(val ptr: Long)

object U16Server {
    fun fromCompressed(bytes: ByteArray): ServerCtx =
        ServerCtx(TfheBridgeJNI.tfhe_int_serverCtxFromCompressed(bytes))

    fun add(srv: ServerCtx, a: EncPtr, b: EncPtr): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_int_addWithServer(srv.ptr, a.raw, b.raw))

    fun free(ctx: ServerCtx) =
        TfheBridgeJNI.tfhe_int_freeServerCtx(ctx.ptr)
}

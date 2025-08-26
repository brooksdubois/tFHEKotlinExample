package kvm.native

import jniNative.TfheBridgeJNI

typealias Keypair = IntKeypair

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

    fun exportClientKey(keys: IntKeypair): ByteArray =
        TfheBridgeJNI.tfhe_int_exportClientKey(keys.ptr)

    fun importClientKey(bytes: ByteArray): IntKeypair =
        IntKeypair(TfheBridgeJNI.tfhe_int_importClientKey(bytes))
}

data class ServerCtx(val ptr: Long)

object U16Server {
    fun addClear(srv: ServerCtx, ct: EncPtr, v: Int): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_int_addClearWithServer(srv.ptr, ct.raw, v and 0xFFFF))

    fun fromCompressed(bytes: ByteArray): ServerCtx =
        ServerCtx(TfheBridgeJNI.tfhe_int_serverCtxFromCompressed(bytes))

    fun add(srv: ServerCtx, a: EncPtr, b: EncPtr): EncPtr =
        EncPtr(TfheBridgeJNI.tfhe_int_addWithServer(srv.ptr, a.raw, b.raw))

    fun free(ctx: ServerCtx) =
        TfheBridgeJNI.tfhe_int_freeServerCtx(ctx.ptr)

}

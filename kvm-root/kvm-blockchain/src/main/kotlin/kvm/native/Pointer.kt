package kvm.native

import jniNative.TfheBridgeJNI

@JvmInline
value class EncPtr(val raw: Long)

fun EncPtr.xor(other: EncPtr): EncPtr =
    EncPtr(TfheBridgeJNI.tfhe_xor(this.raw, other.raw))
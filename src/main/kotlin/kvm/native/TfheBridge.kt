package kvm.native

object TfheBridge {
    init {
        System.loadLibrary("tfhe_bridge") // auto-resolves to libtfhe_bridge.dylib
    }

    @JvmStatic external fun tfhe_init_keys()
    @JvmStatic external fun tfhe_encrypt_bit(value: Boolean): Long
    @JvmStatic external fun tfhe_decrypt_bit(ptr: Long): Boolean
    @JvmStatic external fun tfhe_not(ptr: Long): Long
    @JvmStatic external fun tfhe_and(a: Long, b: Long): Long
    @JvmStatic external fun tfhe_or(a: Long, b: Long): Long
}

package kvm

import kvm.native.TfheBridge

fun main() {
    println("Library path: " + System.getProperty("java.library.path"))
    TfheBridge.tfhe_init_keys()

    val a = TfheBridge.tfhe_encrypt_bit(true)
    val b = TfheBridge.tfhe_encrypt_bit(false)
    println("a = $a")
    println("b = $b")

    val result = TfheBridge.tfhe_and(a, b)

    val decrypted = TfheBridge.tfhe_decrypt_bit(result)
    println("Decrypted AND result: $decrypted")

}

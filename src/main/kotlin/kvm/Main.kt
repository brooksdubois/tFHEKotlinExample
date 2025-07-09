package kvm

import kvm.encrypted.EncryptedBool
import kvm.native.TfheBridge

fun main() {
    TfheBridge.init()

    val a = EncryptedBool.fromBoolean(true)
    val b = EncryptedBool.fromBoolean(false)
    val result = a.and(b).not()

    println("Encrypted result: $result") // 🔒(true)
}

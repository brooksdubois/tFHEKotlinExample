package kvm.encrypted

import kvm.native.Keypair

fun reencryptForTally(original: EncryptedInt, userKey: Keypair, tallyKey: Keypair): EncryptedInt {
    val value = original.decrypt(userKey)
    return EncryptedInt.fromInt(value, tallyKey)
}

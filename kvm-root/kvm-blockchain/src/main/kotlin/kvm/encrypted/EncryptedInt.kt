package kvm.encrypted

import kvm.native.Keypair

class EncryptedInt(private val bits: List<EncryptedBool>) {

    fun add(other: EncryptedInt, key: Keypair): EncryptedInt {
        require(bits.size == other.bits.size) { "Bit widths must match" }

        var carry = EncryptedBool.fromBoolean(false, key)
        val resultBits = mutableListOf<EncryptedBool>()

        for (i in bits.indices) {
            val a = bits[i]
            val b = other.bits[i]

            val partialSum = a.xor(b, key)
            val sum = partialSum.xor(carry, key)

            val carryOut = (a.and(b, key)).or(carry.and(partialSum, key), key)

            resultBits += sum
            carry = carryOut
        }

        return EncryptedInt(resultBits)
    }

    fun decrypt(key: Keypair): Int {
        return bits.mapIndexed { i, bit ->
            if (bit.decrypt(key)) (1 shl i) else 0
        }.sum()
    }

    fun equals(value: Int, key: Keypair): EncryptedBool {
        val bitsToMatch = fromInt(value, key).bits
        require(bits.size == bitsToMatch.size) { "Bit widths must match" }

        val bitwiseEqual = bits.zip(bitsToMatch).map { (a, b) -> a.xor(b, key).not(key) }
        return bitwiseEqual.reduce { acc, bit -> acc.and(bit, key) }
    }

    fun serialize(): List<ByteArray> =
        bits.map { it.serialize() }

    override fun toString(): String = "🔒(?)" // no implicit decryption

    companion object {
        private const val BIT_WIDTH = 8

        fun fromInt(value: Int, key: Keypair): EncryptedInt {
            val bools = (0 until BIT_WIDTH).map { i ->
                EncryptedBool.fromBoolean((value shr i) and 1 == 1, key)
            }
            return EncryptedInt(bools)
        }
    }
}

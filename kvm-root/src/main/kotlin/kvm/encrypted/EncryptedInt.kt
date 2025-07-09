package kvm.encrypted

class EncryptedInt(private val bits: List<EncryptedBool>) {

    fun add(other: EncryptedInt): EncryptedInt {
        require(bits.size == other.bits.size) { "Bit widths must match" }

        var carry = EncryptedBool.fromBoolean(false)
        val resultBits = mutableListOf<EncryptedBool>()

        for (i in bits.indices) {
            val a = bits[i]
            val b = other.bits[i]

            val partialSum = a.xor(b)
            val sum = partialSum.xor(carry)

            val carryOut = (a.and(b)).or(carry.and(partialSum))

            resultBits += sum
            carry = carryOut
        }

        return EncryptedInt(resultBits)
    }

    fun decrypt(): Int {
        return bits
            .mapIndexed { i, bit -> if (bit.decrypt()) (1 shl i) else 0 }
            .sum()
    }

    override fun toString(): String = "🔒(${decrypt()})"

    companion object {
        private const val BIT_WIDTH = 8

        fun fromInt(value: Int): EncryptedInt {
            val bools = (0 until BIT_WIDTH).map { i ->
                EncryptedBool.fromBoolean((value shr i) and 1 == 1)
            }
            return EncryptedInt(bools)
        }
    }

    fun equals(value: Int): EncryptedBool {
        val bitsToMatch = fromInt(value).bits
        require(bits.size == bitsToMatch.size) { "Bit widths must match" }

        val bitwiseEqual = bits.zip(bitsToMatch).map { (a, b) -> a.xor(b).not() }
        return bitwiseEqual.reduce { acc, bit -> acc.and(bit) }
    }
}

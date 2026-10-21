package com.omnicalc.platform.core.math

import java.math.BigInteger
import java.util.Locale

enum class WordSize(val bitCount: Int, val displayName: String, val mask: ULong) {
    BYTE(8, "BYTE (8-bit)", 0xFFuL),
    WORD(16, "WORD (16-bit)", 0xFFFFuL),
    DWORD(32, "DWORD (32-bit)", 0xFFFFFFFFuL),
    QWORD(64, "QWORD (64-bit)", 0xFFFFFFFFFFFFFFFFuL)
}

enum class NumberBase(val radix: Int, val prefix: String) {
    HEX(16, "0x"),
    DEC(10, ""),
    OCT(8, "0o"),
    BIN(2, "0b")
}

data class ProgrammerState(
    val rawValue: ULong = 0uL,
    val wordSize: WordSize = WordSize.QWORD
) {
    val maskedValue: ULong
        get() = rawValue and wordSize.mask

    val hexString: String
        get() = maskedValue.toString(16).uppercase(Locale.ROOT)

    val decUnsignedString: String
        get() = maskedValue.toString(10)

    val decSignedString: String
        get() {
            val bits = wordSize.bitCount
            val signBit = 1uL shl (bits - 1)
            return if ((maskedValue and signBit) != 0uL) {
                // Negative in two's complement
                val complement = (wordSize.mask - maskedValue + 1uL)
                "-${complement.toString(10)}"
            } else {
                maskedValue.toString(10)
            }
        }

    val octString: String
        get() = maskedValue.toString(8)

    val binString: String
        get() {
            val rawBin = maskedValue.toString(2)
            // Pad to full bit count of the word size
            val padded = rawBin.padStart(wordSize.bitCount, '0')
            // Group into 4-bit nibbles for readability
            return padded.chunked(4).joinToString(" ")
        }

    fun isBitSet(index: Int): Boolean {
        if (index !in 0 until wordSize.bitCount) return false
        return (maskedValue and (1uL shl index)) != 0uL
    }

    fun toggleBit(index: Int): ProgrammerState {
        if (index !in 0 until wordSize.bitCount) return this
        val toggled = maskedValue xor (1uL shl index)
        return copy(rawValue = toggled)
    }

    fun and(other: ULong): ProgrammerState = copy(rawValue = (maskedValue and other) and wordSize.mask)
    fun or(other: ULong): ProgrammerState = copy(rawValue = (maskedValue or other) and wordSize.mask)
    fun xor(other: ULong): ProgrammerState = copy(rawValue = (maskedValue xor other) and wordSize.mask)
    fun not(): ProgrammerState = copy(rawValue = (maskedValue.inv()) and wordSize.mask)
    fun nand(other: ULong): ProgrammerState = copy(rawValue = ((maskedValue and other).inv()) and wordSize.mask)
    fun nor(other: ULong): ProgrammerState = copy(rawValue = ((maskedValue or other).inv()) and wordSize.mask)

    fun shiftLeft(bits: Int): ProgrammerState {
        if (bits <= 0) return if (bits < 0) shiftRightLogical(-bits) else this
        if (bits >= wordSize.bitCount) return copy(rawValue = 0uL)
        return copy(rawValue = (maskedValue shl bits) and wordSize.mask)
    }

    fun shiftRightLogical(bits: Int): ProgrammerState {
        if (bits <= 0) return if (bits < 0) shiftLeft(-bits) else this
        if (bits >= wordSize.bitCount) return copy(rawValue = 0uL)
        return copy(rawValue = (maskedValue shr bits) and wordSize.mask)
    }

    fun shiftRightArithmetic(bits: Int): ProgrammerState {
        if (bits <= 0) return if (bits < 0) shiftLeft(-bits) else this
        val signBit = 1uL shl (wordSize.bitCount - 1)
        val isNegative = (maskedValue and signBit) != 0uL
        if (bits >= wordSize.bitCount) {
            return copy(rawValue = if (isNegative) wordSize.mask else 0uL)
        }
        var shifted = (maskedValue shr bits)
        if (isNegative) {
            val highMask = (wordSize.mask shl (wordSize.bitCount - bits)) and wordSize.mask
            shifted = shifted or highMask
        }
        return copy(rawValue = shifted and wordSize.mask)
    }

    fun rotateLeft(bits: Int): ProgrammerState {
        val n = wordSize.bitCount
        val shift = ((bits % n) + n) % n
        if (shift == 0) return this
        val rotated = ((maskedValue shl shift) or (maskedValue shr (n - shift))) and wordSize.mask
        return copy(rawValue = rotated)
    }

    fun rotateRight(bits: Int): ProgrammerState {
        val n = wordSize.bitCount
        val shift = ((bits % n) + n) % n
        if (shift == 0) return this
        val rotated = ((maskedValue shr shift) or (maskedValue shl (n - shift))) and wordSize.mask
        return copy(rawValue = rotated)
    }

    companion object {
        fun fromString(input: String, base: NumberBase, wordSize: WordSize): ProgrammerState {
            val cleaned = input.trim().replace(" ", "").replace("_", "")
            if (cleaned.isEmpty()) return ProgrammerState(0uL, wordSize)

            val parsedULong = try {
                if (base == NumberBase.DEC && cleaned.startsWith("-")) {
                    // Parse negative decimal into two's complement
                    val absVal = cleaned.substring(1).toULong(10)
                    val mask = wordSize.mask
                    val comp = (mask - absVal + 1uL) and mask
                    comp
                } else {
                    cleaned.toULong(base.radix)
                }
            } catch (e: Exception) {
                // If larger than ULong, use BigInteger and mask
                val bi = BigInteger(cleaned, base.radix)
                bi.toLong().toULong()
            }

            return ProgrammerState(parsedULong and wordSize.mask, wordSize)
        }
    }
}

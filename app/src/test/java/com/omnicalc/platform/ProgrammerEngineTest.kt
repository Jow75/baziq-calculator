package com.omnicalc.platform

import com.omnicalc.platform.core.math.NumberBase
import com.omnicalc.platform.core.math.ProgrammerState
import com.omnicalc.platform.core.math.WordSize
import org.junit.Assert.*
import org.junit.Test

class ProgrammerEngineTest {

    @Test
    fun testBaseConversionsForDecimal255() {
        val state = ProgrammerState.fromString("255", NumberBase.DEC, WordSize.BYTE)
        assertEquals("FF", state.hexString)
        assertEquals("255", state.decUnsignedString)
        assertEquals("-1", state.decSignedString) // 0xFF in 8-bit signed two's complement is -1
        assertEquals("377", state.octString)
        assertEquals("1111 1111", state.binString)
    }

    @Test
    fun testHexInputParsing() {
        val state = ProgrammerState.fromString("FF", NumberBase.HEX, WordSize.DWORD)
        assertEquals("255", state.decUnsignedString)
        assertEquals("255", state.decSignedString) // Positive in 32-bit
        assertEquals("0000 0000 0000 0000 0000 0000 1111 1111", state.binString)
    }

    @Test
    fun testBitwiseAndOrXorNot() {
        val a = ProgrammerState.fromString("12", NumberBase.DEC, WordSize.BYTE) // 0000 1100
        val b = 10uL // 0000 1010

        val andRes = a.and(b)
        assertEquals("8", andRes.decUnsignedString) // 0000 1000

        val orRes = a.or(b)
        assertEquals("14", orRes.decUnsignedString) // 0000 1110

        val xorRes = a.xor(b)
        assertEquals("6", xorRes.decUnsignedString) // 0000 0110

        val notRes = a.not()
        // ~12 in 8-bit: 255 - 12 = 243 (0xF3)
        assertEquals("243", notRes.decUnsignedString)
    }

    @Test
    fun testShiftOperations() {
        val a = ProgrammerState.fromString("8", NumberBase.DEC, WordSize.BYTE) // 0000 1000

        val shl = a.shiftLeft(2) // 0010 0000 = 32
        assertEquals("32", shl.decUnsignedString)

        val shr = a.shiftRightLogical(2) // 0000 0010 = 2
        assertEquals("2", shr.decUnsignedString)

        // Shift by zero
        val shl0 = a.shiftLeft(0)
        assertEquals("8", shl0.decUnsignedString)
    }

    @Test
    fun testArithmeticShiftRightWithSignExtension() {
        // -16 in 8-bit is 240 (0xF0 = 1111 0000)
        val neg = ProgrammerState.fromString("240", NumberBase.DEC, WordSize.BYTE)
        assertEquals("-16", neg.decSignedString)

        val sar = neg.shiftRightArithmetic(2)
        // 1111 0000 SAR 2 -> 1111 1100 = 252 (which is -4 in signed 8-bit)
        assertEquals("-4", sar.decSignedString)
        assertEquals("252", sar.decUnsignedString)
    }

    @Test
    fun testRotateLeftAndRight() {
        // In 8-bit: 0x81 = 1000 0001
        val state = ProgrammerState.fromString("129", NumberBase.DEC, WordSize.BYTE)

        // Rotate left 1 -> 0000 0011 = 3
        val rotL = state.rotateLeft(1)
        assertEquals("3", rotL.decUnsignedString)

        // Rotate right 1 -> 1100 0000 = 192
        val rotR = state.rotateRight(1)
        assertEquals("192", rotR.decUnsignedString)

        // Rotate by zero
        assertEquals("129", state.rotateLeft(0).decUnsignedString)
    }

    @Test
    fun testBitToggling() {
        val state = ProgrammerState.fromString("0", NumberBase.DEC, WordSize.BYTE)
        assertFalse(state.isBitSet(0))

        val toggled0 = state.toggleBit(0) // bit 0 becomes 1
        assertTrue(toggled0.isBitSet(0))
        assertEquals("1", toggled0.decUnsignedString)

        val toggledAgain = toggled0.toggleBit(0) // bit 0 becomes 0
        assertFalse(toggledAgain.isBitSet(0))
        assertEquals("0", toggledAgain.decUnsignedString)
    }

    @Test
    fun testWordSizeMasking() {
        // 0x12345678 in DWORD masked to WORD should be 0x5678
        val dwordState = ProgrammerState.fromString("12345678", NumberBase.HEX, WordSize.DWORD)
        assertEquals("12345678", dwordState.hexString)

        val wordState = ProgrammerState.fromString("12345678", NumberBase.HEX, WordSize.WORD)
        assertEquals("5678", wordState.hexString)
    }

    @Test
    fun testShiftBoundariesAndNegativeCounts() {
        val a = ProgrammerState.fromString("1", NumberBase.DEC, WordSize.BYTE)

        // Shift left by 8 (boundary of 8-bit word size) -> all bits shifted out -> 0
        val shl8 = a.shiftLeft(8)
        assertEquals("0", shl8.decUnsignedString)

        // Shift left by more than word size (e.g. 10) -> 0
        val shl10 = a.shiftLeft(10)
        assertEquals("0", shl10.decUnsignedString)

        // Shift right by 8 -> 0
        val shr8 = a.shiftRightLogical(8)
        assertEquals("0", shr8.decUnsignedString)

        // Negative shift normalization in rotate
        // Rotating left by -1 on 8-bit word is identical to rotating right by 1
        val state = ProgrammerState.fromString("129", NumberBase.DEC, WordSize.BYTE) // 1000 0001
        val rotNeg1 = state.rotateLeft(-1)
        val rotRight1 = state.rotateRight(1)
        assertEquals(rotRight1.decUnsignedString, rotNeg1.decUnsignedString)
    }

    @Test
    fun testOverflowMaskingAcrossWordSizes() {
        // Decimal 300 exceeds 8-bit unsigned (255) -> 300 % 256 = 44
        val state = ProgrammerState.fromString("300", NumberBase.DEC, WordSize.BYTE)
        assertEquals("44", state.decUnsignedString)

        // 64-bit max value 0xFFFFFFFFFFFFFFFF
        val qwordMax = ProgrammerState.fromString("FFFFFFFFFFFFFFFF", NumberBase.HEX, WordSize.QWORD)
        assertEquals("-1", qwordMax.decSignedString)
        assertEquals("18446744073709551615", qwordMax.decUnsignedString)
    }
}

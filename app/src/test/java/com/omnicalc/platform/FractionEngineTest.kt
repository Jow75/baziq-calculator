package com.omnicalc.platform

import com.omnicalc.platform.core.math.Fraction
import org.junit.Assert.assertEquals
import org.junit.Test

class FractionEngineTest {

    @Test
    fun testFractionAddition() {
        val half = Fraction.of(1, 2)
        val quarter = Fraction.of(1, 4)
        val sum = half + quarter
        assertEquals("3/4", sum.toString())
    }

    @Test
    fun testFractionSimplification() {
        val f = Fraction.of(6, 8)
        assertEquals("3/4", f.toString())
    }

    @Test
    fun testMixedFraction() {
        val f = Fraction.of(7, 4)
        val mixed = f.toMixed()
        assertEquals("1 3/4", mixed.toString())
    }

    @Test
    fun testDecimalToFraction() {
        val f1 = Fraction.fromDecimal(0.75)
        assertEquals("3/4", f1.toString())

        val f2 = Fraction.fromDecimal(0.625)
        assertEquals("5/8", f2.toString())

        val f3 = Fraction.fromDecimal(0.333333333333)
        assertEquals("1/3", f3.toString())
    }
}

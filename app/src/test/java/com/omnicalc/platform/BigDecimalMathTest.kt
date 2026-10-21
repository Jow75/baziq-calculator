package com.omnicalc.platform

import com.omnicalc.platform.core.math.BigDecimalMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class BigDecimalMathTest {

    @Test
    fun testBasicArithmetic() {
        val a = BigDecimal("123.456")
        val b = BigDecimal("789.012")

        val sum = BigDecimalMath.add(a, b)
        assertEquals(BigDecimal("912.468"), sum)

        val diff = BigDecimalMath.subtract(b, a)
        assertEquals(BigDecimal("665.556"), diff)

        val product = BigDecimalMath.multiply(BigDecimal("2.5"), BigDecimal("4.0"))
        assertEquals(0, BigDecimal("10.00").compareTo(product))
    }

    @Test
    fun testDivisionByZeroThrows() {
        assertThrows(ArithmeticException::class.java) {
            BigDecimalMath.divide(BigDecimal("10"), BigDecimal.ZERO)
        }
    }

    @Test
    fun testNewtonRaphsonSqrt() {
        val sqrt144 = BigDecimalMath.sqrt(BigDecimal("144"))
        assertEquals(0, BigDecimal("12").compareTo(sqrt144))

        val sqrt2 = BigDecimalMath.sqrt(BigDecimal("2"))
        assertEquals(1.41421356, sqrt2.toDouble(), 1e-7)

        val sqrt0 = BigDecimalMath.sqrt(BigDecimal.ZERO)
        assertEquals(BigDecimal.ZERO, sqrt0)
    }

    @Test
    fun testFactorial() {
        assertEquals(BigDecimal.ONE, BigDecimalMath.factorial(0))
        assertEquals(BigDecimal.ONE, BigDecimalMath.factorial(1))
        assertEquals(BigDecimal("120"), BigDecimalMath.factorial(5))
        assertEquals(BigDecimal("3628800"), BigDecimalMath.factorial(10))
    }

    @Test
    fun testPower() {
        val twoCubed = BigDecimalMath.power(BigDecimal("2"), BigDecimal("3"))
        assertEquals(0, BigDecimal("8").compareTo(twoCubed))

        val twoPowerNegative = BigDecimalMath.power(BigDecimal("2"), BigDecimal("-1"))
        assertEquals(0, BigDecimal("0.5").compareTo(twoPowerNegative))
    }
}

package com.omnicalc.platform

import com.omnicalc.platform.core.math.AngleMode
import com.omnicalc.platform.core.math.EvalResult
import com.omnicalc.platform.core.math.ExpressionEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ExpressionEvaluatorTest {

    private val evaluator = ExpressionEvaluator(angleMode = AngleMode.DEGREES)

    @Test
    fun testOperatorPrecedence() {
        // 2 + 3 * 4 MUST be 14, not 20
        val result = evaluator.evaluate("2 + 3 * 4")
        assertTrue(result is EvalResult.Success)
        val success = result as EvalResult.Success
        assertEquals(0, BigDecimal("14").compareTo(success.value))
    }

    @Test
    fun testParentheses() {
        // (2 + 3) * 4 MUST be 20
        val result = evaluator.evaluate("(2 + 3) * 4")
        assertTrue(result is EvalResult.Success)
        val success = result as EvalResult.Success
        assertEquals(0, BigDecimal("20").compareTo(success.value))
    }

    @Test
    fun testUnaryMinus() {
        val r1 = evaluator.evaluate("-5 + 10")
        assertTrue(r1 is EvalResult.Success)
        assertEquals(0, BigDecimal("5").compareTo((r1 as EvalResult.Success).value))

        val r2 = evaluator.evaluate("10 + -5")
        assertTrue(r2 is EvalResult.Success)
        assertEquals(0, BigDecimal("5").compareTo((r2 as EvalResult.Success).value))

        val r3 = evaluator.evaluate("-(-5)")
        assertTrue(r3 is EvalResult.Success)
        assertEquals(0, BigDecimal("5").compareTo((r3 as EvalResult.Success).value))
    }

    @Test
    fun testImplicitMultiplication() {
        val r1 = evaluator.evaluate("2(3 + 4)")
        assertTrue(r1 is EvalResult.Success)
        assertEquals(0, BigDecimal("14").compareTo((r1 as EvalResult.Success).value))

        val r2 = evaluator.evaluate("(2)(3)")
        assertTrue(r2 is EvalResult.Success)
        assertEquals(0, BigDecimal("6").compareTo((r2 as EvalResult.Success).value))
    }

    @Test
    fun testScientificFunctionsDegrees() {
        evaluator.angleMode = AngleMode.DEGREES

        // sin(30) = 0.5
        val r1 = evaluator.evaluate("sin(30)")
        assertTrue(r1 is EvalResult.Success)
        assertEquals(0.5, (r1 as EvalResult.Success).value.toDouble(), 1e-6)

        // cos(0) = 1
        val r2 = evaluator.evaluate("cos(0)")
        assertTrue(r2 is EvalResult.Success)
        assertEquals(1.0, (r2 as EvalResult.Success).value.toDouble(), 1e-6)

        // sqrt(144) = 12
        val r3 = evaluator.evaluate("sqrt(144)")
        assertTrue(r3 is EvalResult.Success)
        assertEquals(0, BigDecimal("12").compareTo((r3 as EvalResult.Success).value))

        // log(100) = 2
        val r4 = evaluator.evaluate("log(100)")
        assertTrue(r4 is EvalResult.Success)
        assertEquals(2.0, (r4 as EvalResult.Success).value.toDouble(), 1e-6)
    }

    @Test
    fun testFactorialAndPower() {
        val r1 = evaluator.evaluate("5!")
        assertTrue(r1 is EvalResult.Success)
        assertEquals(0, BigDecimal("120").compareTo((r1 as EvalResult.Success).value))

        val r2 = evaluator.evaluate("2^3")
        assertTrue(r2 is EvalResult.Success)
        assertEquals(0, BigDecimal("8").compareTo((r2 as EvalResult.Success).value))
    }

    @Test
    fun testDivisionByZeroError() {
        val result = evaluator.evaluate("10 / 0")
        assertTrue(result is EvalResult.Error)
        val error = result as EvalResult.Error
        assertTrue(error.message.contains("Cannot divide by zero"))
    }

    @Test
    fun testMismatchedParenthesesError() {
        val result = evaluator.evaluate("(2 + 3")
        assertTrue(result is EvalResult.Error)
    }

    @Test
    fun testPostfixPercentage() {
        // 50% = 0.5
        val r1 = evaluator.evaluate("50%")
        assertTrue(r1 is EvalResult.Success)
        assertEquals(0, BigDecimal("0.5").compareTo((r1 as EvalResult.Success).value))

        // 50 + 10% = 55 (standard consumer calculator behavior)
        val r2 = evaluator.evaluate("50 + 10%")
        assertTrue(r2 is EvalResult.Success)
        assertEquals(0, BigDecimal("55").compareTo((r2 as EvalResult.Success).value))

        // 50 - 10% = 45
        val r3 = evaluator.evaluate("50 - 10%")
        assertTrue(r3 is EvalResult.Success)
        assertEquals(0, BigDecimal("45").compareTo((r3 as EvalResult.Success).value))

        // 50 * 10% = 5
        val r4 = evaluator.evaluate("50 * 10%")
        assertTrue(r4 is EvalResult.Success)
        assertEquals(0, BigDecimal("5").compareTo((r4 as EvalResult.Success).value))

        // 50 / 10% = 500
        val r5 = evaluator.evaluate("50 / 10%")
        assertTrue(r5 is EvalResult.Success)
        assertEquals(0, BigDecimal("500").compareTo((r5 as EvalResult.Success).value))

        // 20% of 45000 = 9000
        val r6 = evaluator.evaluate("20% of 45000")
        assertTrue(r6 is EvalResult.Success)
        assertEquals(0, BigDecimal("9000").compareTo((r6 as EvalResult.Success).value))

        // Chained percentage: 100 + 10% + 10% = 121
        val r7 = evaluator.evaluate("100 + 10% + 10%")
        assertTrue(r7 is EvalResult.Success)
        assertEquals(0, BigDecimal("121").compareTo((r7 as EvalResult.Success).value))

        // 50% + 50% = 1.0 (both are standalone percentages)
        val r8 = evaluator.evaluate("50% + 50%")
        assertTrue(r8 is EvalResult.Success)
        assertEquals(0, BigDecimal("1").compareTo((r8 as EvalResult.Success).value))

        // Negative numbers with percentage
        val r9 = evaluator.evaluate("-20% * 500")
        assertTrue(r9 is EvalResult.Success)
        assertEquals(0, BigDecimal("-100").compareTo((r9 as EvalResult.Success).value))

        val r10 = evaluator.evaluate("(200 + 50)%")
        assertTrue(r10 is EvalResult.Success)
        assertEquals(0, BigDecimal("2.5").compareTo((r10 as EvalResult.Success).value))
    }

    @Test
    fun testModuloOperator() {
        val r1 = evaluator.evaluate("10 mod 3")
        assertTrue(r1 is EvalResult.Success)
        assertEquals(0, BigDecimal("1").compareTo((r1 as EvalResult.Success).value))

        val r2 = evaluator.evaluate("25 mod 7")
        assertTrue(r2 is EvalResult.Success)
        assertEquals(0, BigDecimal("4").compareTo((r2 as EvalResult.Success).value))
    }

    @Test
    fun testNegativeSquareRootError() {
        val result = evaluator.evaluate("sqrt(-4)")
        assertTrue(result is EvalResult.Error)
        assertTrue((result as EvalResult.Error).message.contains("non-negative"))
    }

    @Test
    fun testTangentUndefinedAt90Degrees() {
        evaluator.angleMode = AngleMode.DEGREES
        val result = evaluator.evaluate("tan(90)")
        assertTrue(result is EvalResult.Error)
        assertTrue((result as EvalResult.Error).message.contains("Tangent is undefined"))
    }

    @Test
    fun testZeroResultFormatting() {
        val result = evaluator.evaluate("5 - 5")
        assertTrue(result is EvalResult.Success)
        assertEquals("0", (result as EvalResult.Success).formatted)
    }
}

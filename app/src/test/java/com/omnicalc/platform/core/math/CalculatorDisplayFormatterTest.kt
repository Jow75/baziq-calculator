package com.omnicalc.platform.core.math

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CalculatorDisplayFormatterTest {

    @Test
    fun testMandatoryIntegerGrouping() {
        assertEquals("0", CalculatorDisplayFormatter.formatExpression("0"))
        assertEquals("1", CalculatorDisplayFormatter.formatExpression("1"))
        assertEquals("10", CalculatorDisplayFormatter.formatExpression("10"))
        assertEquals("100", CalculatorDisplayFormatter.formatExpression("100"))
        assertEquals("999", CalculatorDisplayFormatter.formatExpression("999"))
        assertEquals("1,000", CalculatorDisplayFormatter.formatExpression("1000"))
        assertEquals("1,001", CalculatorDisplayFormatter.formatExpression("1001"))
        assertEquals("9,999", CalculatorDisplayFormatter.formatExpression("9999"))
        assertEquals("10,000", CalculatorDisplayFormatter.formatExpression("10000"))
        assertEquals("10,001", CalculatorDisplayFormatter.formatExpression("10001"))
        assertEquals("999,999", CalculatorDisplayFormatter.formatExpression("999999"))
        assertEquals("1,000,000", CalculatorDisplayFormatter.formatExpression("1000000"))
        assertEquals("1,234,567", CalculatorDisplayFormatter.formatExpression("1234567"))
        assertEquals("123,456,789", CalculatorDisplayFormatter.formatExpression("123456789"))
    }

    @Test
    fun testMandatoryDecimalGrouping() {
        assertEquals("1.5", CalculatorDisplayFormatter.formatExpression("1.5"))
        assertEquals("10.25", CalculatorDisplayFormatter.formatExpression("10.25"))
        assertEquals("1,000.5", CalculatorDisplayFormatter.formatExpression("1000.5"))
        assertEquals("10,000.25", CalculatorDisplayFormatter.formatExpression("10000.25"))
        assertEquals("1,000,000.75", CalculatorDisplayFormatter.formatExpression("1000000.75"))
        assertEquals("1,234,567.89", CalculatorDisplayFormatter.formatExpression("1234567.89"))
    }

    @Test
    fun testTrailingDecimalPointPreserved() {
        assertEquals("1,000.", CalculatorDisplayFormatter.formatExpression("1000."))
        assertEquals("10,000.", CalculatorDisplayFormatter.formatExpression("10000."))
        assertEquals("0.", CalculatorDisplayFormatter.formatExpression("0."))
    }

    @Test
    fun testNegativeNumbersPreserved() {
        assertEquals("-1,000", CalculatorDisplayFormatter.formatExpression("-1000"))
        assertEquals("-10,000", CalculatorDisplayFormatter.formatExpression("-10000"))
        assertEquals("-1,000,000", CalculatorDisplayFormatter.formatExpression("-1000000"))
    }

    @Test
    fun testComplexExpressionsWithSeparators() {
        assertEquals("1,000 + 500", CalculatorDisplayFormatter.formatExpression("1000 + 500"))
        assertEquals("10,000 × 2", CalculatorDisplayFormatter.formatExpression("10000 × 2"))
        assertEquals("1,000,000 ÷ 4", CalculatorDisplayFormatter.formatExpression("1000000 ÷ 4"))
        assertEquals("1,234,567.89 + 10", CalculatorDisplayFormatter.formatExpression("1234567.89 + 10"))
        assertEquals("sin(1,000)", CalculatorDisplayFormatter.formatExpression("sin(1000)"))
        assertEquals("1,000 × (2,000 + 3,000)", CalculatorDisplayFormatter.formatExpression("1000 × (2000 + 3000)"))
    }

    @Test
    fun testGroupingDisabledPreference() {
        assertEquals("1000000", CalculatorDisplayFormatter.formatExpression("1000000", useGrouping = false))
        assertEquals("1000.5", CalculatorDisplayFormatter.formatExpression("1000.5", useGrouping = false))
    }

    @Test
    fun testSanitizeForEvaluation() {
        assertEquals("1000 + 500", CalculatorDisplayFormatter.sanitizeForEvaluation("1,000 + 500"))
        assertEquals("1000000", CalculatorDisplayFormatter.sanitizeForEvaluation("1,000,000"))
        assertEquals("1234567.89", CalculatorDisplayFormatter.sanitizeForEvaluation("1,234,567.89"))
        assertEquals("pow(2, 3)", CalculatorDisplayFormatter.sanitizeForEvaluation("pow(2, 3)"))
    }

    @Test
    fun testMathematicalIntegrityOfFormattedValues() {
        val evaluator = ExpressionEvaluator()

        // Verify original evaluation
        val originalEval = evaluator.evaluate("1000 + 500")
        assertEquals(true, originalEval is EvalResult.Success)
        assertEquals(BigDecimal("1500"), (originalEval as EvalResult.Success).value)

        // Verify evaluation after sanitizing display formatted string
        val formattedExpr = CalculatorDisplayFormatter.formatExpression("1000 + 500")
        assertEquals("1,000 + 500", formattedExpr)
        val sanitized = CalculatorDisplayFormatter.sanitizeForEvaluation(formattedExpr)
        val sanitizedEval = evaluator.evaluate(sanitized)
        assertEquals(true, sanitizedEval is EvalResult.Success)
        assertEquals(BigDecimal("1500"), (sanitizedEval as EvalResult.Success).value)
    }

    @Test
    fun testLongExpressionChainingPreservesMathematicalIntegrity() {
        val evaluator = ExpressionEvaluator()
        // Expression matching user test matrix: 400 + 400 + 400 + 400 + 400 + 400 + 400
        val longExpr = "400 + 400 + 400 + 400 + 400 + 400 + 400"
        val formatted = CalculatorDisplayFormatter.formatExpression(longExpr)
        assertEquals(longExpr, formatted)

        val eval = evaluator.evaluate(CalculatorDisplayFormatter.sanitizeForEvaluation(formatted))
        assertEquals(true, eval is EvalResult.Success)
        assertEquals(BigDecimal("2800"), (eval as EvalResult.Success).value)
    }

    @Test
    fun testVeryLongExpressionWithThousandsGrouping() {
        val evaluator = ExpressionEvaluator()
        // Expression: 1000 + 500 + 2000 + 7500 + 10000 + 25000
        val raw = "1000 + 500 + 2000 + 7500 + 10000 + 25000"
        val formatted = CalculatorDisplayFormatter.formatExpression(raw)
        assertEquals("1,000 + 500 + 2,000 + 7,500 + 10,000 + 25,000", formatted)

        val eval = evaluator.evaluate(CalculatorDisplayFormatter.sanitizeForEvaluation(formatted))
        assertEquals(true, eval is EvalResult.Success)
        assertEquals(BigDecimal("46000"), (eval as EvalResult.Success).value)
    }

    @Test
    fun testLongExpressionWithDecimalPrecisionAndParentheses() {
        val evaluator = ExpressionEvaluator()
        val raw = "1234.56 * (7890.12 + 3456.78) / 1000"
        val formatted = CalculatorDisplayFormatter.formatExpression(raw)
        assertEquals("1,234.56 * (7,890.12 + 3,456.78) / 1,000", formatted)

        val eval = evaluator.evaluate(CalculatorDisplayFormatter.sanitizeForEvaluation(formatted))
        assertEquals(true, eval is EvalResult.Success)
    }
}

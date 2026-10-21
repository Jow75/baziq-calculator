package com.omnicalc.platform

import com.omnicalc.platform.core.math.QuickCalculationEngine
import org.junit.Assert.*
import org.junit.Test

class QuickCalculationEngineTest {

    @Test
    fun testNaturalPercentageOf() {
        val res = QuickCalculationEngine.evaluateQuick("20% of 45000")
        assertNotNull(res)
        assertEquals("9000", res!!.primaryResult)
        assertTrue(res.secondaryResult!!.contains("45000"))
        assertEquals("finance/discount", res.targetRoute)
    }

    @Test
    fun testNaturalPercentageOfDecimal() {
        val res = QuickCalculationEngine.evaluateQuick("15.5% of 200")
        assertNotNull(res)
        assertEquals("31", res!!.primaryResult)
    }

    @Test
    fun testNaturalTaxVatAddition() {
        val res = QuickCalculationEngine.evaluateQuick("1000 + 16% VAT")
        assertNotNull(res)
        assertEquals("1160.00", res!!.primaryResult)
        assertTrue(res.secondaryResult!!.contains("160.00"))
        assertEquals("finance/tax_vat", res.targetRoute)
    }

    @Test
    fun testNaturalDiscountSubtraction() {
        val res = QuickCalculationEngine.evaluateQuick("20000 - 15% discount")
        assertNotNull(res)
        assertEquals("17000.00", res!!.primaryResult)
        assertTrue(res.secondaryResult!!.contains("3000.00"))
        assertEquals("finance/discount", res.targetRoute)
    }

    @Test
    fun testNaturalCurrencyConversionUsdToKes() {
        val res = QuickCalculationEngine.evaluateQuick("100 USD to KES")
        assertNotNull(res)
        assertTrue(res!!.primaryResult.contains("KES"))
        assertTrue(res.primaryResult.contains("12,950") || res.primaryResult.contains("12950"))
        assertEquals("currency", res.targetRoute)
    }

    @Test
    fun testNaturalCurrencyConversionEurToKmf() {
        val res = QuickCalculationEngine.evaluateQuick("500 EUR in KMF")
        assertNotNull(res)
        assertTrue(res!!.primaryResult.contains("KMF"))
        assertEquals("currency", res.targetRoute)
    }

    @Test
    fun testNaturalUnitConversionKmToMiles() {
        val res = QuickCalculationEngine.evaluateQuick("10 km to miles")
        assertNotNull(res)
        assertTrue(res!!.primaryResult.startsWith("6.21371"))
        assertEquals("units", res.targetRoute)
    }

    @Test
    fun testNaturalUnitConversionCelsiusToFahrenheit() {
        val res = QuickCalculationEngine.evaluateQuick("100 c to f")
        assertNotNull(res)
        assertEquals("212 °F", res!!.primaryResult)
        assertEquals("units", res.targetRoute)
    }

    @Test
    fun testNaturalBmiCalculation() {
        val res = QuickCalculationEngine.evaluateQuick("66 kg / 1.75 m")
        assertNotNull(res)
        assertTrue(res!!.primaryResult.contains("21.5") || res.primaryResult.contains("21.6"))
        assertTrue(res.primaryResult.contains("Normal Weight"))
        assertEquals("health/bmi", res.targetRoute)
    }

    @Test
    fun testNaturalArithmeticExpression() {
        val res = QuickCalculationEngine.evaluateQuick("20000 * 0.15")
        assertNotNull(res)
        assertEquals("3000", res!!.primaryResult)
        assertEquals("calculator/basic", res.targetRoute)
    }

    @Test
    fun testNaturalPercentageOfWithCommasAndSpaces() {
        val res = QuickCalculationEngine.evaluateQuick("20 % of 45,000")
        assertNotNull(res)
        assertEquals("9000", res!!.primaryResult)
        assertTrue(res.secondaryResult!!.contains("45000"))
        assertEquals("finance/discount", res.targetRoute)
    }

    @Test
    fun testNaturalArithmeticWithPercentage() {
        val res = QuickCalculationEngine.evaluateQuick("45000 * 20%")
        assertNotNull(res)
        assertEquals("9000", res!!.primaryResult)
        assertEquals("calculator/basic", res.targetRoute)
    }

    @Test
    fun testNaturalTaxAdditionWithWords() {
        val res = QuickCalculationEngine.evaluateQuick("1000 plus 16% tax")
        assertNotNull(res)
        assertEquals("1160.00", res!!.primaryResult)
        assertTrue(res.secondaryResult!!.contains("160.00"))
        assertEquals("finance/tax_vat", res.targetRoute)
    }

    @Test
    fun testNaturalCurrencyConversionCaseInsensitiveIn() {
        val res = QuickCalculationEngine.evaluateQuick("100 usd in kes")
        assertNotNull(res)
        assertTrue(res!!.primaryResult.contains("KES"))
        assertTrue(res.primaryResult.contains("12,950") || res.primaryResult.contains("12950"))
        assertEquals("currency", res.targetRoute)
    }

    @Test
    fun testNaturalUnitConversionLongWords() {
        val res1 = QuickCalculationEngine.evaluateQuick("10 kilometers to miles")
        assertNotNull(res1)
        assertTrue(res1!!.primaryResult.startsWith("6.21371"))

        val res2 = QuickCalculationEngine.evaluateQuick("10 miles to kilometers")
        assertNotNull(res2)
        assertTrue(res2!!.primaryResult.startsWith("16.0934"))
    }

    @Test
    fun testNaturalUnitConversionDegreeSymbolAndNegativeTemperature() {
        val res1 = QuickCalculationEngine.evaluateQuick("100 °C to °F")
        assertNotNull(res1)
        assertEquals("212 °F", res1!!.primaryResult)

        // -20 C to F = -20 * 1.8 + 32 = -36 + 32 = -4 °F
        val res2 = QuickCalculationEngine.evaluateQuick("-20 C to F")
        assertNotNull(res2)
        assertEquals("-4 °F", res2!!.primaryResult)
    }

    @Test
    fun testMalformedAndAmbiguousInputsReturnNullGracefully() {
        // Lone percentage with no operation or base
        assertNull(QuickCalculationEngine.evaluateQuick("20%"))
        assertNull(QuickCalculationEngine.evaluateQuick("20% of"))
        assertNull(QuickCalculationEngine.evaluateQuick("of 45000"))

        // Ambiguous single currency or pair without amount
        assertNull(QuickCalculationEngine.evaluateQuick("100 USD"))
        assertNull(QuickCalculationEngine.evaluateQuick("USD KES"))

        // Incomplete or invalid unit conversions
        assertNull(QuickCalculationEngine.evaluateQuick("10 to miles"))
        assertNull(QuickCalculationEngine.evaluateQuick("banana to KES"))
        assertNull(QuickCalculationEngine.evaluateQuick("10 km to banana"))
        assertNull(QuickCalculationEngine.evaluateQuick("10 c"))

        // Gibberish
        assertNull(QuickCalculationEngine.evaluateQuick("completely unknown random gibberish 123 xyz"))
    }
}

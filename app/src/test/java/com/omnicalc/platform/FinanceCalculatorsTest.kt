package com.omnicalc.platform

import com.omnicalc.platform.feature.finance.FinanceCalculators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FinanceCalculatorsTest {

    @Test
    fun testDiscountCalculation() {
        val res = FinanceCalculators.calculateDiscount(BigDecimal("1000"), BigDecimal("15"))
        assertEquals(0, BigDecimal("150.00").compareTo(res.amountSaved))
        assertEquals(0, BigDecimal("850.00").compareTo(res.finalPrice))
    }

    @Test
    fun testDiscountAndTax() {
        // 10,000 price, 20% discount (leaves 8,000), 16% tax on 8,000 is 1,280 -> Total = 9,280
        val res = FinanceCalculators.calculateDiscountAndTax(
            originalPrice = BigDecimal("10000"),
            discountPercent = BigDecimal("20"),
            taxPercent = BigDecimal("16"),
            taxAppliedAfterDiscount = true
        )
        assertEquals(0, BigDecimal("2000.00").compareTo(res.amountSaved))
        assertEquals(0, BigDecimal("1280.00").compareTo(res.taxAmount))
        assertEquals(0, BigDecimal("9280.00").compareTo(res.finalPrice))
    }

    @Test
    fun testTaxExclusiveAndInclusive() {
        // Exclusive: 1,000 at 16% tax -> tax = 160, total = 1,160
        val excl = FinanceCalculators.calculateTax(BigDecimal("1000"), BigDecimal("16"), false)
        assertEquals(0, BigDecimal("160.00").compareTo(excl.taxAmount))
        assertEquals(0, BigDecimal("1160.00").compareTo(excl.grossTotal))

        // Inclusive: 1,160 with 16% tax -> net = 1,000, tax = 160, total = 1,160
        val incl = FinanceCalculators.calculateTax(BigDecimal("1160"), BigDecimal("16"), true)
        assertEquals(0, BigDecimal("1000.00").compareTo(incl.netAmount))
        assertEquals(0, BigDecimal("160.00").compareTo(incl.taxAmount))
    }

    @Test
    fun testLoanAmortizationSchedule() {
        // Principal 10,000, 12% annual interest, 12 months
        val loan = FinanceCalculators.calculateLoan(BigDecimal("10000"), BigDecimal("12"), 12)
        assertEquals(12, loan.schedule.size)

        // Verify ending balance of last payment is exactly 0.00
        val lastRow = loan.schedule.last()
        assertEquals(0, BigDecimal("0.00").compareTo(lastRow.endingBalance))

        // Total payments must exceed principal due to interest
        assertTrue(loan.totalPayment > BigDecimal("10000"))
        assertTrue(loan.totalInterest > BigDecimal.ZERO)
    }

    @Test
    fun testProfitAndMarginMarkupDistinction() {
        val res = FinanceCalculators.calculateProfitMargin(
            costPrice = BigDecimal("100"),
            sellingPrice = BigDecimal("150"),
            quantity = 1
        )
        assertEquals(0, BigDecimal("50.00").compareTo(res.grossProfit))
        // Margin = 50 / 150 = 33.33%
        assertEquals(0, BigDecimal("33.33").compareTo(res.profitMarginPercent))
        // Markup = 50 / 100 = 50.00%
        assertEquals(0, BigDecimal("50.00").compareTo(res.markupPercent))
    }

    @Test
    fun testBreakEven() {
        // Fixed: 10,000; Var: 30; Price: 50 -> CM = 20 -> Units = 500, Revenue = 25,000
        val be = FinanceCalculators.calculateBreakEven(
            fixedCosts = BigDecimal("10000"),
            variableCostPerUnit = BigDecimal("30"),
            sellingPricePerUnit = BigDecimal("50")
        )
        assertEquals(0, BigDecimal("500").compareTo(be.breakEvenUnits))
        assertEquals(0, BigDecimal("25000.00").compareTo(be.breakEvenRevenue))
        assertEquals(0, BigDecimal("20.00").compareTo(be.contributionMargin))
    }

    @Test
    fun testZeroInterestLoan() {
        val loan = FinanceCalculators.calculateLoan(BigDecimal("12000"), BigDecimal.ZERO, 12)
        assertEquals(12, loan.schedule.size)
        assertEquals(0, BigDecimal("1000.00").compareTo(loan.periodicPayment))
        assertEquals(0, BigDecimal("0.00").compareTo(loan.totalInterest))
        assertEquals(0, BigDecimal("12000.00").compareTo(loan.totalPayment))
        assertEquals(0, BigDecimal("0.00").compareTo(loan.schedule.last().endingBalance))
    }

    @Test(expected = ArithmeticException::class)
    fun testBreakEvenFailsWhenVariableCostExceedsPrice() {
        FinanceCalculators.calculateBreakEven(
            fixedCosts = BigDecimal("10000"),
            variableCostPerUnit = BigDecimal("60"),
            sellingPricePerUnit = BigDecimal("50")
        )
    }

    @Test
    fun testSavingsWithContributions() {
        // Initial 1,000, 100/mo, 6% annual, 1 year (12 months)
        val savings = FinanceCalculators.calculateSavings(
            initialDeposit = BigDecimal("1000"),
            monthlyDeposit = BigDecimal("100"),
            annualRatePercent = BigDecimal("6"),
            years = 1
        )
        // Total principal = 1000 + 1200 = 2200
        assertEquals(0, BigDecimal("2200.00").compareTo(savings.totalPrincipal))
        assertTrue(savings.totalInterestEarned > BigDecimal.ZERO)
        assertTrue(savings.finalBalance > savings.totalPrincipal)
    }

    @Test
    fun testTipBillSplitting() {
        val tip = FinanceCalculators.calculateTip(
            billAmount = BigDecimal("2000"),
            tipPercent = BigDecimal("15"),
            numberOfPeople = 4
        )
        // Tip = 300, Total = 2300, Per Person Total = 575, Per Person Tip = 75
        assertEquals(0, BigDecimal("300.00").compareTo(tip.tipAmount))
        assertEquals(0, BigDecimal("2300.00").compareTo(tip.totalBill))
        assertEquals(0, BigDecimal("575.00").compareTo(tip.perPersonTotal))
        assertEquals(0, BigDecimal("75.00").compareTo(tip.perPersonTip))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeLoanPrincipalThrowsException() {
        FinanceCalculators.calculateLoan(BigDecimal("-5000"), BigDecimal("5"), 12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeLoanRateThrowsException() {
        FinanceCalculators.calculateLoan(BigDecimal("5000"), BigDecimal("-5"), 12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeTaxRateThrowsException() {
        FinanceCalculators.calculateTax(BigDecimal("100"), BigDecimal("-10"), false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeSavingsDepositThrowsException() {
        FinanceCalculators.calculateSavings(BigDecimal("-100"), BigDecimal("50"), BigDecimal("5"), 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeTipPercentThrowsException() {
        FinanceCalculators.calculateTip(BigDecimal("100"), BigDecimal("-15"), 2)
    }
}

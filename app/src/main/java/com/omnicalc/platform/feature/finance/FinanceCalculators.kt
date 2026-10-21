package com.omnicalc.platform.feature.finance

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class DiscountResult(
    val amountSaved: BigDecimal,
    val finalPrice: BigDecimal
)

data class DiscountTaxResult(
    val amountSaved: BigDecimal,
    val taxAmount: BigDecimal,
    val finalPrice: BigDecimal
)

data class TaxResult(
    val netAmount: BigDecimal,
    val taxAmount: BigDecimal,
    val grossTotal: BigDecimal
)

data class AmortizationRow(
    val paymentNumber: Int,
    val beginningBalance: BigDecimal,
    val principalPayment: BigDecimal,
    val interestPayment: BigDecimal,
    val endingBalance: BigDecimal
)

data class LoanResult(
    val periodicPayment: BigDecimal,
    val totalInterest: BigDecimal,
    val totalPayment: BigDecimal,
    val schedule: List<AmortizationRow>
)

data class SavingsResult(
    val totalPrincipal: BigDecimal,
    val totalInterestEarned: BigDecimal,
    val finalBalance: BigDecimal
)

data class ProfitMarginResult(
    val grossProfit: BigDecimal,
    val profitMarginPercent: BigDecimal,
    val markupPercent: BigDecimal,
    val totalRevenue: BigDecimal,
    val totalCost: BigDecimal
)

data class BreakEvenResult(
    val breakEvenUnits: BigDecimal,
    val breakEvenRevenue: BigDecimal,
    val contributionMargin: BigDecimal,
    val contributionMarginRatio: BigDecimal
)

data class TipResult(
    val tipAmount: BigDecimal,
    val totalBill: BigDecimal,
    val perPersonTotal: BigDecimal,
    val perPersonTip: BigDecimal
)

object FinanceCalculators {
    private val MC = MathContext(18, RoundingMode.HALF_EVEN)

    fun calculateDiscount(originalPrice: BigDecimal, discountPercent: BigDecimal): DiscountResult {
        require(originalPrice >= BigDecimal.ZERO) { "Original price cannot be negative" }
        require(discountPercent >= BigDecimal.ZERO) { "Discount percentage cannot be negative" }

        val saved = originalPrice.multiply(discountPercent, MC).divide(BigDecimal(100), MC)
        val finalPrice = originalPrice.subtract(saved, MC).max(BigDecimal.ZERO)
        return DiscountResult(saved.setScale(2, RoundingMode.HALF_UP), finalPrice.setScale(2, RoundingMode.HALF_UP))
    }

    fun calculateDiscountAndTax(
        originalPrice: BigDecimal,
        discountPercent: BigDecimal,
        taxPercent: BigDecimal,
        taxAppliedAfterDiscount: Boolean
    ): DiscountTaxResult {
        require(originalPrice >= BigDecimal.ZERO) { "Price cannot be negative" }

        val discountAmount = originalPrice.multiply(discountPercent, MC).divide(BigDecimal(100), MC)

        return if (taxAppliedAfterDiscount) {
            val discountedPrice = originalPrice.subtract(discountAmount, MC)
            val taxAmount = discountedPrice.multiply(taxPercent, MC).divide(BigDecimal(100), MC)
            val finalPrice = discountedPrice.add(taxAmount, MC)
            DiscountTaxResult(
                discountAmount.setScale(2, RoundingMode.HALF_UP),
                taxAmount.setScale(2, RoundingMode.HALF_UP),
                finalPrice.setScale(2, RoundingMode.HALF_UP)
            )
        } else {
            val taxAmount = originalPrice.multiply(taxPercent, MC).divide(BigDecimal(100), MC)
            val finalPrice = originalPrice.add(taxAmount, MC).subtract(discountAmount, MC)
            DiscountTaxResult(
                discountAmount.setScale(2, RoundingMode.HALF_UP),
                taxAmount.setScale(2, RoundingMode.HALF_UP),
                finalPrice.setScale(2, RoundingMode.HALF_UP)
            )
        }
    }

    fun calculateTax(amount: BigDecimal, taxRatePercent: BigDecimal, isTaxInclusive: Boolean): TaxResult {
        require(amount >= BigDecimal.ZERO) { "Amount cannot be negative" }
        require(taxRatePercent >= BigDecimal.ZERO) { "Tax rate cannot be negative" }

        val hundred = BigDecimal(100)
        return if (!isTaxInclusive) {
            // Tax Exclusive
            val tax = amount.multiply(taxRatePercent, MC).divide(hundred, MC)
            val total = amount.add(tax, MC)
            TaxResult(
                netAmount = amount.setScale(2, RoundingMode.HALF_UP),
                taxAmount = tax.setScale(2, RoundingMode.HALF_UP),
                grossTotal = total.setScale(2, RoundingMode.HALF_UP)
            )
        } else {
            // Tax Inclusive
            val factor = BigDecimal.ONE.add(taxRatePercent.divide(hundred, MC), MC)
            val net = amount.divide(factor, MC)
            val tax = amount.subtract(net, MC)
            TaxResult(
                netAmount = net.setScale(2, RoundingMode.HALF_UP),
                taxAmount = tax.setScale(2, RoundingMode.HALF_UP),
                grossTotal = amount.setScale(2, RoundingMode.HALF_UP)
            )
        }
    }

    fun calculateLoan(
        principal: BigDecimal,
        annualRatePercent: BigDecimal,
        termMonths: Int
    ): LoanResult {
        require(principal > BigDecimal.ZERO) { "Principal must be greater than zero" }
        require(annualRatePercent >= BigDecimal.ZERO) { "Annual interest rate cannot be negative" }
        require(termMonths > 0) { "Loan term must be at least 1 month" }

        val monthlyPayment: BigDecimal
        val totalInterest: BigDecimal
        val totalPayment: BigDecimal
        val schedule = mutableListOf<AmortizationRow>()

        if (annualRatePercent.compareTo(BigDecimal.ZERO) == 0) {
            // Zero interest loan
            monthlyPayment = principal.divide(BigDecimal(termMonths), 2, RoundingMode.HALF_UP)
            totalPayment = principal
            totalInterest = BigDecimal.ZERO

            var balance = principal
            for (i in 1..termMonths) {
                val pPayment = if (i == termMonths) balance else monthlyPayment
                val endBalance = balance.subtract(pPayment).max(BigDecimal.ZERO)
                schedule.add(
                    AmortizationRow(
                        paymentNumber = i,
                        beginningBalance = balance.setScale(2, RoundingMode.HALF_UP),
                        principalPayment = pPayment.setScale(2, RoundingMode.HALF_UP),
                        interestPayment = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        endingBalance = endBalance.setScale(2, RoundingMode.HALF_UP)
                    )
                )
                balance = endBalance
            }
        } else {
            val r = annualRatePercent.divide(BigDecimal(1200), MC)
            val onePlusR = BigDecimal.ONE.add(r, MC)
            val factor = onePlusR.pow(termMonths, MC)
            val numerator = principal.multiply(r, MC).multiply(factor, MC)
            val denominator = factor.subtract(BigDecimal.ONE, MC)

            monthlyPayment = numerator.divide(denominator, 2, RoundingMode.HALF_UP)
            totalPayment = monthlyPayment.multiply(BigDecimal(termMonths))
            totalInterest = totalPayment.subtract(principal).max(BigDecimal.ZERO)

            var balance = principal
            for (i in 1..termMonths) {
                val interestPayment = balance.multiply(r, MC).setScale(2, RoundingMode.HALF_UP)
                val principalPayment = if (i == termMonths) balance else monthlyPayment.subtract(interestPayment).min(balance)
                val endBalance = balance.subtract(principalPayment).max(BigDecimal.ZERO)

                schedule.add(
                    AmortizationRow(
                        paymentNumber = i,
                        beginningBalance = balance.setScale(2, RoundingMode.HALF_UP),
                        principalPayment = principalPayment.setScale(2, RoundingMode.HALF_UP),
                        interestPayment = interestPayment.setScale(2, RoundingMode.HALF_UP),
                        endingBalance = endBalance.setScale(2, RoundingMode.HALF_UP)
                    )
                )
                balance = endBalance
            }
        }

        return LoanResult(
            periodicPayment = monthlyPayment.setScale(2, RoundingMode.HALF_UP),
            totalInterest = totalInterest.setScale(2, RoundingMode.HALF_UP),
            totalPayment = totalPayment.setScale(2, RoundingMode.HALF_UP),
            schedule = schedule
        )
    }

    fun calculateSavings(
        initialDeposit: BigDecimal,
        monthlyDeposit: BigDecimal,
        annualRatePercent: BigDecimal,
        years: Int
    ): SavingsResult {
        require(initialDeposit >= BigDecimal.ZERO) { "Initial deposit cannot be negative" }
        require(monthlyDeposit >= BigDecimal.ZERO) { "Monthly deposit cannot be negative" }
        require(annualRatePercent >= BigDecimal.ZERO) { "Annual rate cannot be negative" }
        require(years > 0) { "Years must be at least 1" }
        val months = years * 12
        val r = annualRatePercent.divide(BigDecimal(1200), MC)

        var balance = initialDeposit
        var totalPrincipal = initialDeposit

        for (i in 1..months) {
            val interestMonth = balance.multiply(r, MC)
            balance = balance.add(interestMonth, MC).add(monthlyDeposit, MC)
            totalPrincipal = totalPrincipal.add(monthlyDeposit, MC)
        }

        val totalInterest = balance.subtract(totalPrincipal).max(BigDecimal.ZERO)

        return SavingsResult(
            totalPrincipal = totalPrincipal.setScale(2, RoundingMode.HALF_UP),
            totalInterestEarned = totalInterest.setScale(2, RoundingMode.HALF_UP),
            finalBalance = balance.setScale(2, RoundingMode.HALF_UP)
        )
    }

    fun calculateProfitMargin(costPrice: BigDecimal, sellingPrice: BigDecimal, quantity: Int = 1): ProfitMarginResult {
        require(quantity > 0) { "Quantity must be greater than zero" }
        require(costPrice >= BigDecimal.ZERO) { "Cost cannot be negative" }
        require(sellingPrice >= BigDecimal.ZERO) { "Selling price cannot be negative" }

        val qBd = BigDecimal(quantity)
        val totalRevenue = sellingPrice.multiply(qBd, MC)
        val totalCost = costPrice.multiply(qBd, MC)
        val grossProfit = totalRevenue.subtract(totalCost, MC)

        val marginPercent = if (sellingPrice.compareTo(BigDecimal.ZERO) > 0) {
            grossProfit.divide(totalRevenue, MC).multiply(BigDecimal(100), MC)
        } else BigDecimal.ZERO

        val markupPercent = if (costPrice.compareTo(BigDecimal.ZERO) > 0) {
            grossProfit.divide(totalCost, MC).multiply(BigDecimal(100), MC)
        } else BigDecimal.ZERO

        return ProfitMarginResult(
            grossProfit = grossProfit.setScale(2, RoundingMode.HALF_UP),
            profitMarginPercent = marginPercent.setScale(2, RoundingMode.HALF_UP),
            markupPercent = markupPercent.setScale(2, RoundingMode.HALF_UP),
            totalRevenue = totalRevenue.setScale(2, RoundingMode.HALF_UP),
            totalCost = totalCost.setScale(2, RoundingMode.HALF_UP)
        )
    }

    fun calculateBreakEven(fixedCosts: BigDecimal, variableCostPerUnit: BigDecimal, sellingPricePerUnit: BigDecimal): BreakEvenResult {
        require(fixedCosts >= BigDecimal.ZERO) { "Fixed costs cannot be negative" }
        val cm = sellingPricePerUnit.subtract(variableCostPerUnit, MC)
        if (cm <= BigDecimal.ZERO) {
            throw ArithmeticException("Selling price must exceed variable cost to achieve break-even")
        }

        val units = fixedCosts.divide(cm, 0, RoundingMode.CEILING)
        val revenue = units.multiply(sellingPricePerUnit, MC)
        val cmRatio = cm.divide(sellingPricePerUnit, MC).multiply(BigDecimal(100), MC)

        return BreakEvenResult(
            breakEvenUnits = units,
            breakEvenRevenue = revenue.setScale(2, RoundingMode.HALF_UP),
            contributionMargin = cm.setScale(2, RoundingMode.HALF_UP),
            contributionMarginRatio = cmRatio.setScale(2, RoundingMode.HALF_UP)
        )
    }

    fun calculateTip(billAmount: BigDecimal, tipPercent: BigDecimal, numberOfPeople: Int = 1): TipResult {
        require(billAmount >= BigDecimal.ZERO) { "Bill amount cannot be negative" }
        require(tipPercent >= BigDecimal.ZERO) { "Tip percentage cannot be negative" }
        require(numberOfPeople > 0) { "Number of people must be at least 1" }

        val tip = billAmount.multiply(tipPercent, MC).divide(BigDecimal(100), MC)
        val total = billAmount.add(tip, MC)
        val peopleBd = BigDecimal(numberOfPeople)
        val perPersonTotal = total.divide(peopleBd, 2, RoundingMode.HALF_UP)
        val perPersonTip = tip.divide(peopleBd, 2, RoundingMode.HALF_UP)

        return TipResult(
            tipAmount = tip.setScale(2, RoundingMode.HALF_UP),
            totalBill = total.setScale(2, RoundingMode.HALF_UP),
            perPersonTotal = perPersonTotal,
            perPersonTip = perPersonTip
        )
    }
}

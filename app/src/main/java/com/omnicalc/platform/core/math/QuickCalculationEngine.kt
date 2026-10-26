package com.omnicalc.platform.core.math

import com.omnicalc.platform.core.currency.CurrencyCatalog
import com.omnicalc.platform.core.currency.CurrencyFormatter
import com.omnicalc.platform.core.currency.CurrencyRateSet
import com.omnicalc.platform.core.currency.FreshnessStatus
import com.omnicalc.platform.core.currency.RateSource
import com.omnicalc.platform.core.currency.StaticFallbackRateProvider
import com.omnicalc.platform.core.units.UnitConverterEngine
import com.omnicalc.platform.feature.finance.FinanceCalculators
import com.omnicalc.platform.feature.health.HealthCalculators
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class QuickCalculationResult(
    val query: String,
    val primaryResult: String,
    val secondaryResult: String? = null,
    val targetRoute: String? = null,
    val explanation: String? = null
)

object QuickCalculationEngine {

    private val evaluator = ExpressionEvaluator(angleMode = AngleMode.DEGREES)
    private val staticRates = StaticFallbackRateProvider()

    // 1. Natural Percentage: "20% of 45000", "20 % of 45,000", "15 percent of 200"
    private val PERCENT_OF_REGEX = Regex(
        """(?i)^\s*(\d[\d,]*(?:\.\d+)?)\s*(?:%|percent)\s+of\s+(\d[\d,]*(?:\.\d+)?)\s*$"""
    )

    // 2. Tax / VAT addition: "1000 + 16% VAT", "1000 plus 16% tax"
    private val TAX_ADD_REGEX = Regex(
        """(?i)^\s*(\d[\d,]*(?:\.\d+)?)\s*(?:\+|plus)\s*(\d[\d,]*(?:\.\d+)?)\s*(?:%|percent)?\s*(?:vat|tax)\s*$"""
    )

    // 3. Discount subtraction: "20000 - 15% discount", "20000 minus 15% discount", "5000 - 20% off"
    private val DISCOUNT_SUB_REGEX = Regex(
        """(?i)^\s*(\d[\d,]*(?:\.\d+)?)\s*(?:\-|minus)\s*(\d[\d,]*(?:\.\d+)?)\s*(?:%|percent)?\s*(?:discount|off)\s*$"""
    )

    // 4. Currency conversion: "100 USD to KES", "100 usd in kes", "500 EUR in KMF"
    private val CURRENCY_CONVERT_REGEX = Regex(
        """(?i)^\s*(\d[\d,]*(?:\.\d+)?)\s*([a-zA-Z]{3})\s+(?:to|in)\s+([a-zA-Z]{3})\s*$"""
    )

    // 5. Unit conversion: "10 km to miles", "100 c to f", "-20 C to F", "100 °C to °F"
    private val UNIT_CONVERT_REGEX = Regex(
        """(?i)^\s*([+-]?\d[\d,]*(?:\.\d+)?)\s*([a-zA-Z°/²³_-]+)\s+(?:to|in)\s+([a-zA-Z°/²³_-]+)\s*$"""
    )

    // 6. BMI quick expression: "66 kg / 1.75 m" or "bmi 70kg 175cm" or "70 / 1.75^2"
    private val BMI_EXPR_REGEX = Regex(
        """(?i)^\s*(\d[\d,]*(?:\.\d+)?)\s*(?:kg)?\s*/\s*(\d[\d,]*(?:\.\d+)?)\s*(?:m)?\s*$"""
    )

    /**
     * @param rateSet the app's current rates (downloaded or saved); when given it takes
     *        precedence over [liveRates] and its own freshness labels describe the result.
     */
    fun evaluateQuick(
        query: String,
        liveRates: Map<String, Double>? = null,
        overrides: Map<String, Double>? = null,
        rateSet: CurrencyRateSet? = null
    ): QuickCalculationResult? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        // Ambiguous lone percentage: "20%" or "20 %" without an operation or base
        if (trimmed.matches(Regex("""(?i)^\s*[+-]?\d+(?:\.\d+)?\s*(?:%|percent)\s*$"""))) {
            return null
        }

        // Try Natural Percentage: "20% of 45000" or "20 % of 45,000"
        PERCENT_OF_REGEX.matchEntire(trimmed)?.let { match ->
            val percent = BigDecimal(match.groupValues[1].replace(",", ""))
            val total = BigDecimal(match.groupValues[2].replace(",", ""))
            val result = total.multiply(percent).divide(BigDecimal(100), 4, RoundingMode.HALF_UP).stripTrailingZeros()
            return QuickCalculationResult(
                query = trimmed,
                primaryResult = result.toPlainString(),
                secondaryResult = "${percent.stripTrailingZeros().toPlainString()}% of ${total.stripTrailingZeros().toPlainString()} is ${result.toPlainString()}",
                targetRoute = "finance/discount",
                explanation = "Formula: (Percentage / 100) × Total"
            )
        }

        // Try Tax / VAT addition: "1000 + 16% VAT" or "1000 plus 16% tax"
        TAX_ADD_REGEX.matchEntire(trimmed)?.let { match ->
            val net = BigDecimal(match.groupValues[1].replace(",", ""))
            val taxRate = BigDecimal(match.groupValues[2].replace(",", ""))
            val taxResult = FinanceCalculators.calculateTax(net, taxRate, isTaxInclusive = false)
            return QuickCalculationResult(
                query = trimmed,
                primaryResult = taxResult.grossTotal.toPlainString(),
                secondaryResult = "Net: ${taxResult.netAmount.toPlainString()} • Tax (${taxRate.toPlainString()}%): ${taxResult.taxAmount.toPlainString()} • Total: ${taxResult.grossTotal.toPlainString()}",
                targetRoute = "finance/tax_vat",
                explanation = "Sales Tax / VAT addition"
            )
        }

        // Try Discount subtraction: "20000 - 15% discount" or "20000 minus 15% discount"
        DISCOUNT_SUB_REGEX.matchEntire(trimmed)?.let { match ->
            val original = BigDecimal(match.groupValues[1].replace(",", ""))
            val discountRate = BigDecimal(match.groupValues[2].replace(",", ""))
            val discountResult = FinanceCalculators.calculateDiscount(original, discountRate)
            return QuickCalculationResult(
                query = trimmed,
                primaryResult = discountResult.finalPrice.toPlainString(),
                secondaryResult = "Original: ${original.toPlainString()} • Saved: ${discountResult.amountSaved.toPlainString()} (${discountRate.toPlainString()}%) • Final: ${discountResult.finalPrice.toPlainString()}",
                targetRoute = "finance/discount",
                explanation = "Discount calculation"
            )
        }

        // Try Currency conversion: "100 USD to KES" or "500 EUR in KMF"
        CURRENCY_CONVERT_REGEX.matchEntire(trimmed)?.let { match ->
            val amount = BigDecimal(match.groupValues[1].replace(",", ""))
            val fromCode = match.groupValues[2].uppercase(Locale.ROOT)
            val toCode = match.groupValues[3].uppercase(Locale.ROOT)

            val fromInfo = CurrencyCatalog.find(fromCode)
            val toInfo = CurrencyCatalog.find(toCode)

            val rates = rateSet?.rates ?: liveRates ?: staticRates.getStaticRatesMap()
            val fromRate = overrides?.get(fromCode) ?: rates[fromCode] ?: StaticFallbackRateProvider.STATIC_RATES[fromCode]
            val toRate = overrides?.get(toCode) ?: rates[toCode] ?: StaticFallbackRateProvider.STATIC_RATES[toCode]

            if (fromRate != null && toRate != null && fromRate > 0.0 && toRate > 0.0) {
                val converted = amount.multiply(BigDecimal(toRate.toString()))
                    .divide(BigDecimal(fromRate.toString()), 4, RoundingMode.HALF_UP)
                val formatted = CurrencyCatalog.formatAmount(converted, toInfo)
                val fromRateBd = BigDecimal(fromRate.toString())
                val toRateBd = BigDecimal(toRate.toString())
                val ratePair = CurrencyFormatter.formatRate(
                    toRateBd.divide(fromRateBd, java.math.MathContext(18, RoundingMode.HALF_EVEN)),
                    CurrencyFormatter.rateSignificantDigits(fromRateBd, toRateBd)
                )

                val sourcesMap = mutableMapOf<String, RateSource>()
                if (liveRates != null) {
                    sourcesMap["USD"] = RateSource.LIVE
                    liveRates.keys.forEach { code -> sourcesMap[code] = RateSource.LIVE }
                    StaticFallbackRateProvider.STATIC_RATES.keys.forEach { code ->
                        if (!liveRates.containsKey(code) && code != "USD") {
                            sourcesMap[code] = RateSource.OFFLINE_REFERENCE
                        }
                    }
                } else {
                    StaticFallbackRateProvider.STATIC_RATES.keys.forEach { code ->
                        sourcesMap[code] = RateSource.OFFLINE_REFERENCE
                    }
                }

                val tempSet = CurrencyRateSet(
                    baseCurrency = "USD",
                    rates = rates,
                    timestampMs = System.currentTimeMillis(),
                    providerName = if (liveRates != null) "Live" else "Offline Reference",
                    status = if (liveRates != null) FreshnessStatus.CURRENT else FreshnessStatus.OFFLINE,
                    rateSources = sourcesMap
                )
                val resolution = (rateSet ?: tempSet).resolveConversion(fromCode, toCode, overrides ?: emptyMap())

                return QuickCalculationResult(
                    query = trimmed,
                    primaryResult = "$formatted ${toInfo.code}",
                    secondaryResult = "${amount.stripTrailingZeros().toPlainString()} ${fromInfo.code} = $formatted ${toInfo.code} (Rate: 1 ${fromInfo.code} = $ratePair ${toInfo.code})",
                    targetRoute = "currency",
                    explanation = resolution.summary
                )
            }
        }

        // Try Unit conversion: "10 km to miles", "-20 C to F", "100 °C to °F"
        UNIT_CONVERT_REGEX.matchEntire(trimmed)?.let { match ->
            val amount = BigDecimal(match.groupValues[1].replace(",", ""))
            val fromStr = match.groupValues[2].lowercase(Locale.ROOT)
            val toStr = match.groupValues[3].lowercase(Locale.ROOT)

            val fromUnit = resolveUnit(fromStr)
            val toUnit = resolveUnit(toStr)

            if (fromUnit != null && toUnit != null && fromUnit.dimension == toUnit.dimension) {
                val converted = UnitConverterEngine.convert(amount, fromUnit, toUnit)
                val formatted = UnitConverterEngine.formatResult(converted)
                return QuickCalculationResult(
                    query = trimmed,
                    primaryResult = "$formatted ${toUnit.symbol}",
                    secondaryResult = "${amount.stripTrailingZeros().toPlainString()} ${fromUnit.name} = $formatted ${toUnit.name}",
                    targetRoute = "units",
                    explanation = "Dimension: ${fromUnit.dimension.displayName}"
                )
            }
        }

        // Try BMI expression: "66 kg / 1.75 m"
        BMI_EXPR_REGEX.matchEntire(trimmed)?.let { match ->
            val wKg = BigDecimal(match.groupValues[1].replace(",", ""))
            val hM = BigDecimal(match.groupValues[2].replace(",", ""))
            if (hM > BigDecimal("0.5") && hM < BigDecimal("3.0") && wKg > BigDecimal("10") && wKg < BigDecimal("500")) {
                val bmi = HealthCalculators.calculateBmi(wKg, hM)
                return QuickCalculationResult(
                    query = trimmed,
                    primaryResult = "BMI ${bmi.bmi.toPlainString()} (${bmi.category.label})",
                    secondaryResult = "Healthy weight for this height: ${bmi.healthyWeightMinKg.toPlainString()} – ${bmi.healthyWeightMaxKg.toPlainString()} kg",
                    targetRoute = "health/bmi",
                    explanation = "Formula: weight (kg) / [height (m)]²"
                )
            }
        }

        // Try General Expression evaluation: e.g. "20000 * 0.15" or "(125 * 48) / 2" or "45000 * 20%"
        when (val eval = evaluator.evaluate(trimmed)) {
            is EvalResult.Success -> {
                // If it contains operators, return result
                if (trimmed.any { it in "+-*/×÷^%!" }) {
                    return QuickCalculationResult(
                        query = trimmed,
                        primaryResult = eval.formatted,
                        secondaryResult = "$trimmed = ${eval.formatted}",
                        targetRoute = "calculator/basic",
                        explanation = "Standard Arithmetic"
                    )
                }
            }
            is EvalResult.Error -> {
                // Not a valid quick expression
            }
        }

        return null
    }

    private val UNIT_ALIASES: Map<String, String> = mapOf(
        "miles" to "mi",
        "mile" to "mi",
        "km" to "km",
        "kms" to "km",
        "kilometer" to "km",
        "kilometers" to "km",
        "kilometre" to "km",
        "kilometres" to "km",
        "m" to "m",
        "meter" to "m",
        "meters" to "m",
        "metre" to "m",
        "metres" to "m",
        "cm" to "cm",
        "centimeter" to "cm",
        "centimeters" to "cm",
        "mm" to "mm",
        "millimeter" to "mm",
        "millimeters" to "mm",
        "in" to "in",
        "inch" to "in",
        "inches" to "in",
        "ft" to "ft",
        "foot" to "ft",
        "feet" to "ft",
        "yd" to "yd",
        "yard" to "yd",
        "yards" to "yd",
        "kg" to "kg",
        "kgs" to "kg",
        "kilo" to "kg",
        "kilos" to "kg",
        "kilogram" to "kg",
        "kilograms" to "kg",
        "g" to "g",
        "gram" to "g",
        "grams" to "g",
        "mg" to "mg",
        "milligram" to "mg",
        "milligrams" to "mg",
        "lb" to "lb",
        "lbs" to "lb",
        "pound" to "lb",
        "pounds" to "lb",
        "oz" to "oz",
        "ounce" to "oz",
        "ounces" to "oz",
        "l" to "l",
        "liter" to "l",
        "liters" to "l",
        "litre" to "l",
        "litres" to "l",
        "ml" to "ml",
        "milliliter" to "ml",
        "milliliters" to "ml",
        "gal" to "gal_us",
        "gallon" to "gal_us",
        "gallons" to "gal_us",
        "c" to "c",
        "celsius" to "c",
        "centigrade" to "c",
        "°c" to "c",
        "f" to "f",
        "fahrenheit" to "f",
        "°f" to "f",
        "k" to "k",
        "kelvin" to "k",
        "s" to "s",
        "sec" to "s",
        "second" to "s",
        "seconds" to "s",
        "min" to "min",
        "minute" to "min",
        "minutes" to "min",
        "h" to "h",
        "hr" to "h",
        "hour" to "h",
        "hours" to "h",
        "d" to "d",
        "day" to "d",
        "days" to "d",
        "wk" to "wk",
        "week" to "wk",
        "weeks" to "wk",
        "yr" to "yr",
        "year" to "yr",
        "years" to "yr"
    )

    private fun resolveUnit(query: String): com.omnicalc.platform.core.units.UnitDefinition? {
        val q = query.lowercase(Locale.ROOT).trim()

        // 1. Direct ID match
        UnitConverterEngine.findUnit(q)?.let { return it }

        // 2. Direct symbol / name match
        UnitConverterEngine.ALL_UNITS.find {
            it.name.lowercase(Locale.ROOT) == q || it.symbol.lowercase(Locale.ROOT) == q
        }?.let { return it }

        // 3. Known aliases map
        val aliasId = UNIT_ALIASES[q]
        if (aliasId != null) {
            UnitConverterEngine.findUnit(aliasId)?.let { return it }
        }

        // 4. Plural strip: miles -> mile, inches -> inch
        val singular = when {
            q.endsWith("ies") -> q.dropLast(3) + "y"
            q.endsWith("es") && (q.endsWith("shes") || q.endsWith("ches") || q.endsWith("sses") || q.endsWith("xes")) -> q.dropLast(2)
            q.endsWith("s") -> q.dropLast(1)
            else -> q
        }
        UnitConverterEngine.ALL_UNITS.find {
            it.name.lowercase(Locale.ROOT) == singular || it.id.lowercase(Locale.ROOT) == singular
        }?.let { return it }

        val singularAlias = UNIT_ALIASES[singular]
        if (singularAlias != null) {
            UnitConverterEngine.findUnit(singularAlias)?.let { return it }
        }

        // 5. Prefix match
        return UnitConverterEngine.ALL_UNITS.find {
            it.name.lowercase(Locale.ROOT).startsWith(singular)
        }
    }
}

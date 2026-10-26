package com.omnicalc.platform.core.currency

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Universal Currency Number Formatter.
 *
 * Provides:
 * - Thousands grouping with commas for all world currencies (e.g. 1,000; 10,000; 67,380.59; 1,377,781.11; 10,000,000)
 * - Decimal precision preservation and typing-friendly partial string formatting (e.g. "500.", "-0.5")
 * - Total separation of presentation from mathematical values: internal calculations always operate on pure numeric BigDecimals
 * - Safe sanitization and parsing to prevent comma-induced calculation bugs
 */
object CurrencyFormatter {

    /**
     * Formats a raw numeric string with thousands commas grouping.
     * Preserves negative sign, fractional digits, and trailing dot during user input.
     */
    fun formatString(raw: String): String {
        if (raw.isBlank()) return ""
        val clean = raw.replace(",", "").replace(" ", "").trim()
        if (clean == "-" || clean == "." || clean == "-.") return clean

        val isNegative = clean.startsWith("-")
        val withoutSign = if (isNegative) clean.substring(1) else clean

        val parts = withoutSign.split(".", limit = 2)
        val intPart = parts[0]
        val hasDot = parts.size > 1 || clean.endsWith(".")
        val decPart = if (parts.size > 1) parts[1] else ""

        val formattedInt = if (intPart.isEmpty()) {
            "0"
        } else {
            val sb = StringBuilder()
            val len = intPart.length
            for (i in 0 until len) {
                if (i > 0 && (len - i) % 3 == 0) {
                    sb.append(',')
                }
                sb.append(intPart[i])
            }
            sb.toString()
        }

        val result = StringBuilder()
        if (isNegative) result.append('-')
        result.append(formattedInt)
        if (hasDot) {
            result.append('.')
            result.append(decPart)
        }
        return result.toString()
    }

    /**
     * Formats a BigDecimal amount with exact currency decimal scaling and thousands grouping.
     */
    fun formatBigDecimal(amount: BigDecimal, decimalPlaces: Int = 2): String {
        val rounded = amount.setScale(decimalPlaces, RoundingMode.HALF_EVEN)
        return formatString(rounded.toPlainString())
    }

    /**
     * Strips commas, spaces, and formatting characters to return a clean numeric string for math evaluation.
     */
    fun sanitizeToNumeric(formatted: String): String {
        return formatted.replace(",", "").replace(" ", "").trim()
    }

    /**
     * Formats an exchange rate without hiding its decimals: 129.84 stays "129.84", never "130".
     *
     * A rate taken directly from the provider keeps all of its digits. A rate derived by
     * division (a cross rate such as KES -> UGX) has no exact decimal form, so it is limited to
     * [significantDigits] (but never rounds the integer part away), which avoids binary-style
     * noise like 129.8399999997. Exact values show at least two decimals ("3,550.00").
     */
    fun formatRate(rate: BigDecimal, significantDigits: Int = MAX_RATE_SIGNIFICANT_DIGITS): String {
        if (rate.signum() <= 0) return "0.00"
        val integerDigits = (rate.precision() - rate.scale()).coerceAtLeast(0)
        val digits = significantDigits.coerceIn(MIN_RATE_SIGNIFICANT_DIGITS, MAX_RATE_SIGNIFICANT_DIGITS)
            .coerceAtLeast(integerDigits)
        val value = rate.round(java.math.MathContext(digits, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        // Padding zeros is only truthful for an exact value; after rounding it would claim
        // precision the rate does not have.
        val exact = value.compareTo(rate) == 0
        val format = DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)).apply {
            minimumFractionDigits = if (exact) 2 else 0
            maximumFractionDigits = value.scale().coerceAtLeast(2)
            isParseBigDecimal = true
        }
        return format.format(value)
    }

    /**
     * Significant digits worth showing for a cross rate fromRate -> toRate: as many as the
     * more precise of the two provider values, so USD -> KES (1 and 129.84) shows 129.84 and
     * KES -> USD shows 0.0077018.
     */
    fun rateSignificantDigits(vararg providerRates: BigDecimal): Int =
        providerRates.maxOfOrNull { it.stripTrailingZeros().precision() } ?: MAX_RATE_SIGNIFICANT_DIGITS

    const val MIN_RATE_SIGNIFICANT_DIGITS = 5
    const val MAX_RATE_SIGNIFICANT_DIGITS = 15

    /**
     * Safely parses a formatted or unformatted string into a BigDecimal.
     * Returns BigDecimal.ZERO on blank or incomplete input.
     */
    fun parseToBigDecimal(raw: String): BigDecimal {
        val clean = sanitizeToNumeric(raw)
        return try {
            if (clean.isBlank() || clean == "-" || clean == "." || clean == "-.") {
                BigDecimal.ZERO
            } else {
                BigDecimal(clean)
            }
        } catch (_: Exception) {
            BigDecimal.ZERO
        }
    }
}

package com.omnicalc.platform.core.currency

import java.math.BigDecimal
import java.math.RoundingMode

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

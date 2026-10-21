package com.omnicalc.platform.core.math

/**
 * High-performance, mathematical-integrity display formatter for calculator numbers and expressions.
 *
 * Strictly separates display presentation (e.g. "1,000") from internal mathematical
 * representation ("1000"). Preserves decimal fractions, trailing decimal points while typing,
 * negative numbers, and scientific notations without corrupting evaluation state.
 */
object CalculatorDisplayFormatter {

    private val NUMBER_TOKEN_REGEX = Regex("""(?<![\w.])(\d+)(\.\d*)?([eE][+-]?\d+)?""")

    /**
     * Formats an entire calculator expression for display.
     * Every individual number token is formatted with thousands grouping separators.
     * Function names (e.g. "log10", "atan2"), operators, and symbols are preserved.
     */
    fun formatExpression(expression: String, useGrouping: Boolean = true): String {
        if (!useGrouping || expression.isEmpty()) return expression

        return NUMBER_TOKEN_REGEX.replace(expression) { matchResult ->
            val intPart = matchResult.groupValues[1]
            val decPart = matchResult.groupValues[2] // e.g. ".5" or "." or ""
            val expPart = matchResult.groupValues[3] // e.g. "E12" or ""

            val formattedInt = formatIntegerDigits(intPart)
            formattedInt + decPart + expPart
        }
    }

    /**
     * Formats a calculation result or live preview string.
     */
    fun formatResult(result: String, useGrouping: Boolean = true): String {
        if (!useGrouping || result.isEmpty()) return result
        return formatExpression(result, useGrouping = true)
    }

    /**
     * Inserts grouping commas every 3 digits from the right into a non-empty string of pure digits.
     */
    fun formatIntegerDigits(digits: String): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder(digits.length + (digits.length / 3))
        val remainder = digits.length % 3
        if (remainder > 0) {
            sb.append(digits, 0, remainder)
        }
        for (i in remainder until digits.length step 3) {
            if (sb.isNotEmpty()) {
                sb.append(',')
            }
            sb.append(digits, i, i + 3)
        }
        return sb.toString()
    }

    /**
     * Strips grouping commas that occur between digits (e.g., "1,000" -> "1000"),
     * ensuring expressions can be safely evaluated even if pasted or restored with commas.
     * Preserves commas in multi-argument functions like "pow(2, 3)".
     */
    fun sanitizeForEvaluation(input: String): String {
        if (input.isEmpty()) return input
        // Remove commas preceded by digit and followed by 3 digits
        return input.replace(Regex("""(?<=\d),(?=\d{3})"""), "")
    }
}

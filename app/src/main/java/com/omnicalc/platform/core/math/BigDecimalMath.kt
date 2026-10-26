package com.omnicalc.platform.core.math

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object BigDecimalMath {
    val DEFAULT_MATH_CONTEXT = MathContext(34, RoundingMode.HALF_EVEN)
    val DISPLAY_MATH_CONTEXT = MathContext(16, RoundingMode.HALF_EVEN)

    // A double carries 15 reliable significant decimal digits; anything beyond is binary noise
    // (e.g. sin(30°) = 0.49999999999999994).
    private val DOUBLE_RESULT_MATH_CONTEXT = MathContext(15, RoundingMode.HALF_EVEN)

    /** Converts a double computed by java.lang.Math into a BigDecimal without its binary noise. */
    fun fromDouble(value: Double): BigDecimal =
        BigDecimal.valueOf(value).round(DOUBLE_RESULT_MATH_CONTEXT)

    fun add(a: BigDecimal, b: BigDecimal, mc: MathContext = DEFAULT_MATH_CONTEXT): BigDecimal =
        a.add(b, mc)

    fun subtract(a: BigDecimal, b: BigDecimal, mc: MathContext = DEFAULT_MATH_CONTEXT): BigDecimal =
        a.subtract(b, mc)

    fun multiply(a: BigDecimal, b: BigDecimal, mc: MathContext = DEFAULT_MATH_CONTEXT): BigDecimal =
        a.multiply(b, mc)

    fun divide(a: BigDecimal, b: BigDecimal, mc: MathContext = DEFAULT_MATH_CONTEXT): BigDecimal {
        if (b.compareTo(BigDecimal.ZERO) == 0) {
            throw ArithmeticException("Cannot divide by zero")
        }
        return a.divide(b, mc)
    }

    fun sqrt(value: BigDecimal, mc: MathContext = DEFAULT_MATH_CONTEXT): BigDecimal {
        if (value.signum() < 0) {
            throw ArithmeticException("Square root of negative number is undefined in real numbers")
        }
        if (value.signum() == 0) return BigDecimal.ZERO

        // Newton-Raphson approximation for high precision
        var x = BigDecimal(Math.sqrt(value.toDouble()), mc)
        val two = BigDecimal(2)
        val tolerance = BigDecimal("1E-" + (mc.precision - 2))

        for (i in 0 until 50) {
            val nextX = x.add(value.divide(x, mc), mc).divide(two, mc)
            if (nextX.subtract(x, mc).abs().compareTo(tolerance) <= 0) {
                return nextX
            }
            x = nextX
        }
        return x
    }

    fun power(base: BigDecimal, exponent: BigDecimal, mc: MathContext = DEFAULT_MATH_CONTEXT): BigDecimal {
        // If exponent is integer
        try {
            val intExp = exponent.toBigIntegerExact()
            if (intExp >= BigInteger.valueOf(Int.MIN_VALUE.toLong()) && intExp <= BigInteger.valueOf(Int.MAX_VALUE.toLong())) {
                val exp = intExp.toInt()
                if (exp == 0) return BigDecimal.ONE
                if (exp < 0) {
                    return BigDecimal.ONE.divide(base.pow(-exp, mc), mc)
                }
                return base.pow(exp, mc)
            }
        } catch (_: ArithmeticException) {
            // Fractional exponent
        }

        val baseD = base.toDouble()
        val expD = exponent.toDouble()
        if (baseD < 0 && expD % 1.0 != 0.0) {
            throw ArithmeticException("Negative base with non-integer exponent is undefined in real numbers")
        }
        val result = Math.pow(baseD, expD)
        if (result.isNaN() || result.isInfinite()) {
            throw ArithmeticException("Calculation resulted in overflow or undefined number")
        }
        return fromDouble(result)
    }

    fun factorial(n: Int): BigDecimal {
        if (n < 0) throw ArithmeticException("Factorial is undefined for negative numbers")
        if (n > 5000) throw ArithmeticException("Factorial operand too large (max 5000)")
        var result = BigInteger.ONE
        for (i in 2..n) {
            result = result.multiply(BigInteger.valueOf(i.toLong()))
        }
        return BigDecimal(result)
    }

    fun format(
        value: BigDecimal,
        decimals: Int = 4,
        useGrouping: Boolean = true,
        stripTrailingZeros: Boolean = true,
        locale: Locale = Locale.getDefault()
    ): String {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val df = DecimalFormat().apply {
            decimalFormatSymbols = symbols
            isGroupingUsed = useGrouping
            maximumFractionDigits = decimals
            minimumFractionDigits = if (stripTrailingZeros) 0 else decimals
        }
        return df.format(value)
    }
}

package com.omnicalc.platform.core.math

import java.math.BigDecimal
import java.math.BigInteger

data class Fraction(val num: BigInteger, val den: BigInteger) {
    init {
        require(den != BigInteger.ZERO) { "Denominator cannot be zero" }
    }

    val simplified: Fraction by lazy {
        val gcd = num.gcd(den)
        var sNum = num.divide(gcd)
        var sDen = den.divide(gcd)
        if (sDen < BigInteger.ZERO) {
            sNum = sNum.negate()
            sDen = sDen.negate()
        }
        Fraction(sNum, sDen)
    }

    val isInteger: Boolean get() = simplified.den == BigInteger.ONE

    fun toMixed(): MixedFraction {
        val s = simplified
        val whole = s.num.divide(s.den)
        val remainder = s.num.remainder(s.den).abs()
        return MixedFraction(whole, remainder, s.den)
    }

    operator fun plus(other: Fraction): Fraction {
        val newNum = this.num.multiply(other.den).add(other.num.multiply(this.den))
        val newDen = this.den.multiply(other.den)
        return Fraction(newNum, newDen).simplified
    }

    operator fun minus(other: Fraction): Fraction {
        val newNum = this.num.multiply(other.den).subtract(other.num.multiply(this.den))
        val newDen = this.den.multiply(other.den)
        return Fraction(newNum, newDen).simplified
    }

    operator fun times(other: Fraction): Fraction {
        return Fraction(this.num.multiply(other.num), this.den.multiply(other.den)).simplified
    }

    operator fun div(other: Fraction): Fraction {
        require(other.num != BigInteger.ZERO) { "Cannot divide by zero fraction" }
        return Fraction(this.num.multiply(other.den), this.den.multiply(other.num)).simplified
    }

    fun toDouble(): Double {
        return num.toDouble() / den.toDouble()
    }

    fun toBigDecimal(scale: Int = 10): BigDecimal {
        return BigDecimal(num).divide(BigDecimal(den), scale, java.math.RoundingMode.HALF_EVEN)
    }

    override fun toString(): String {
        val s = simplified
        return if (s.den == BigInteger.ONE) {
            s.num.toString()
        } else {
            "${s.num}/${s.den}"
        }
    }

    companion object {
        val ZERO = Fraction(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Fraction(BigInteger.ONE, BigInteger.ONE)

        fun of(num: Long, den: Long): Fraction =
            Fraction(BigInteger.valueOf(num), BigInteger.valueOf(den)).simplified

        /**
         * Converts a decimal to fraction using continued fraction approximation
         */
        fun fromDecimal(value: Double, maxDenominator: Long = 1000000L): Fraction {
            if (value.isNaN() || value.isInfinite()) {
                throw IllegalArgumentException("Invalid decimal value: $value")
            }

            val isNegative = value < 0
            var x = Math.abs(value)

            var h1 = 1L
            var h0 = 0L
            var k1 = 0L
            var k0 = 1L

            var b = x
            do {
                val a = b.toLong()
                var aux = h1
                h1 = a * h1 + h0
                h0 = aux

                aux = k1
                k1 = a * k1 + k0
                k0 = aux

                if (Math.abs(x - h1.toDouble() / k1.toDouble()) <= 1e-12) break
                val diff = b - a.toDouble()
                if (Math.abs(diff) < 1e-12) break
                b = 1.0 / diff
            } while (k1 <= maxDenominator && b < 1e12)

            val finalNum = if (isNegative) -h1 else h1
            return of(finalNum, k1)
        }
    }
}

data class MixedFraction(val whole: BigInteger, val num: BigInteger, val den: BigInteger) {
    override fun toString(): String {
        return when {
            num == BigInteger.ZERO -> whole.toString()
            whole == BigInteger.ZERO -> "$num/$den"
            else -> "$whole $num/$den"
        }
    }
}

package com.omnicalc.platform.feature.geometry

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.acos
import kotlin.math.sqrt

data class TriangleSolution(
    val isValid: Boolean,
    val area: Double = 0.0,
    val perimeter: Double = 0.0,
    val angleA: Double = 0.0,
    val angleB: Double = 0.0,
    val angleC: Double = 0.0,
    val type: String = "",
    val errorMessage: String? = null
)

object GeometryCalculators {

    private val PI = BigDecimal("3.141592653589793")

    fun calculateShape(
        shape: ShapeType,
        p1: BigDecimal,
        p2: BigDecimal = BigDecimal.ZERO,
        p3: BigDecimal = BigDecimal.ZERO
    ): Pair<String, String>? {
        if (p1 <= BigDecimal.ZERO || p2 < BigDecimal.ZERO || p3 < BigDecimal.ZERO) return null

        return when (shape) {
            ShapeType.CIRCLE -> {
                val r = p1
                val area = PI.multiply(r).multiply(r).setScale(4, RoundingMode.HALF_UP)
                val perim = BigDecimal(2).multiply(PI).multiply(r).setScale(4, RoundingMode.HALF_UP)
                Pair("Area: ${area.toPlainString()}", "Circumference: ${perim.toPlainString()}")
            }
            ShapeType.RECTANGLE -> {
                if (p2 <= BigDecimal.ZERO) return null
                val l = p1
                val w = p2
                val area = l.multiply(w).setScale(4, RoundingMode.HALF_UP)
                val perim = BigDecimal(2).multiply(l.add(w)).setScale(4, RoundingMode.HALF_UP)
                Pair("Area: ${area.toPlainString()}", "Perimeter: ${perim.toPlainString()}")
            }
            ShapeType.TRIANGLE -> {
                if (p2 <= BigDecimal.ZERO) return null
                val b = p1
                val h = p2
                val area = b.multiply(h).multiply(BigDecimal("0.5")).setScale(4, RoundingMode.HALF_UP)
                Pair("Area: ${area.toPlainString()}", "Formula: ½ × base × height")
            }
            ShapeType.TRAPEZOID -> {
                if (p2 <= BigDecimal.ZERO || p3 <= BigDecimal.ZERO) return null
                val a = p1
                val b = p2
                val h = p3
                val area = a.add(b).multiply(BigDecimal("0.5")).multiply(h).setScale(4, RoundingMode.HALF_UP)
                Pair("Area: ${area.toPlainString()}", "Formula: ½ × (a + b) × h")
            }
            ShapeType.CYLINDER -> {
                if (p2 <= BigDecimal.ZERO) return null
                val r = p1
                val h = p2
                val vol = PI.multiply(r).multiply(r).multiply(h).setScale(4, RoundingMode.HALF_UP)
                val sa = BigDecimal(2).multiply(PI).multiply(r).multiply(r.add(h)).setScale(4, RoundingMode.HALF_UP)
                Pair("Volume: ${vol.toPlainString()}", "Total Surface Area: ${sa.toPlainString()}")
            }
            ShapeType.SPHERE -> {
                val r = p1
                val vol = BigDecimal(4).divide(BigDecimal(3), 10, RoundingMode.HALF_UP)
                    .multiply(PI).multiply(r).multiply(r).multiply(r).setScale(4, RoundingMode.HALF_UP)
                val sa = BigDecimal(4).multiply(PI).multiply(r).multiply(r).setScale(4, RoundingMode.HALF_UP)
                Pair("Volume: ${vol.toPlainString()}", "Surface Area: ${sa.toPlainString()}")
            }
            ShapeType.CONE -> {
                if (p2 <= BigDecimal.ZERO) return null
                val r = p1
                val h = p2
                val vol = BigDecimal(1).divide(BigDecimal(3), 10, RoundingMode.HALF_UP)
                    .multiply(PI).multiply(r).multiply(r).multiply(h).setScale(4, RoundingMode.HALF_UP)
                val slant = sqrt(r.toDouble() * r.toDouble() + h.toDouble() * h.toDouble())
                val sa = PI.multiply(r).multiply(r.add(BigDecimal(slant))).setScale(4, RoundingMode.HALF_UP)
                Pair("Volume: ${vol.toPlainString()}", "Surface Area: ${sa.toPlainString()}")
            }
            ShapeType.CUBOID -> {
                if (p2 <= BigDecimal.ZERO || p3 <= BigDecimal.ZERO) return null
                val l = p1
                val w = p2
                val h = p3
                val vol = l.multiply(w).multiply(h).setScale(4, RoundingMode.HALF_UP)
                val sa = BigDecimal(2).multiply(
                    l.multiply(w).add(l.multiply(h)).add(w.multiply(h))
                ).setScale(4, RoundingMode.HALF_UP)
                Pair("Volume: ${vol.toPlainString()}", "Surface Area: ${sa.toPlainString()}")
            }
        }
    }

    fun solveTriangleSss(a: Double, b: Double, c: Double): TriangleSolution {
        if (a <= 0.0 || b <= 0.0 || c <= 0.0) {
            return TriangleSolution(
                isValid = false,
                errorMessage = "All side lengths must be strictly greater than zero."
            )
        }

        // Validate Triangle Inequality: sum of any two sides must strictly exceed third
        if (a + b <= c || a + c <= b || b + c <= a) {
            return TriangleSolution(
                isValid = false,
                errorMessage = "These three side lengths cannot form a valid triangle. The sum of any two sides must exceed the third side."
            )
        }

        // Heron's Formula
        val s = (a + b + c) / 2.0
        val area = sqrt(s * (s - a) * (s - b) * (s - c))
        val perimeter = a + b + c

        // Law of Cosines for angles
        val cosA = (b * b + c * c - a * a) / (2.0 * b * c)
        val cosB = (a * a + c * c - b * b) / (2.0 * a * c)
        val cosC = (a * a + b * b - c * c) / (2.0 * a * b)

        val angleA = Math.toDegrees(acos(cosA.coerceIn(-1.0, 1.0)))
        val angleB = Math.toDegrees(acos(cosB.coerceIn(-1.0, 1.0)))
        val angleC = Math.toDegrees(acos(cosC.coerceIn(-1.0, 1.0)))

        val type = when {
            Math.abs(angleA - 90.0) < 1e-4 || Math.abs(angleB - 90.0) < 1e-4 || Math.abs(angleC - 90.0) < 1e-4 -> "Right-angled Triangle"
            angleA > 90.0 || angleB > 90.0 || angleC > 90.0 -> "Obtuse Triangle"
            else -> "Acute Triangle"
        }

        return TriangleSolution(
            isValid = true,
            area = area,
            perimeter = perimeter,
            angleA = angleA,
            angleB = angleB,
            angleC = angleC,
            type = type
        )
    }
}

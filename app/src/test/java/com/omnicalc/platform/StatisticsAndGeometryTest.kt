package com.omnicalc.platform

import com.omnicalc.platform.feature.geometry.GeometryCalculators
import com.omnicalc.platform.feature.geometry.ShapeType
import com.omnicalc.platform.feature.statistics.StatisticsCalculators
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class StatisticsAndGeometryTest {

    // --- Statistics Tests ---

    @Test
    fun testEmptyStatisticsReturnsNull() {
        val result = StatisticsCalculators.calculate(emptyList())
        assertNull(result)
    }

    @Test
    fun testSingleElementStatisticsHasNullSampleVariance() {
        val result = StatisticsCalculators.calculate(listOf(42.0))
        assertNotNull(result)
        assertEquals(1, result!!.count)
        assertEquals(42.0, result.mean, 1e-6)
        assertEquals(42.0, result.median, 1e-6)
        assertEquals(42.0, result.min, 1e-6)
        assertEquals(42.0, result.max, 1e-6)
        assertEquals(0.0, result.range, 1e-6)
        assertEquals(0.0, result.popVariance, 1e-6)
        assertEquals(0.0, result.popStdDev, 1e-6)
        // With n=1, sample variance denominator (n-1) is 0, so it must safely be null
        assertNull(result.sampleVariance)
        assertNull(result.sampleStdDev)
    }

    @Test
    fun testMultiElementStatisticsVarianceDistinction() {
        // Dataset: [2, 4, 4, 4, 5, 5, 7, 9] (n = 8, sum = 40, mean = 5.0)
        // Deviations: -3, -1, -1, -1, 0, 0, 2, 4
        // Squared dev: 9 + 1 + 1 + 1 + 0 + 0 + 4 + 16 = 32
        // Population variance: 32 / 8 = 4.0, Pop StdDev = 2.0
        // Sample variance: 32 / 7 = 4.5714..., Sample StdDev = 2.1380...
        val data = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        val result = StatisticsCalculators.calculate(data)
        assertNotNull(result)
        assertEquals(8, result!!.count)
        assertEquals(40.0, result.sum, 1e-6)
        assertEquals(5.0, result.mean, 1e-6)
        assertEquals(4.5, result.median, 1e-6)
        assertEquals(listOf(4.0), result.modes)
        assertEquals(4.0, result.popVariance, 1e-6)
        assertEquals(2.0, result.popStdDev, 1e-6)
        assertNotNull(result.sampleVariance)
        assertEquals(32.0 / 7.0, result.sampleVariance!!, 1e-4)
    }

    // --- Geometry Tests ---

    @Test
    fun testCircleCalculations() {
        val res = GeometryCalculators.calculateShape(ShapeType.CIRCLE, BigDecimal("5"))
        assertNotNull(res)
        assertTrue(res!!.first.contains("78.5398")) // Area = pi * 25
        assertTrue(res.second.contains("31.4159")) // Circumference = 2 * pi * 5
    }

    @Test
    fun testRectangleCalculations() {
        val res = GeometryCalculators.calculateShape(ShapeType.RECTANGLE, BigDecimal("4"), BigDecimal("5"))
        assertNotNull(res)
        assertTrue(res!!.first.contains("20.0000"))
        assertTrue(res.second.contains("18.0000"))
    }

    @Test
    fun testSphereCalculations() {
        val res = GeometryCalculators.calculateShape(ShapeType.SPHERE, BigDecimal("3"))
        assertNotNull(res)
        assertTrue(res!!.first.contains("113.0973")) // Vol = 4/3 * pi * 27
        assertTrue(res.second.contains("113.0973")) // SA = 4 * pi * 9
    }

    @Test
    fun testInvalidGeometryInputsReturnNull() {
        // Negative radius
        assertNull(GeometryCalculators.calculateShape(ShapeType.CIRCLE, BigDecimal("-5")))
        // Zero radius
        assertNull(GeometryCalculators.calculateShape(ShapeType.CIRCLE, BigDecimal.ZERO))
        // Negative rectangle width
        assertNull(GeometryCalculators.calculateShape(ShapeType.RECTANGLE, BigDecimal("5"), BigDecimal("-2")))
    }

    @Test
    fun testTriangleSolverSssValidRightTriangle() {
        val sol = GeometryCalculators.solveTriangleSss(3.0, 4.0, 5.0)
        assertTrue(sol.isValid)
        assertEquals(6.0, sol.area, 1e-4)
        assertEquals(12.0, sol.perimeter, 1e-4)
        assertEquals(90.0, sol.angleC, 1e-2)
        assertEquals("Right-angled Triangle", sol.type)
        assertNull(sol.errorMessage)
    }

    @Test
    fun testTriangleSolverInequalityFailure() {
        // 1 + 2 = 3 <= 10 (Cannot close triangle)
        val sol = GeometryCalculators.solveTriangleSss(1.0, 2.0, 10.0)
        assertFalse(sol.isValid)
        assertTrue(sol.errorMessage!!.contains("cannot form a valid triangle"))
    }

    @Test
    fun testTriangleSolverDegenerateLineFailure() {
        // 2 + 3 = 5 (Degenerate straight line, not a triangle)
        val sol = GeometryCalculators.solveTriangleSss(2.0, 3.0, 5.0)
        assertFalse(sol.isValid)
    }

    @Test
    fun testTriangleSolverNegativeSideFailure() {
        val sol = GeometryCalculators.solveTriangleSss(-3.0, 4.0, 5.0)
        assertFalse(sol.isValid)
        assertTrue(sol.errorMessage!!.contains("strictly greater than zero"))
    }
}

package com.omnicalc.platform

import com.omnicalc.platform.core.units.Dimension
import com.omnicalc.platform.core.units.UnitConverterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.math.BigDecimal

class UnitConverterEngineTest {

    @Test
    fun testLengthConversion() {
        val km = UnitConverterEngine.findUnit("km")!!
        val m = UnitConverterEngine.findUnit("m")!!
        val mi = UnitConverterEngine.findUnit("mi")!!

        // 1 km = 1000 m
        val meters = UnitConverterEngine.convert(BigDecimal.ONE, km, m)
        assertEquals(0, BigDecimal("1000").compareTo(meters))

        // 10 km to miles ≈ 6.21371
        val miles = UnitConverterEngine.convert(BigDecimal("10"), km, mi)
        assertEquals(6.21371, miles.toDouble(), 1e-4)
    }

    @Test
    fun testLengthReversibility() {
        val m = UnitConverterEngine.findUnit("m")!!
        val ft = UnitConverterEngine.findUnit("ft")!!

        val original = BigDecimal("42.5")
        val converted = UnitConverterEngine.convert(original, m, ft)
        val roundtrip = UnitConverterEngine.convert(converted, ft, m)

        assertEquals(original.toDouble(), roundtrip.toDouble(), 1e-6)
    }

    @Test
    fun testTemperatureConversion() {
        val c = UnitConverterEngine.findUnit("c")!!
        val f = UnitConverterEngine.findUnit("f")!!
        val k = UnitConverterEngine.findUnit("k")!!

        // 100 °C = 212 °F
        val boilingF = UnitConverterEngine.convert(BigDecimal("100"), c, f)
        assertEquals(212.0, boilingF.toDouble(), 1e-4)

        // 0 °C = 32 °F
        val freezingF = UnitConverterEngine.convert(BigDecimal.ZERO, c, f)
        assertEquals(32.0, freezingF.toDouble(), 1e-4)

        // 0 °C = 273.15 K
        val zeroCInK = UnitConverterEngine.convert(BigDecimal.ZERO, c, k)
        assertEquals(273.15, zeroCInK.toDouble(), 1e-4)
    }

    @Test
    fun testVolumeGallonDistinction() {
        val galUs = UnitConverterEngine.findUnit("gal_us")!!
        val galImp = UnitConverterEngine.findUnit("gal_imp")!!
        val liter = UnitConverterEngine.findUnit("l")!!

        val litersInUsGal = UnitConverterEngine.convert(BigDecimal.ONE, galUs, liter)
        val litersInImpGal = UnitConverterEngine.convert(BigDecimal.ONE, galImp, liter)

        // Verify US gallon (3.785 L) is NOT equal to Imperial gallon (4.546 L)
        assertNotEquals(litersInUsGal.toDouble(), litersInImpGal.toDouble(), 0.1)
        assertEquals(3.78541, litersInUsGal.toDouble(), 1e-4)
        assertEquals(4.54609, litersInImpGal.toDouble(), 1e-4)
    }

    @Test
    fun testDataStorageBinaryVsDecimal() {
        val mb = UnitConverterEngine.findUnit("mb")!!
        val mib = UnitConverterEngine.findUnit("mib")!!
        val b = UnitConverterEngine.findUnit("byte")!!

        // 1 MB = 1,000,000 bytes
        val bytesInMb = UnitConverterEngine.convert(BigDecimal.ONE, mb, b)
        assertEquals(1000000.0, bytesInMb.toDouble(), 1.0)

        // 1 MiB = 1,048,576 bytes
        val bytesInMib = UnitConverterEngine.convert(BigDecimal.ONE, mib, b)
        assertEquals(1048576.0, bytesInMib.toDouble(), 1.0)
    }

    @Test
    fun testFuelEconomyReciprocal() {
        val l100 = UnitConverterEngine.findUnit("l_100km")!!
        val kmL = UnitConverterEngine.findUnit("km_l")!!

        // 8 L/100km = 100 / 8 = 12.5 km/L
        val res = UnitConverterEngine.convert(BigDecimal("8"), l100, kmL)
        assertEquals(12.5, res.toDouble(), 1e-4)

        // 10 km/L = 100 / 10 = 10 L/100km
        val back = UnitConverterEngine.convert(BigDecimal("10"), kmL, l100)
        assertEquals(10.0, back.toDouble(), 1e-4)
    }
}

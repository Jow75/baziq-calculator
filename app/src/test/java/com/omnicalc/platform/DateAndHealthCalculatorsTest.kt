package com.omnicalc.platform

import com.omnicalc.platform.core.math.NumberBase
import com.omnicalc.platform.core.math.ProgrammerState
import com.omnicalc.platform.core.math.WordSize
import com.omnicalc.platform.core.registry.ToolRegistry
import com.omnicalc.platform.feature.dates.DateCalculators
import com.omnicalc.platform.feature.health.ActivityLevel
import com.omnicalc.platform.feature.health.BmiCategory
import com.omnicalc.platform.feature.health.HealthCalculators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class DateAndHealthCalculatorsTest {

    @Test
    fun testLeapYearDateDifference() {
        val feb28 = LocalDate.of(2024, 2, 28)
        val mar1 = LocalDate.of(2024, 3, 1)

        val diff = DateCalculators.calculateDateDifference(feb28, mar1)
        // 2024 is a leap year, so Feb 28 to Mar 1 is 2 days (Feb 29 exists)
        assertEquals(2L, diff.totalDays)
    }

    @Test
    fun testDateAdditionLeapYear() {
        val feb28 = LocalDate.of(2024, 2, 28)
        val nextDay = DateCalculators.addSubtractDate(feb28, days = 1, months = 0, years = 0, isAdd = true)
        assertEquals(LocalDate.of(2024, 2, 29), nextDay)
    }

    @Test
    fun testOvernightTimeDuration() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(6, 0)
        val duration = DateCalculators.calculateTimeDuration(start, end)
        assertEquals(8L, duration.hours)
        assertEquals(0L, duration.minutes)
        assertEquals(480L, duration.totalMinutes)
    }

    @Test
    fun testBmiCalculation() {
        val weight = BigDecimal("70")
        val height = BigDecimal("1.75")
        val res = HealthCalculators.calculateBmi(weight, height)

        assertEquals(0, BigDecimal("22.9").compareTo(res.bmi))
        assertEquals(BmiCategory.NORMAL, res.category)
    }

    @Test
    fun testBmrAndTdee() {
        val res = HealthCalculators.calculateBmrAndTdee(
            weightKg = 70.0,
            heightCm = 175.0,
            ageYears = 25,
            isMale = true,
            activityLevel = ActivityLevel.SEDENTARY
        )
        // BMR: 10*70 + 6.25*175 - 5*25 + 5 = 700 + 1093.75 - 125 + 5 = 1673.75 ≈ 1674 kcal
        assertEquals(1674, res.bmrCalories)
        // TDEE: 1673.75 * 1.2 = 2008.5 -> rounds to 2009
        assertEquals(2009, res.tdeeCalories)
    }

    @Test
    fun testProgrammerEngineConversions() {
        val state = ProgrammerState.fromString("255", NumberBase.DEC, WordSize.BYTE)
        assertEquals("FF", state.hexString)
        assertEquals("255", state.decUnsignedString)
        assertEquals("377", state.octString)
        assertEquals("1111 1111", state.binString)

        // Test bitwise NOT on BYTE
        val notState = state.not()
        assertEquals("0", notState.hexString)
    }

    @Test
    fun testUniversalSearch() {
        val bmiResults = ToolRegistry.search("bmi")
        assertTrue(bmiResults.isNotEmpty())
        assertEquals("bmi_calc", bmiResults.first().id)

        val kesResults = ToolRegistry.search("kes")
        assertTrue(kesResults.isNotEmpty())
        assertEquals("currency_converter", kesResults.first().id)

        val loanResults = ToolRegistry.search("loan")
        assertTrue(loanResults.isNotEmpty())
        assertEquals("loan_calc", loanResults.first().id)

        val hexResults = ToolRegistry.search("hex")
        assertTrue(hexResults.isNotEmpty())
        assertEquals("programmer_calc", hexResults.first().id)
    }

    @Test
    fun testAgeCalculation() {
        val birthDate = LocalDate.of(2000, 1, 1)
        val targetDate = LocalDate.of(2025, 1, 1)
        val age = DateCalculators.calculateAge(birthDate, targetDate)

        assertEquals(25, age.years)
        assertEquals(0, age.months)
        assertEquals(0, age.days)
        assertTrue(age.totalDays > 9000L)
    }

    @Test
    fun testLeapDayBirthdayInNonLeapYear() {
        val leapBirthDate = LocalDate.of(2020, 2, 29)
        val nonLeapTarget = LocalDate.of(2023, 1, 1)
        val age = DateCalculators.calculateAge(leapBirthDate, nonLeapTarget)
        assertEquals(2, age.years)
        assertTrue(age.daysUntilNextBirthday > 0)
    }

    @Test
    fun testTimezoneConversion() {
        val ldt = java.time.LocalDateTime.of(2026, 6, 15, 12, 0) // Summer
        val nairobiZone = java.time.ZoneId.of("Africa/Nairobi") // UTC+3
        val londonZone = java.time.ZoneId.of("Europe/London") // BST = UTC+1

        val converted = DateCalculators.convertTimeZone(ldt, nairobiZone, londonZone)
        // 12:00 in Nairobi (UTC+3) is 10:00 in London (BST, UTC+1)
        assertEquals(10, converted.hour)
    }

    @Test
    fun testOvulationEstimation() {
        val lastPeriod = LocalDate.of(2026, 5, 1)
        val result = HealthCalculators.estimateOvulation(lastPeriod, cycleLengthDays = 28)

        // Next period = May 29
        assertEquals(LocalDate.of(2026, 5, 29), result.nextPeriodDate)
        // Ovulation = May 29 - 14 = May 15
        assertEquals(LocalDate.of(2026, 5, 15), result.estimatedOvulationDate)
        // Fertile window = May 10 to May 16
        assertEquals(LocalDate.of(2026, 5, 10), result.fertileWindowStart)
        assertEquals(LocalDate.of(2026, 5, 16), result.fertileWindowEnd)
    }

    @Test
    fun testDstSpringForwardDuration() {
        val londonZone = java.time.ZoneId.of("Europe/London")
        // London springs forward on Sunday, March 29, 2026 at 01:00 UTC -> 02:00 BST
        val beforeDst = java.time.ZonedDateTime.of(2026, 3, 29, 0, 30, 0, 0, londonZone)
        val afterDst = java.time.ZonedDateTime.of(2026, 3, 29, 2, 30, 0, 0, londonZone)

        val duration = DateCalculators.calculateZonedDuration(beforeDst, afterDst)
        // Wall clock time says 2 hours, but physical elapsed duration across DST jump is exactly 1 hour (60 minutes)
        assertEquals(60L, duration.toMinutes())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeBmiWeightThrowsException() {
        HealthCalculators.calculateBmi(BigDecimal("-70"), BigDecimal("1.75"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testZeroBmiHeightThrowsException() {
        HealthCalculators.calculateBmi(BigDecimal("70"), BigDecimal.ZERO)
    }
}

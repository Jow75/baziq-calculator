package com.omnicalc.platform.feature.health

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

enum class BmiCategory(val label: String, val description: String) {
    UNDERWEIGHT("Underweight", "BMI less than 18.5"),
    NORMAL("Normal Weight", "BMI between 18.5 and 24.9"),
    OVERWEIGHT("Overweight", "BMI between 25.0 and 29.9"),
    OBESE_CLASS_1("Obesity Class I", "BMI between 30.0 and 34.9"),
    OBESE_CLASS_2("Obesity Class II", "BMI between 35.0 and 39.9"),
    OBESE_CLASS_3("Obesity Class III", "BMI 40.0 or higher")
}

data class BmiResult(
    val bmi: BigDecimal,
    val category: BmiCategory,
    val healthyWeightMinKg: BigDecimal,
    val healthyWeightMaxKg: BigDecimal
)

enum class ActivityLevel(val displayName: String, val multiplier: Double) {
    SEDENTARY("Sedentary (Little or no exercise)", 1.2),
    LIGHTLY_ACTIVE("Lightly Active (Exercise 1-3 days/week)", 1.375),
    MODERATELY_ACTIVE("Moderately Active (Exercise 3-5 days/week)", 1.55),
    VERY_ACTIVE("Very Active (Hard exercise 6-7 days/week)", 1.725),
    EXTRA_ACTIVE("Extra Active (Intense training / physical job)", 1.9)
}

data class BmrTdeeResult(
    val bmrCalories: Int,
    val tdeeCalories: Int
)

data class OvulationResult(
    val estimatedOvulationDate: LocalDate,
    val fertileWindowStart: LocalDate,
    val fertileWindowEnd: LocalDate,
    val nextPeriodDate: LocalDate
)

object HealthCalculators {
    private val MC = MathContext(10, RoundingMode.HALF_EVEN)

    fun calculateBmi(weightKg: BigDecimal, heightM: BigDecimal): BmiResult {
        require(weightKg > BigDecimal.ZERO) { "Weight must be greater than zero" }
        require(heightM > BigDecimal.ZERO) { "Height must be greater than zero" }

        val heightSq = heightM.multiply(heightM, MC)
        val bmi = weightKg.divide(heightSq, 1, RoundingMode.HALF_UP)
        val bmiD = bmi.toDouble()

        val category = when {
            bmiD < 18.5 -> BmiCategory.UNDERWEIGHT
            bmiD < 25.0 -> BmiCategory.NORMAL
            bmiD < 30.0 -> BmiCategory.OVERWEIGHT
            bmiD < 35.0 -> BmiCategory.OBESE_CLASS_1
            bmiD < 40.0 -> BmiCategory.OBESE_CLASS_2
            else -> BmiCategory.OBESE_CLASS_3
        }

        // Healthy weight range (BMI 18.5 - 24.9)
        val minHealthy = BigDecimal("18.5").multiply(heightSq, MC).setScale(1, RoundingMode.HALF_UP)
        val maxHealthy = BigDecimal("24.9").multiply(heightSq, MC).setScale(1, RoundingMode.HALF_UP)

        return BmiResult(bmi, category, minHealthy, maxHealthy)
    }

    fun calculateBmrAndTdee(
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        isMale: Boolean,
        activityLevel: ActivityLevel
    ): BmrTdeeResult {
        require(weightKg > 0) { "Weight must be positive" }
        require(heightCm > 0) { "Height must be positive" }
        require(ageYears > 0) { "Age must be positive" }

        // Mifflin-St Jeor formula
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        val bmr = if (isMale) base + 5.0 else base - 161.0
        val tdee = bmr * activityLevel.multiplier

        return BmrTdeeResult(
            bmrCalories = Math.round(bmr).toInt(),
            tdeeCalories = Math.round(tdee).toInt()
        )
    }

    fun estimateOvulation(
        lastPeriodDate: LocalDate,
        cycleLengthDays: Int = 28
    ): OvulationResult {
        require(cycleLengthDays in 20..45) { "Cycle length typically ranges between 20 and 45 days" }

        val nextPeriod = lastPeriodDate.plusDays(cycleLengthDays.toLong())
        // Standard calendar approximation: luteal phase is approximately 14 days
        val ovulation = nextPeriod.minusDays(14)
        val fertileStart = ovulation.minusDays(5)
        val fertileEnd = ovulation.plusDays(1)

        return OvulationResult(
            estimatedOvulationDate = ovulation,
            fertileWindowStart = fertileStart,
            fertileWindowEnd = fertileEnd,
            nextPeriodDate = nextPeriod
        )
    }
}

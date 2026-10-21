package com.omnicalc.platform.feature.statistics

import kotlin.math.sqrt

data class StatisticsResult(
    val count: Int,
    val sum: Double,
    val mean: Double,
    val median: Double,
    val modes: List<Double>,
    val range: Double,
    val min: Double,
    val max: Double,
    val popVariance: Double,
    val popStdDev: Double,
    val sampleVariance: Double?,
    val sampleStdDev: Double?
)

object StatisticsCalculators {

    fun calculate(tokens: List<Double>): StatisticsResult? {
        if (tokens.isEmpty()) return null

        val n = tokens.size
        val sum = tokens.sum()
        val mean = sum / n
        val sorted = tokens.sorted()
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        val min = sorted.first()
        val max = sorted.last()
        val range = max - min

        // Mode
        val counts = tokens.groupingBy { it }.eachCount()
        val maxCount = counts.values.maxOrNull() ?: 1
        val modes = counts.filter { it.value == maxCount && maxCount > 1 }.keys.toList()

        // Sum of squared deviations
        val ssd = tokens.sumOf { (it - mean) * (it - mean) }
        val popVariance = ssd / n
        val popStdDev = sqrt(popVariance)

        val sampleVariance = if (n > 1) ssd / (n - 1) else null
        val sampleStdDev = sampleVariance?.let { sqrt(it) }

        return StatisticsResult(
            count = n,
            sum = sum,
            mean = mean,
            median = median,
            modes = modes,
            range = range,
            min = min,
            max = max,
            popVariance = popVariance,
            popStdDev = popStdDev,
            sampleVariance = sampleVariance,
            sampleStdDev = sampleStdDev
        )
    }
}

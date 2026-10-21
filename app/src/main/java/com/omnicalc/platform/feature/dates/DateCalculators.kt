package com.omnicalc.platform.feature.dates

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class AgeResult(
    val years: Int,
    val months: Int,
    val days: Int,
    val totalDays: Long,
    val totalWeeks: Long,
    val totalHours: Long,
    val daysUntilNextBirthday: Long,
    val nextBirthdayDayOfWeek: String
)

data class DateDiffResult(
    val years: Int,
    val months: Int,
    val days: Int,
    val totalDays: Long
)

data class TimeDurationResult(
    val hours: Long,
    val minutes: Long,
    val totalMinutes: Long
)

object DateCalculators {

    fun calculateAge(birthDate: LocalDate, targetDate: LocalDate = LocalDate.now()): AgeResult {
        require(!targetDate.isBefore(birthDate)) { "Target date cannot be before birth date" }

        val period = Period.between(birthDate, targetDate)
        val totalDays = ChronoUnit.DAYS.between(birthDate, targetDate)
        val totalWeeks = totalDays / 7
        val totalHours = totalDays * 24

        // Next birthday calculation
        var nextBday = birthDate.withYear(targetDate.year)
        if (nextBday.isBefore(targetDate) || nextBday.isEqual(targetDate)) {
            nextBday = birthDate.withYear(targetDate.year + 1)
        }
        val daysUntilNext = ChronoUnit.DAYS.between(targetDate, nextBday)
        val dayOfWeek = nextBday.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }

        return AgeResult(
            years = period.years,
            months = period.months,
            days = period.days,
            totalDays = totalDays,
            totalWeeks = totalWeeks,
            totalHours = totalHours,
            daysUntilNextBirthday = daysUntilNext,
            nextBirthdayDayOfWeek = dayOfWeek
        )
    }

    fun calculateDateDifference(startDate: LocalDate, endDate: LocalDate): DateDiffResult {
        val (first, second) = if (startDate.isAfter(endDate)) Pair(endDate, startDate) else Pair(startDate, endDate)
        val period = Period.between(first, second)
        val totalDays = ChronoUnit.DAYS.between(first, second)

        return DateDiffResult(
            years = period.years,
            months = period.months,
            days = period.days,
            totalDays = totalDays
        )
    }

    fun addSubtractDate(
        startDate: LocalDate,
        days: Long,
        months: Long,
        years: Long,
        isAdd: Boolean
    ): LocalDate {
        return if (isAdd) {
            startDate.plusYears(years).plusMonths(months).plusDays(days)
        } else {
            startDate.minusYears(years).minusMonths(months).minusDays(days)
        }
    }

    fun calculateTimeDuration(startTime: LocalTime, endTime: LocalTime): TimeDurationResult {
        var startSeconds = startTime.toSecondOfDay()
        var endSeconds = endTime.toSecondOfDay()

        // Handle overnight rollover (e.g. 22:00 to 06:00)
        if (endSeconds < startSeconds) {
            endSeconds += 24 * 3600
        }

        val diffSeconds = endSeconds - startSeconds
        val hours = diffSeconds / 3600
        val minutes = (diffSeconds % 3600) / 60
        val totalMinutes = diffSeconds / 60

        return TimeDurationResult(
            hours = hours.toLong(),
            minutes = minutes.toLong(),
            totalMinutes = totalMinutes.toLong()
        )
    }

    fun convertTimeZone(
        dateTime: LocalDateTime,
        fromZone: ZoneId,
        toZone: ZoneId
    ): ZonedDateTime {
        val zonedFrom = dateTime.atZone(fromZone)
        return zonedFrom.withZoneSameInstant(toZone)
    }

    fun calculateZonedDuration(start: ZonedDateTime, end: ZonedDateTime): Duration {
        return Duration.between(start, end)
    }
}

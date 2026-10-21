package com.omnicalc.platform.feature.dates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnicalc.platform.ui.components.NumberInputField
import com.omnicalc.platform.ui.components.ResultCard
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var birthYear by remember { mutableStateOf("2000") }
    var birthMonth by remember { mutableStateOf("1") }
    var birthDay by remember { mutableStateOf("1") }

    val ageResult = remember(birthYear, birthMonth, birthDay) {
        try {
            val y = birthYear.toInt()
            val m = birthMonth.toInt()
            val d = birthDay.toInt()
            val bDate = LocalDate.of(y, m, d)
            DateCalculators.calculateAge(bDate)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Age Calculator", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Date of Birth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInputField(value = birthDay, onValueChange = { birthDay = it }, label = "Day (1-31)", modifier = Modifier.weight(1f))
                NumberInputField(value = birthMonth, onValueChange = { birthMonth = it }, label = "Month (1-12)", modifier = Modifier.weight(1f))
                NumberInputField(value = birthYear, onValueChange = { birthYear = it }, label = "Year", modifier = Modifier.weight(1.5f))
            }

            if (ageResult != null) {
                ResultCard(
                    title = "Exact Age",
                    primaryValue = "${ageResult.years} yrs, ${ageResult.months} mos, ${ageResult.days} days",
                    secondaryValue = "Total Days: ${ageResult.totalDays} • Total Weeks: ${ageResult.totalWeeks} • Total Hours: ${ageResult.totalHours}"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Next Birthday Countdown",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${ageResult.daysUntilNextBirthday} days remaining (falls on a ${ageResult.nextBirthdayDayOfWeek})",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDiffScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var startYear by remember { mutableStateOf("2024") }
    var startMonth by remember { mutableStateOf("1") }
    var startDay by remember { mutableStateOf("1") }

    var endYear by remember { mutableStateOf("2024") }
    var endMonth by remember { mutableStateOf("12") }
    var endDay by remember { mutableStateOf("31") }

    val diffResult = remember(startYear, startMonth, startDay, endYear, endMonth, endDay) {
        try {
            val d1 = LocalDate.of(startYear.toInt(), startMonth.toInt(), startDay.toInt())
            val d2 = LocalDate.of(endYear.toInt(), endMonth.toInt(), endDay.toInt())
            DateCalculators.calculateDateDifference(d1, d2)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Date Difference", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Start Date", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInputField(value = startDay, onValueChange = { startDay = it }, label = "Day", modifier = Modifier.weight(1f))
                NumberInputField(value = startMonth, onValueChange = { startMonth = it }, label = "Month", modifier = Modifier.weight(1f))
                NumberInputField(value = startYear, onValueChange = { startYear = it }, label = "Year", modifier = Modifier.weight(1.5f))
            }

            Text("End Date", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInputField(value = endDay, onValueChange = { endDay = it }, label = "Day", modifier = Modifier.weight(1f))
                NumberInputField(value = endMonth, onValueChange = { endMonth = it }, label = "Month", modifier = Modifier.weight(1f))
                NumberInputField(value = endYear, onValueChange = { endYear = it }, label = "Year", modifier = Modifier.weight(1.5f))
            }

            if (diffResult != null) {
                ResultCard(
                    title = "Difference Between Dates",
                    primaryValue = "${diffResult.years} years, ${diffResult.months} months, ${diffResult.days} days",
                    secondaryValue = "Total elapsed calendar days: ${diffResult.totalDays} days"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDurationScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var startHour by remember { mutableStateOf("22") }
    var startMin by remember { mutableStateOf("00") }
    var endHour by remember { mutableStateOf("06") }
    var endMin by remember { mutableStateOf("30") }

    val durationResult = remember(startHour, startMin, endHour, endMin) {
        try {
            val t1 = LocalTime.of(startHour.toInt(), startMin.toInt())
            val t2 = LocalTime.of(endHour.toInt(), endMin.toInt())
            DateCalculators.calculateTimeDuration(t1, t2)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time Duration", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Start Time (24h)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInputField(value = startHour, onValueChange = { startHour = it }, label = "Hour (0-23)", modifier = Modifier.weight(1f))
                NumberInputField(value = startMin, onValueChange = { startMin = it }, label = "Minute (0-59)", modifier = Modifier.weight(1f))
            }

            Text("End Time (24h)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInputField(value = endHour, onValueChange = { endHour = it }, label = "Hour (0-23)", modifier = Modifier.weight(1f))
                NumberInputField(value = endMin, onValueChange = { endMin = it }, label = "Minute (0-59)", modifier = Modifier.weight(1f))
            }

            if (durationResult != null) {
                ResultCard(
                    title = "Elapsed Duration",
                    primaryValue = "${durationResult.hours} hours ${durationResult.minutes} minutes",
                    secondaryValue = "Total minutes: ${durationResult.totalMinutes} min",
                    explanation = "Accurately handles overnight transitions (e.g. 22:00 to 06:30)."
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimezoneScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val commonZones = listOf(
        "Africa/Nairobi" to "Nairobi (EAT, UTC+3)",
        "UTC" to "Universal Coordinated Time (UTC)",
        "Europe/London" to "London (GMT/BST)",
        "America/New_York" to "New York (EST/EDT)",
        "America/Los_Angeles" to "Los Angeles (PST/PDT)",
        "Asia/Dubai" to "Dubai (GST, UTC+4)",
        "Asia/Tokyo" to "Tokyo (JST, UTC+9)",
        "Asia/Kolkata" to "India (IST, UTC+5:30)",
        "Europe/Paris" to "Paris / Frankfurt (CET/CEST)",
        "Australia/Sydney" to "Sydney (AEST/AEDT)"
    )

    var fromZoneId by remember { mutableStateOf("Africa/Nairobi") }
    var toZoneId by remember { mutableStateOf("Europe/London") }

    val now = remember { java.time.LocalDateTime.now() }
    var hourStr by remember { mutableStateOf(now.hour.toString().padStart(2, '0')) }
    var minStr by remember { mutableStateOf(now.minute.toString().padStart(2, '0')) }
    var dayStr by remember { mutableStateOf(now.dayOfMonth.toString()) }
    var monthStr by remember { mutableStateOf(now.monthValue.toString()) }
    var yearStr by remember { mutableStateOf(now.year.toString()) }

    var expandedFrom by remember { mutableStateOf(false) }
    var expandedTo by remember { mutableStateOf(false) }

    val result = remember(fromZoneId, toZoneId, hourStr, minStr, dayStr, monthStr, yearStr) {
        try {
            val ldt = java.time.LocalDateTime.of(
                yearStr.toInt(),
                monthStr.toInt(),
                dayStr.toInt(),
                hourStr.toInt(),
                minStr.toInt()
            )
            val zFrom = java.time.ZoneId.of(fromZoneId)
            val zTo = java.time.ZoneId.of(toZoneId)
            val converted = DateCalculators.convertTimeZone(ldt, zFrom, zTo)

            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy • HH:mm (z)")
            val fromFormatted = ldt.atZone(zFrom).format(formatter)
            val toFormatted = converted.format(formatter)

            val offsetDiffHours = (converted.offset.totalSeconds - ldt.atZone(zFrom).offset.totalSeconds) / 3600.0

            Triple(toFormatted, fromFormatted, offsetDiffHours)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time Zone Converter", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Source Date & Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInputField(value = dayStr, onValueChange = { dayStr = it }, label = "Day", modifier = Modifier.weight(1f))
                NumberInputField(value = monthStr, onValueChange = { monthStr = it }, label = "Month", modifier = Modifier.weight(1f))
                NumberInputField(value = yearStr, onValueChange = { yearStr = it }, label = "Year", modifier = Modifier.weight(1.5f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInputField(value = hourStr, onValueChange = { hourStr = it }, label = "Hour (0-23)", modifier = Modifier.weight(1f))
                NumberInputField(value = minStr, onValueChange = { minStr = it }, label = "Minute (0-59)", modifier = Modifier.weight(1f))
            }

            Text("From Timezone", fontWeight = FontWeight.Medium)
            OutlinedCard(
                onClick = { expandedFrom = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = commonZones.find { it.first == fromZoneId }?.second ?: fromZoneId,
                    modifier = Modifier.padding(14.dp),
                    fontWeight = FontWeight.Medium
                )
                DropdownMenu(expanded = expandedFrom, onDismissRequest = { expandedFrom = false }) {
                    commonZones.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                fromZoneId = id
                                expandedFrom = false
                            }
                        )
                    }
                }
            }

            Text("To Timezone", fontWeight = FontWeight.Medium)
            OutlinedCard(
                onClick = { expandedTo = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = commonZones.find { it.first == toZoneId }?.second ?: toZoneId,
                    modifier = Modifier.padding(14.dp),
                    fontWeight = FontWeight.Medium
                )
                DropdownMenu(expanded = expandedTo, onDismissRequest = { expandedTo = false }) {
                    commonZones.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                toZoneId = id
                                expandedTo = false
                            }
                        )
                    }
                }
            }

            if (result != null) {
                val diffStr = if (result.third >= 0) "+${result.third} hrs ahead" else "${result.third} hrs behind"
                ResultCard(
                    title = "Converted Target Time",
                    primaryValue = result.first,
                    secondaryValue = "Origin: ${result.second} ($diffStr)",
                    explanation = "Accurately computes official IANA daylight savings and timezone transitions."
                )
            }
        }
    }
}


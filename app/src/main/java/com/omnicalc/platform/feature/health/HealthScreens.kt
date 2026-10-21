package com.omnicalc.platform.feature.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnicalc.platform.ui.components.DisclaimerCard
import com.omnicalc.platform.ui.components.NumberInputField
import com.omnicalc.platform.ui.components.ResultCard
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BmiScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var isMetric by remember { mutableStateOf(true) }
    var heightCmStr by remember { mutableStateOf("175") }
    var weightKgStr by remember { mutableStateOf("70") }

    var heightFtStr by remember { mutableStateOf("5") }
    var heightInStr by remember { mutableStateOf("9") }
    var weightLbsStr by remember { mutableStateOf("154") }

    val bmiResult = remember(isMetric, heightCmStr, weightKgStr, heightFtStr, heightInStr, weightLbsStr) {
        try {
            if (isMetric) {
                val hM = BigDecimal(heightCmStr).divide(BigDecimal(100), 4, RoundingMode.HALF_EVEN)
                val wKg = BigDecimal(weightKgStr)
                HealthCalculators.calculateBmi(wKg, hM)
            } else {
                val totalInches = BigDecimal(heightFtStr).multiply(BigDecimal(12)).add(BigDecimal(heightInStr))
                val hM = totalInches.multiply(BigDecimal("0.0254"))
                val wKg = BigDecimal(weightLbsStr).multiply(BigDecimal("0.45359237"))
                HealthCalculators.calculateBmi(wKg, hM)
            }
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BMI Calculator", fontWeight = FontWeight.SemiBold) },
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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isMetric,
                    onClick = { isMetric = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Metric (cm / kg)") }
                SegmentedButton(
                    selected = !isMetric,
                    onClick = { isMetric = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Imperial (ft, in / lbs)") }
            }

            if (isMetric) {
                NumberInputField(value = heightCmStr, onValueChange = { heightCmStr = it }, label = "Height", suffix = "cm")
                NumberInputField(value = weightKgStr, onValueChange = { weightKgStr = it }, label = "Weight", suffix = "kg")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInputField(value = heightFtStr, onValueChange = { heightFtStr = it }, label = "Height (ft)", modifier = Modifier.weight(1f))
                    NumberInputField(value = heightInStr, onValueChange = { heightInStr = it }, label = "Inches", modifier = Modifier.weight(1f))
                }
                NumberInputField(value = weightLbsStr, onValueChange = { weightLbsStr = it }, label = "Weight", suffix = "lbs")
            }

            if (bmiResult != null) {
                val categoryColor = when (bmiResult.category) {
                    BmiCategory.NORMAL -> Color(0xFF10B981)
                    BmiCategory.UNDERWEIGHT -> Color(0xFFF59E0B)
                    BmiCategory.OVERWEIGHT -> Color(0xFFF97316)
                    else -> Color(0xFFEF4444)
                }

                ResultCard(
                    title = "Body Mass Index",
                    primaryValue = "${bmiResult.bmi.toPlainString()} kg/m²",
                    secondaryValue = "Category: ${bmiResult.category.label} (${bmiResult.category.description})",
                    explanation = "Healthy weight range for this height: ${bmiResult.healthyWeightMinKg.toPlainString()} – ${bmiResult.healthyWeightMaxKg.toPlainString()} kg"
                )
            }

            DisclaimerCard(
                text = "Important Medical Disclaimer: BMI is a population-level statistical screening estimate and does not directly measure body fat percentage, muscle mass, or metabolic health. Consult a medical professional for personal clinical evaluations."
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BmrTdeeScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var weightStr by remember { mutableStateOf("70") }
    var heightStr by remember { mutableStateOf("175") }
    var ageStr by remember { mutableStateOf("25") }
    var isMale by remember { mutableStateOf(true) }
    var selectedActivity by remember { mutableStateOf(ActivityLevel.MODERATELY_ACTIVE) }

    val result = remember(weightStr, heightStr, ageStr, isMale, selectedActivity) {
        try {
            val w = weightStr.toDouble()
            val h = heightStr.toDouble()
            val a = ageStr.toInt()
            HealthCalculators.calculateBmrAndTdee(w, h, a, isMale, selectedActivity)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BMR & TDEE Calculator", fontWeight = FontWeight.SemiBold) },
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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isMale,
                    onClick = { isMale = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Male") }
                SegmentedButton(
                    selected = !isMale,
                    onClick = { isMale = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Female") }
            }

            NumberInputField(value = ageStr, onValueChange = { ageStr = it }, label = "Age", suffix = "years")
            NumberInputField(value = heightStr, onValueChange = { heightStr = it }, label = "Height", suffix = "cm")
            NumberInputField(value = weightStr, onValueChange = { weightStr = it }, label = "Weight", suffix = "kg")

            Text("Activity Level", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            ActivityLevel.values().forEach { level ->
                FilterChip(
                    selected = level == selectedActivity,
                    onClick = { selectedActivity = level },
                    label = { Text(level.displayName) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (result != null) {
                ResultCard(
                    title = "Estimated Daily Calorie Needs (TDEE)",
                    primaryValue = "${result.tdeeCalories} kcal / day",
                    secondaryValue = "Basal Metabolic Rate (BMR at rest): ${result.bmrCalories} kcal / day",
                    formula = "Mifflin-St Jeor Equation × Activity Multiplier"
                )
            }

            DisclaimerCard(
                text = "Calorie calculations are mathematical estimates. Individual metabolic rates vary depending on muscle mass, thyroid function, and body composition."
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OvulationScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var periodYear by remember { mutableStateOf("2026") }
    var periodMonth by remember { mutableStateOf("10") }
    var periodDay by remember { mutableStateOf("1") }
    var cycleLengthStr by remember { mutableStateOf("28") }

    val result = remember(periodYear, periodMonth, periodDay, cycleLengthStr) {
        try {
            val y = periodYear.toInt()
            val m = periodMonth.toInt()
            val d = periodDay.toInt()
            val cycle = cycleLengthStr.toInt()
            HealthCalculators.estimateOvulation(LocalDate.of(y, m, d), cycle)
        } catch (_: Exception) {
            null
        }
    }

    val dtf = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ovulation & Fertility Estimator", fontWeight = FontWeight.SemiBold) },
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
            Text("First Day of Last Period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInputField(value = periodDay, onValueChange = { periodDay = it }, label = "Day", modifier = Modifier.weight(1f))
                NumberInputField(value = periodMonth, onValueChange = { periodMonth = it }, label = "Month", modifier = Modifier.weight(1f))
                NumberInputField(value = periodYear, onValueChange = { periodYear = it }, label = "Year", modifier = Modifier.weight(1.5f))
            }

            NumberInputField(value = cycleLengthStr, onValueChange = { cycleLengthStr = it }, label = "Average Cycle Length", suffix = "days")

            if (result != null) {
                ResultCard(
                    title = "Estimated Ovulation Window",
                    primaryValue = result.estimatedOvulationDate.format(dtf),
                    secondaryValue = "Estimated Fertile Window: ${result.fertileWindowStart.format(dtf)} to ${result.fertileWindowEnd.format(dtf)}",
                    explanation = "Next expected period: ${result.nextPeriodDate.format(dtf)}"
                )
            }

            DisclaimerCard(
                text = "CRITICAL MEDICAL NOTICE: This tool provides a calendar-based estimate assuming a standard 14-day luteal phase. In reality, human ovulation timing varies month-to-month. There is no foolproof calendar formula to predict exact ovulation. DO NOT use this tool for natural family planning or contraception. For pregnancy prevention or fertility guidance, consult a certified healthcare professional."
            )
        }
    }
}

package com.omnicalc.platform.feature.education

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnicalc.platform.ui.components.NumberInputField
import com.omnicalc.platform.ui.components.ResultCard
import java.math.BigDecimal
import java.math.RoundingMode

data class CourseEntry(
    val name: String,
    val credits: Double,
    val gradePoints: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var pointsEarnedStr by remember { mutableStateOf("85") }
    var pointsPossibleStr by remember { mutableStateOf("100") }

    val result = remember(pointsEarnedStr, pointsPossibleStr) {
        try {
            val earned = BigDecimal(pointsEarnedStr)
            val possible = BigDecimal(pointsPossibleStr)
            if (possible > BigDecimal.ZERO) {
                val pct = earned.divide(possible, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
                val pctD = pct.toDouble()
                val letter = when {
                    pctD >= 90.0 -> "A (Excellent)"
                    pctD >= 80.0 -> "B (Good)"
                    pctD >= 70.0 -> "C (Satisfactory)"
                    pctD >= 60.0 -> "D (Pass)"
                    else -> "F (Fail)"
                }
                Pair(pct.toPlainString(), letter)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grade Calculator", fontWeight = FontWeight.SemiBold) },
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
            NumberInputField(value = pointsEarnedStr, onValueChange = { pointsEarnedStr = it }, label = "Points Earned")
            NumberInputField(value = pointsPossibleStr, onValueChange = { pointsPossibleStr = it }, label = "Total Points Possible")

            if (result != null) {
                ResultCard(
                    title = "Academic Grade",
                    primaryValue = "${result.first}%",
                    secondaryValue = "Letter Grade: ${result.second}",
                    formula = "(Points Earned / Total Possible) × 100"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpaScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var courses by remember {
        mutableStateOf(
            listOf(
                CourseEntry("Math", 4.0, 4.0), // A
                CourseEntry("Physics", 4.0, 3.7), // A-
                CourseEntry("Computer Science", 3.0, 4.0), // A
                CourseEntry("Literature", 3.0, 3.3) // B+
            )
        )
    }

    val gpaResult = remember(courses) {
        val totalCredits = courses.sumOf { it.credits }
        if (totalCredits > 0) {
            val totalPoints = courses.sumOf { it.credits * it.gradePoints }
            val gpa = totalPoints / totalCredits
            Pair(String.format("%.2f", gpa), String.format("%.1f", totalCredits))
        } else Pair("0.00", "0.0")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPA Calculator", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    courses = courses + CourseEntry("Course ${courses.size + 1}", 3.0, 4.0)
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Course") }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
        ) {
            item {
                ResultCard(
                    title = "Cumulative Grade Point Average",
                    primaryValue = "${gpaResult.first} / 4.00",
                    secondaryValue = "Total Credit Hours: ${gpaResult.second}",
                    formula = "Σ(Credits × Grade Points) / Σ(Credits)"
                )
            }

            item {
                Text("Courses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            itemsIndexed(courses) { index, course ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(course.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text("${course.credits} Credits • Points: ${course.gradePoints}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (courses.size > 1) {
                            IconButton(onClick = { courses = courses.toMutableList().also { it.removeAt(index) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete course", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

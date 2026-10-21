package com.omnicalc.platform.feature.statistics

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
import com.omnicalc.platform.core.math.BigDecimalMath
import com.omnicalc.platform.ui.components.NumberInputField
import com.omnicalc.platform.ui.components.ResultCard
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var dataInput by remember { mutableStateOf("12, 15, 23, 23, 40, 55, 60") }

    val stats = remember(dataInput) {
        try {
            val tokens = dataInput.split(',', ' ', '\n', '\t')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.toDouble() }

            val calc = StatisticsCalculators.calculate(tokens) ?: return@remember null
            object {
                val count = calc.count
                val sumFormatted = String.format("%.2f", calc.sum)
                val meanFormatted = String.format("%.4f", calc.mean)
                val medianFormatted = String.format("%.4f", calc.median)
                val modeFormatted = if (calc.modes.isEmpty()) "No mode" else calc.modes.joinToString(", ")
                val rangeFormatted = String.format("%.2f", calc.range)
                val minFormatted = String.format("%.2f", calc.min)
                val maxFormatted = String.format("%.2f", calc.max)
                val sampleStdDevFormatted = calc.sampleStdDev?.let { String.format("%.4f", it) } ?: "Undefined (n = 1)"
                val sampleVarFormatted = calc.sampleVariance?.let { String.format("%.4f", it) } ?: "Undefined (n = 1)"
                val popStdDevFormatted = String.format("%.4f", calc.popStdDev)
                val popVarFormatted = String.format("%.4f", calc.popVariance)
            }
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics Calculator", fontWeight = FontWeight.SemiBold) },
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
            OutlinedTextField(
                value = dataInput,
                onValueChange = { dataInput = it },
                label = { Text("Data points (separated by commas or spaces)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3
            )

            if (stats != null) {
                ResultCard(
                    title = "Central Tendency",
                    primaryValue = "Mean: ${stats.meanFormatted}",
                    secondaryValue = "Median: ${stats.medianFormatted} • Mode: ${stats.modeFormatted} • Count: ${stats.count}"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Dispersion & Variation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sample Std Dev (s):", fontWeight = FontWeight.Medium)
                            Text(stats.sampleStdDevFormatted, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sample Variance (s²):")
                            Text(stats.sampleVarFormatted)
                        }
                        Divider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Population Std Dev (σ):", fontWeight = FontWeight.Medium)
                            Text(stats.popStdDevFormatted, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Population Variance (σ²):")
                            Text(stats.popVarFormatted)
                        }
                        Divider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Range (Max - Min):")
                            Text("${stats.rangeFormatted} (${stats.minFormatted} to ${stats.maxFormatted})")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sum (Σx):")
                            Text(stats.sumFormatted)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProbabilityScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var nStr by remember { mutableStateOf("10") }
    var rStr by remember { mutableStateOf("3") }

    val result = remember(nStr, rStr) {
        try {
            val n = nStr.toInt()
            val r = rStr.toInt()
            if (n >= 0 && r >= 0 && r <= n) {
                // nCr = n! / (r! * (n-r)!)
                val nFact = BigDecimalMath.factorial(n).toBigInteger()
                val rFact = BigDecimalMath.factorial(r).toBigInteger()
                val nMinusRFact = BigDecimalMath.factorial(n - r).toBigInteger()

                val nPr = nFact.divide(nMinusRFact)
                val nCr = nPr.divide(rFact)

                Pair(nCr.toString(), nPr.toString())
            } else null
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Combinations & Permutations", fontWeight = FontWeight.SemiBold) },
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
            NumberInputField(value = nStr, onValueChange = { nStr = it }, label = "Total items (n)")
            NumberInputField(value = rStr, onValueChange = { rStr = it }, label = "Items chosen (r)")

            if (result != null) {
                ResultCard(
                    title = "Combinations nCr (Order does NOT matter)",
                    primaryValue = result.first,
                    formula = "n! / [r! × (n - r)!]"
                )

                ResultCard(
                    title = "Permutations nPr (Order DOES matter)",
                    primaryValue = result.second,
                    formula = "n! / (n - r)!"
                )
            }
        }
    }
}

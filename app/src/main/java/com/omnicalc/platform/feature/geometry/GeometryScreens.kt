package com.omnicalc.platform.feature.geometry

import androidx.compose.foundation.horizontalScroll
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
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.acos
import kotlin.math.sqrt

enum class ShapeType(val displayName: String, val is3D: Boolean) {
    CIRCLE("Circle", false),
    RECTANGLE("Rectangle", false),
    TRIANGLE("Triangle", false),
    TRAPEZOID("Trapezoid", false),
    CYLINDER("Cylinder", true),
    SPHERE("Sphere", true),
    CONE("Cone", true),
    CUBOID("Cuboid", true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeometryScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var selectedShape by remember { mutableStateOf(ShapeType.CIRCLE) }

    var param1 by remember { mutableStateOf("5") } // radius, length, base
    var param2 by remember { mutableStateOf("10") } // width, height
    var param3 by remember { mutableStateOf("4") } // height (3D)

    val result = remember(selectedShape, param1, param2, param3) {
        try {
            val p1 = BigDecimal(param1)
            val p2 = try { BigDecimal(param2) } catch (_: Exception) { BigDecimal.ZERO }
            val p3 = try { BigDecimal(param3) } catch (_: Exception) { BigDecimal.ZERO }
            GeometryCalculators.calculateShape(selectedShape, p1, p2, p3)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geometry 2D & 3D", fontWeight = FontWeight.SemiBold) },
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShapeType.values().forEach { shape ->
                    FilterChip(
                        selected = shape == selectedShape,
                        onClick = { selectedShape = shape },
                        label = { Text(shape.displayName) }
                    )
                }
            }

            when (selectedShape) {
                ShapeType.CIRCLE -> {
                    NumberInputField(value = param1, onValueChange = { param1 = it }, label = "Radius (r)")
                }
                ShapeType.RECTANGLE -> {
                    NumberInputField(value = param1, onValueChange = { param1 = it }, label = "Length")
                    NumberInputField(value = param2, onValueChange = { param2 = it }, label = "Width")
                }
                ShapeType.TRIANGLE -> {
                    NumberInputField(value = param1, onValueChange = { param1 = it }, label = "Base (b)")
                    NumberInputField(value = param2, onValueChange = { param2 = it }, label = "Height (h)")
                }
                ShapeType.TRAPEZOID -> {
                    NumberInputField(value = param1, onValueChange = { param1 = it }, label = "Top Base (a)")
                    NumberInputField(value = param2, onValueChange = { param2 = it }, label = "Bottom Base (b)")
                    NumberInputField(value = param3, onValueChange = { param3 = it }, label = "Height (h)")
                }
                ShapeType.CYLINDER -> {
                    NumberInputField(value = param1, onValueChange = { param1 = it }, label = "Radius (r)")
                    NumberInputField(value = param2, onValueChange = { param2 = it }, label = "Height (h)")
                }
                ShapeType.SPHERE -> {
                    NumberInputField(value = param1, onValueChange = { param1 = it }, label = "Radius (r)")
                }
                ShapeType.CONE -> {
                    NumberInputField(value = param1, onValueChange = { param1 = it }, label = "Radius (r)")
                    NumberInputField(value = param2, onValueChange = { param2 = it }, label = "Height (h)")
                }
                ShapeType.CUBOID -> {
                    NumberInputField(value = param1, onValueChange = { param1 = it }, label = "Length (l)")
                    NumberInputField(value = param2, onValueChange = { param2 = it }, label = "Width (w)")
                    NumberInputField(value = param3, onValueChange = { param3 = it }, label = "Height (h)")
                }
            }

            if (result != null) {
                ResultCard(
                    title = "${selectedShape.displayName} Dimensions",
                    primaryValue = result.first,
                    secondaryValue = result.second
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriangleSolverScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var sideA by remember { mutableStateOf("3") }
    var sideB by remember { mutableStateOf("4") }
    var sideC by remember { mutableStateOf("5") }

    val result = remember(sideA, sideB, sideC) {
        try {
            val a = sideA.toDouble()
            val b = sideB.toDouble()
            val c = sideC.toDouble()
            val solution = GeometryCalculators.solveTriangleSss(a, b, c)

            if (!solution.isValid) {
                Pair(null, solution.errorMessage)
            } else {
                Pair(
                    "Area: ${String.format("%.4f", solution.area)} • Perimeter: ${String.format("%.2f", solution.perimeter)}",
                    "Angles: A = ${String.format("%.2f", solution.angleA)}°, B = ${String.format("%.2f", solution.angleB)}°, C = ${String.format("%.2f", solution.angleC)}° (${solution.type})"
                )
            }
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Triangle Solver", fontWeight = FontWeight.SemiBold) },
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
            Text("Enter All 3 Side Lengths (SSS)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInputField(value = sideA, onValueChange = { sideA = it }, label = "Side a", modifier = Modifier.weight(1f))
                NumberInputField(value = sideB, onValueChange = { sideB = it }, label = "Side b", modifier = Modifier.weight(1f))
                NumberInputField(value = sideC, onValueChange = { sideC = it }, label = "Side c", modifier = Modifier.weight(1f))
            }

            if (result.first != null) {
                ResultCard(
                    title = "Triangle Solution",
                    primaryValue = result.first!!,
                    secondaryValue = result.second
                )
            } else if (result.second != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result.second!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

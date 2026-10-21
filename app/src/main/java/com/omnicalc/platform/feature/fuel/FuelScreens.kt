package com.omnicalc.platform.feature.fuel

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
import com.omnicalc.platform.core.units.Dimension
import com.omnicalc.platform.core.units.UnitConverterEngine
import com.omnicalc.platform.ui.components.NumberInputField
import com.omnicalc.platform.ui.components.ResultCard
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelCostScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var distanceStr by remember { mutableStateOf("300") } // e.g. Nairobi to Kisumu ~350 km
    var consumptionStr by remember { mutableStateOf("7.5") } // 7.5 L/100km
    var pricePerLiterStr by remember { mutableStateOf("185") } // e.g. KES 185/L

    val result = remember(distanceStr, consumptionStr, pricePerLiterStr) {
        try {
            val dist = BigDecimal(distanceStr)
            val cons = BigDecimal(consumptionStr)
            val price = BigDecimal(pricePerLiterStr)

            // Liters needed = distance * (consumption / 100)
            val litersNeeded = dist.multiply(cons).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            val totalCost = litersNeeded.multiply(price).setScale(2, RoundingMode.HALF_UP)
            val costPerKm = if (dist > BigDecimal.ZERO) totalCost.divide(dist, 2, RoundingMode.HALF_UP) else BigDecimal.ZERO

            Triple(litersNeeded, totalCost, costPerKm)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fuel Trip Cost", fontWeight = FontWeight.SemiBold) },
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
            NumberInputField(value = distanceStr, onValueChange = { distanceStr = it }, label = "Trip Distance", suffix = "km")
            NumberInputField(value = consumptionStr, onValueChange = { consumptionStr = it }, label = "Fuel Consumption Rate", suffix = "L/100 km")
            NumberInputField(value = pricePerLiterStr, onValueChange = { pricePerLiterStr = it }, label = "Fuel Price", suffix = "/ Liter")

            if (result != null) {
                ResultCard(
                    title = "Total Fuel Cost",
                    primaryValue = result.second.toPlainString(),
                    secondaryValue = "Fuel Needed: ${result.first.toPlainString()} Liters • Cost per km: ${result.third.toPlainString()}",
                    formula = "(Distance × Consumption / 100) × Price"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelEconomyScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var l100kmStr by remember { mutableStateOf("8.0") }

    val result = remember(l100kmStr) {
        try {
            val l100 = BigDecimal(l100kmStr)
            val unitL100 = UnitConverterEngine.findUnit("l_100km")!!
            val unitKmL = UnitConverterEngine.findUnit("km_l")!!
            val unitMpgUs = UnitConverterEngine.findUnit("mpg_us")!!
            val unitMpgImp = UnitConverterEngine.findUnit("mpg_imp")!!

            val kmL = UnitConverterEngine.convert(l100, unitL100, unitKmL).setScale(2, RoundingMode.HALF_UP)
            val mpgUs = UnitConverterEngine.convert(l100, unitL100, unitMpgUs).setScale(2, RoundingMode.HALF_UP)
            val mpgImp = UnitConverterEngine.convert(l100, unitL100, unitMpgImp).setScale(2, RoundingMode.HALF_UP)

            Triple(kmL, mpgUs, mpgImp)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fuel Economy Converter", fontWeight = FontWeight.SemiBold) },
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
            NumberInputField(value = l100kmStr, onValueChange = { l100kmStr = it }, label = "Liters per 100 km", suffix = "L/100km")

            if (result != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Equivalent Ratings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kilometers per Liter:")
                            Text("${result.first.toPlainString()} km/L", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Divider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Miles per Gallon (US):")
                            Text("${result.second.toPlainString()} MPG (US)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Divider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Miles per Gallon (Imperial):")
                            Text("${result.third.toPlainString()} MPG (Imp)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

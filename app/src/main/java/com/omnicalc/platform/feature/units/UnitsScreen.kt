package com.omnicalc.platform.feature.units

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicalc.platform.core.units.Dimension
import com.omnicalc.platform.core.units.UnitConverterEngine
import com.omnicalc.platform.core.units.UnitDefinition
import com.omnicalc.platform.ui.components.NumberInputField
import com.omnicalc.platform.ui.components.ResultCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsScreen(
    viewModel: UnitsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var showFromMenu by remember { mutableStateOf(false) }
    var showToMenu by remember { mutableStateOf(false) }

    val currentDimensionUnits = remember(state.selectedDimension) {
        UnitConverterEngine.getUnitsForDimension(state.selectedDimension)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unit Converter", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Dimension Category Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Dimension.values().forEach { dim ->
                        FilterChip(
                            selected = dim == state.selectedDimension,
                            onClick = { viewModel.selectDimension(dim) },
                            label = { Text(dim.displayName) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Converter Input & Selection Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Input Value
                        NumberInputField(
                            value = state.inputValue,
                            onValueChange = viewModel::onInputValueChanged,
                            label = "Value",
                            suffix = state.fromUnit.symbol,
                            isError = state.errorMessage != null,
                            errorMessage = state.errorMessage
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // From Unit Selector
                        Box {
                            OutlinedCard(
                                onClick = { showFromMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "FROM",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${state.fromUnit.name} (${state.fromUnit.symbol})",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text("▾", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            DropdownMenu(
                                expanded = showFromMenu,
                                onDismissRequest = { showFromMenu = false }
                            ) {
                                currentDimensionUnits.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text("${unit.name} (${unit.symbol})") },
                                        onClick = {
                                            viewModel.selectFromUnit(unit)
                                            showFromMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Swap Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(onClick = viewModel::swapUnits) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Swap units",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // To Unit Selector
                        Box {
                            OutlinedCard(
                                onClick = { showToMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "TO",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${state.toUnit.name} (${state.toUnit.symbol})",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text("▾", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            DropdownMenu(
                                expanded = showToMenu,
                                onDismissRequest = { showToMenu = false }
                            ) {
                                currentDimensionUnits.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text("${unit.name} (${unit.symbol})") },
                                        onClick = {
                                            viewModel.selectToUnit(unit)
                                            showToMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Primary Result Card
            item {
                ResultCard(
                    title = "Converted Result",
                    primaryValue = "${state.resultValue} ${state.toUnit.symbol}",
                    secondaryValue = "${state.inputValue} ${state.fromUnit.name} = ${state.resultValue} ${state.toUnit.name}",
                    formula = state.formulaExplanation
                )
            }

            // Live Breakdown of all other units in this category
            if (state.allBreakdownItems.isNotEmpty()) {
                item {
                    Text(
                        text = "All ${state.selectedDimension.displayName} Units",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(state.allBreakdownItems) { breakdown ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (breakdown.unit.id == state.toUnit.id)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = breakdown.unit.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = breakdown.unit.symbol,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${breakdown.formattedValue} ${breakdown.unit.symbol}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

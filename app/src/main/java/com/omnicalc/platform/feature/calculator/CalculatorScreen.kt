package com.omnicalc.platform.feature.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import android.util.Log
import com.omnicalc.platform.MainActivity
import com.omnicalc.platform.OmniCalcApplication
import com.omnicalc.platform.core.database.entities.CalculationHistoryEntity
import com.omnicalc.platform.ui.components.CalculatorDisplay
import com.omnicalc.platform.ui.components.CalculatorKeypad
import com.omnicalc.platform.ui.components.QuickToolsBar
import java.math.BigDecimal

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onNavigateToTool: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        val tInteractive = SystemClock.elapsedRealtime()
        val processStart = OmniCalcApplication.processStartTime
        val activityCreate = MainActivity.activityCreateTime
        Log.i("PERF_OMNICALC", "STAGE_5_FIRST_INTERACTIVE_UI: cold=${tInteractive - processStart} ms, activity=${tInteractive - activityCreate} ms")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Baziq Calculator",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "Calculation History")
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Universal Search")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Quick Tools Bar - static, independent of keypad typing
            QuickToolsBar(
                onSelectTool = onNavigateToTool,
                onOpenSearch = onNavigateToSearch
            )

            // Calculator Display Area - isolated so only this recomposes on keystrokes
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomEnd
            ) {
                CalculatorDisplaySection(viewModel = viewModel)
            }

            // Keypad - isolated so typing digits NEVER triggers keypad recomposition
            CalculatorKeypadSection(viewModel = viewModel)
        }
    }
}

@Composable
private fun CalculatorDisplaySection(
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    CalculatorDisplay(
        expression = state.expression,
        result = state.liveResult,
        hasMemory = state.memory.compareTo(BigDecimal.ZERO) != 0,
        errorMessage = state.errorMessage,
        useGroupingSeparators = state.useGroupingSeparators,
        modifier = modifier
    )
}

@Composable
private fun CalculatorKeypadSection(
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier
) {
    val isScientificExpanded by remember(viewModel) {
        viewModel.uiState.map { it.isScientificExpanded }.distinctUntilChanged()
    }.collectAsState(initial = false)

    val angleMode by remember(viewModel) {
        viewModel.uiState.map { it.angleMode }.distinctUntilChanged()
    }.collectAsState(initial = com.omnicalc.platform.core.math.AngleMode.DEGREES)

    val hapticsEnabled by remember(viewModel) {
        viewModel.uiState.map { it.hapticsEnabled }.distinctUntilChanged()
    }.collectAsState(initial = true)

    CalculatorKeypad(
        onDigit = viewModel::onDigit,
        onOperator = viewModel::onOperator,
        onClear = viewModel::onClear,
        onEquals = viewModel::onEquals,
        onParentheses = viewModel::onParentheses,
        onPercentage = viewModel::onPercentage,
        onSignChange = viewModel::onSignChange,
        onScientificFunction = viewModel::onScientificFunction,
        onMemoryAction = viewModel::onMemoryAction,
        isScientificExpanded = isScientificExpanded,
        onToggleScientific = viewModel::onToggleScientific,
        angleMode = angleMode,
        onToggleAngleMode = viewModel::onToggleAngleMode,
        onBackspace = viewModel::onBackspace,
        hapticsEnabled = hapticsEnabled,
        modifier = modifier
    )
}


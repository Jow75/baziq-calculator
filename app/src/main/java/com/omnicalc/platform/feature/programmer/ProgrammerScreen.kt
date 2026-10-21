package com.omnicalc.platform.feature.programmer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicalc.platform.core.math.NumberBase
import com.omnicalc.platform.core.math.ProgrammerState
import com.omnicalc.platform.core.math.WordSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgrammerScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember { mutableStateOf(ProgrammerState(255uL, WordSize.QWORD)) }
    var activeBase by remember { mutableStateOf(NumberBase.DEC) }
    var inputBuffer by remember { mutableStateOf("255") }

    fun updateValue(newBuffer: String, base: NumberBase = activeBase) {
        inputBuffer = newBuffer
        state = ProgrammerState.fromString(newBuffer, base, state.wordSize)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Programmer Calculator", fontWeight = FontWeight.SemiBold) },
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
            // Word size selection tabs
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WordSize.values().forEach { ws ->
                    FilterChip(
                        selected = ws == state.wordSize,
                        onClick = {
                            state = state.copy(wordSize = ws)
                            updateValue(inputBuffer)
                        },
                        label = { Text(ws.displayName) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4-Base Live Viewer
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BaseDisplayRow(label = "HEX", value = state.hexString, isSelected = activeBase == NumberBase.HEX) {
                        activeBase = NumberBase.HEX
                        inputBuffer = state.hexString
                    }
                    Divider()
                    BaseDisplayRow(label = "DEC", value = state.decUnsignedString, isSelected = activeBase == NumberBase.DEC) {
                        activeBase = NumberBase.DEC
                        inputBuffer = state.decUnsignedString
                    }
                    Divider()
                    BaseDisplayRow(label = "OCT", value = state.octString, isSelected = activeBase == NumberBase.OCT) {
                        activeBase = NumberBase.OCT
                        inputBuffer = state.octString
                    }
                    Divider()
                    BaseDisplayRow(label = "BIN", value = state.binString, isSelected = activeBase == NumberBase.BIN) {
                        activeBase = NumberBase.BIN
                        inputBuffer = state.binString.replace(" ", "")
                    }
                }
            }

            // Bitwise operations row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { state = state.not(); updateValue(state.decUnsignedString, NumberBase.DEC) }, modifier = Modifier.weight(1f)) { Text("NOT") }
                FilledTonalButton(onClick = { state = state.shiftLeft(1); updateValue(state.decUnsignedString, NumberBase.DEC) }, modifier = Modifier.weight(1f)) { Text("<< 1") }
                FilledTonalButton(onClick = { state = state.shiftRightLogical(1); updateValue(state.decUnsignedString, NumberBase.DEC) }, modifier = Modifier.weight(1f)) { Text(">> 1") }
                FilledTonalButton(onClick = { state = state.rotateLeft(1); updateValue(state.decUnsignedString, NumberBase.DEC) }, modifier = Modifier.weight(1f)) { Text("ROL") }
                FilledTonalButton(onClick = { state = ProgrammerState(0uL, state.wordSize); inputBuffer = "0" }, modifier = Modifier.weight(1f)) { Text("CLR") }
            }

            // Contextual Keypad for the active base
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // HEX Digits A-F (enabled only when HEX is active)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("A", "B", "C", "D", "E", "F").forEach { char ->
                            KeypadButton(
                                text = char,
                                enabled = activeBase == NumberBase.HEX,
                                onClick = { updateValue(inputBuffer + char) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 7, 8, 9
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("7", "8", "9").forEach { char ->
                            val enabled = activeBase == NumberBase.HEX || activeBase == NumberBase.DEC || (activeBase == NumberBase.OCT && char == "7")
                            KeypadButton(text = char, enabled = enabled, onClick = { updateValue(inputBuffer + char) }, modifier = Modifier.weight(1f))
                        }
                    }

                    // 4, 5, 6
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("4", "5", "6").forEach { char ->
                            val enabled = activeBase != NumberBase.BIN
                            KeypadButton(text = char, enabled = enabled, onClick = { updateValue(inputBuffer + char) }, modifier = Modifier.weight(1f))
                        }
                    }

                    // 1, 2, 3
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1", "2", "3").forEach { char ->
                            val enabled = if (char == "1") true else activeBase != NumberBase.BIN
                            KeypadButton(text = char, enabled = enabled, onClick = { updateValue(inputBuffer + char) }, modifier = Modifier.weight(1f))
                        }
                    }

                    // 0 & Backspace
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KeypadButton(text = "0", enabled = true, onClick = { updateValue(inputBuffer + "0") }, modifier = Modifier.weight(2f))
                        KeypadButton(
                            text = "⌫",
                            enabled = inputBuffer.isNotEmpty(),
                            onClick = {
                                if (inputBuffer.isNotEmpty()) {
                                    val dropped = inputBuffer.dropLast(1)
                                    updateValue(if (dropped.isEmpty()) "0" else dropped)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BaseDisplayRow(
    label: String,
    value: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        ),
        contentPadding = PaddingValues(vertical = 12.dp),
        modifier = modifier
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

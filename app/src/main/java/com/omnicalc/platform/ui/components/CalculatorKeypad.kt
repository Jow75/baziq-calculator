package com.omnicalc.platform.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicalc.platform.core.math.AngleMode

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

sealed class KeyType {
    object Number : KeyType()
    object Operator : KeyType()
    object Function : KeyType()
    object Equals : KeyType()
    object Clear : KeyType()
    object Memory : KeyType()
}

private fun defaultContentDescription(text: String): String = when (text) {
    "+" -> "Add"
    "−", "-" -> "Subtract"
    "×", "*" -> "Multiply"
    "÷", "/" -> "Divide"
    "=" -> "Equals"
    "C" -> "Clear"
    "( )" -> "Parentheses"
    "%" -> "Percentage"
    "±" -> "Plus minus"
    "." -> "Decimal point"
    "√" -> "Square root"
    "xʸ" -> "Power"
    "x!" -> "Factorial"
    "π" -> "Pi"
    "e" -> "Euler's number"
    "sin" -> "Sine"
    "cos" -> "Cosine"
    "tan" -> "Tangent"
    "ln" -> "Natural log"
    "log" -> "Logarithm"
    "MC" -> "Memory Clear"
    "MR" -> "Memory Recall"
    "M+" -> "Memory Add"
    "M-" -> "Memory Subtract"
    else -> text
}

@Composable
fun CalculatorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: KeyType = KeyType.Number,
    hapticsEnabled: Boolean = true,
    contentDesc: String? = null
) {
    val haptic = LocalHapticFeedback.current
    val desc = contentDesc ?: defaultContentDescription(text)

    val (bgColor, textColor) = when (type) {
        KeyType.Number -> Pair(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.onSurface
        )
        KeyType.Operator -> Pair(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        KeyType.Function -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.primary
        )
        KeyType.Equals -> Pair(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary
        )
        KeyType.Clear -> Pair(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.onErrorContainer
        )
        KeyType.Memory -> Pair(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Box(
        modifier = modifier
            .padding(4.dp)
            .aspectRatio(if (type == KeyType.Memory) 2.2f else if (type == KeyType.Function) 1.45f else 1.25f)
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .semantics { contentDescription = desc }
            .clickable {
                if (hapticsEnabled) {
                    haptic.performHapticFeedback(
                        if (type == KeyType.Equals) HapticFeedbackType.LongPress
                        else HapticFeedbackType.TextHandleMove
                    )
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = when (type) {
                KeyType.Memory -> MaterialTheme.typography.labelSmall
                KeyType.Function -> MaterialTheme.typography.titleMedium
                KeyType.Number, KeyType.Operator, KeyType.Equals, KeyType.Clear -> MaterialTheme.typography.titleLarge
            },
            fontWeight = if (type == KeyType.Number) FontWeight.Medium else FontWeight.SemiBold,
            color = textColor,
            fontSize = if (type == KeyType.Memory) 12.sp else if (type == KeyType.Function) 15.sp else 22.sp
        )
    }
}

/** Content description of the backspace key; it deletes one character, it never clears all. */
const val BACKSPACE_KEY_DESCRIPTION = "Backspace"

/**
 * Backspace key: a filled, tinted 64 x 48 dp key with a 26 dp icon, so it reads as a key
 * rather than a toolbar glyph and meets the 48 dp minimum touch target.
 */
@Composable
fun BackspaceKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .size(width = 64.dp, height = 48.dp)
            .semantics { contentDescription = BACKSPACE_KEY_DESCRIPTION }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Backspace,
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun CalculatorKeypad(
    onDigit: (String) -> Unit,
    onOperator: (String) -> Unit,
    onClear: () -> Unit,
    onEquals: () -> Unit,
    onParentheses: () -> Unit,
    onPercentage: () -> Unit,
    onSignChange: () -> Unit,
    onScientificFunction: (String) -> Unit,
    onMemoryAction: (String) -> Unit,
    isScientificExpanded: Boolean,
    onToggleScientific: () -> Unit,
    angleMode: AngleMode = AngleMode.DEGREES,
    onToggleAngleMode: () -> Unit = {},
    onBackspace: () -> Unit = {},
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Top scientific toggle, DEG/RAD mode, Backspace & Memory bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Memory Buttons (MC, MR, M+, M-)
            val memoryButtons = remember {
                listOf(
                    Pair("MC", "Memory Clear"),
                    Pair("MR", "Memory Recall"),
                    Pair("M+", "Memory Add"),
                    Pair("M-", "Memory Subtract")
                )
            }
            // Memory keys share the width left over by the backspace key and the toggle,
            // so the larger backspace key never pushes the row off narrow screens.
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                memoryButtons.forEach { (mem, desc) ->
                    Surface(
                        onClick = { onMemoryAction(mem) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = desc }
                    ) {
                        Text(
                            text = mem,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }

            // Right Action Controls: Backspace + Scientific Toggle
            Row(
                modifier = Modifier.padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BackspaceKey(onClick = onBackspace, hapticsEnabled = hapticsEnabled)

                // Scientific Toggle Button
                TextButton(
                    onClick = onToggleScientific,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isScientificExpanded) "Basic ▾" else "Scientific ▴",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Scientific Expansion Rows (Harmonious 6-column grid featuring DEG/RAD alongside trigonometric functions)
        AnimatedVisibility(visible = isScientificExpanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    CalculatorButton(
                        text = if (angleMode == AngleMode.DEGREES) "DEG" else "RAD",
                        onClick = onToggleAngleMode,
                        modifier = Modifier.weight(1f),
                        type = KeyType.Function,
                        hapticsEnabled = hapticsEnabled,
                        contentDesc = if (angleMode == AngleMode.DEGREES) "Degrees (tap for Radians)" else "Radians (tap for Degrees)"
                    )
                    CalculatorButton("sin", { onScientificFunction("sin(") }, Modifier.weight(1f), KeyType.Function, hapticsEnabled)
                    CalculatorButton("cos", { onScientificFunction("cos(") }, Modifier.weight(1f), KeyType.Function, hapticsEnabled)
                    CalculatorButton("tan", { onScientificFunction("tan(") }, Modifier.weight(1f), KeyType.Function, hapticsEnabled)
                    CalculatorButton("ln", { onScientificFunction("ln(") }, Modifier.weight(1f), KeyType.Function, hapticsEnabled)
                    CalculatorButton("log", { onScientificFunction("log(") }, Modifier.weight(1f), KeyType.Function, hapticsEnabled)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    CalculatorButton("√", { onScientificFunction("sqrt(") }, Modifier.weight(1f), KeyType.Function, hapticsEnabled)
                    CalculatorButton("xʸ", { onOperator("^") }, Modifier.weight(1f), KeyType.Function, hapticsEnabled)
                    CalculatorButton("x!", { onOperator("!") }, Modifier.weight(1f), KeyType.Function, hapticsEnabled)
                    CalculatorButton("π", { onDigit("π") }, Modifier.weight(1f), KeyType.Function, hapticsEnabled)
                    CalculatorButton("e", { onDigit("e") }, Modifier.weight(1f), KeyType.Function, hapticsEnabled)
                    CalculatorButton("1/x", { onScientificFunction("1/(") }, Modifier.weight(1f), KeyType.Function, hapticsEnabled)
                }
            }
        }

        // Standard 4x5 Keypad
        // Row 1: C, ( ), %, ÷
        Row(modifier = Modifier.fillMaxWidth()) {
            CalculatorButton("C", onClear, Modifier.weight(1f), KeyType.Clear, hapticsEnabled)
            CalculatorButton("( )", onParentheses, Modifier.weight(1f), KeyType.Operator, hapticsEnabled)
            CalculatorButton("%", onPercentage, Modifier.weight(1f), KeyType.Operator, hapticsEnabled)
            CalculatorButton("÷", { onOperator("÷") }, Modifier.weight(1f), KeyType.Operator, hapticsEnabled)
        }

        // Row 2: 7, 8, 9, ×
        Row(modifier = Modifier.fillMaxWidth()) {
            CalculatorButton("7", { onDigit("7") }, Modifier.weight(1f), KeyType.Number, hapticsEnabled)
            CalculatorButton("8", { onDigit("8") }, Modifier.weight(1f), KeyType.Number, hapticsEnabled)
            CalculatorButton("9", { onDigit("9") }, Modifier.weight(1f), KeyType.Number, hapticsEnabled)
            CalculatorButton("×", { onOperator("×") }, Modifier.weight(1f), KeyType.Operator, hapticsEnabled)
        }

        // Row 3: 4, 5, 6, −
        Row(modifier = Modifier.fillMaxWidth()) {
            CalculatorButton("4", { onDigit("4") }, Modifier.weight(1f), KeyType.Number, hapticsEnabled)
            CalculatorButton("5", { onDigit("5") }, Modifier.weight(1f), KeyType.Number, hapticsEnabled)
            CalculatorButton("6", { onDigit("6") }, Modifier.weight(1f), KeyType.Number, hapticsEnabled)
            CalculatorButton("−", { onOperator("−") }, Modifier.weight(1f), KeyType.Operator, hapticsEnabled)
        }

        // Row 4: 1, 2, 3, +
        Row(modifier = Modifier.fillMaxWidth()) {
            CalculatorButton("1", { onDigit("1") }, Modifier.weight(1f), KeyType.Number, hapticsEnabled)
            CalculatorButton("2", { onDigit("2") }, Modifier.weight(1f), KeyType.Number, hapticsEnabled)
            CalculatorButton("3", { onDigit("3") }, Modifier.weight(1f), KeyType.Number, hapticsEnabled)
            CalculatorButton("+", { onOperator("+") }, Modifier.weight(1f), KeyType.Operator, hapticsEnabled)
        }

        // Row 5: ±, 0, ., =
        Row(modifier = Modifier.fillMaxWidth()) {
            CalculatorButton("±", onSignChange, Modifier.weight(1f), KeyType.Number, hapticsEnabled)
            CalculatorButton("0", { onDigit("0") }, Modifier.weight(1f), KeyType.Number, hapticsEnabled)
            CalculatorButton(".", { onDigit(".") }, Modifier.weight(1f), KeyType.Number, hapticsEnabled)
            CalculatorButton("=", onEquals, Modifier.weight(1f), KeyType.Equals, hapticsEnabled)
        }
    }
}

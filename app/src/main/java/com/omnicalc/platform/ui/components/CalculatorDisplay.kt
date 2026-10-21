package com.omnicalc.platform.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.omnicalc.platform.core.math.CalculatorDisplayFormatter

/**
 * Adaptive calculator display that progressively reduces expression font size
 * as the expression approaches and exceeds the available display width, while
 * strictly preserving horizontal swipe/scroll to inspect earlier expression parts.
 *
 * Sizing Strategy:
 * 1. Base font size is 28sp (displaySmall).
 * 2. If the expression comfortably fits within available width, 28sp is used (zero shrinking).
 * 3. As the expression lengthens and approaches container width, font size steps down:
 *    28sp -> 25sp -> 22sp -> 19sp -> 16sp (sensible minimum font size).
 * 4. Stepped scaling eliminates fractional font jitter while typing.
 * 5. When the expression exceeds the capacity of 16sp, horizontal scrolling smoothly
 *    accommodates the overflow.
 * 6. While typing (expression length increases), display scrolls to the right to keep
 *    the active cursor visible. When the user manually swipes left to inspect history,
 *    scroll position is preserved.
 */
@Composable
fun CalculatorDisplay(
    expression: String,
    result: String,
    hasMemory: Boolean = false,
    errorMessage: String? = null,
    useGroupingSeparators: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var prevLength by remember { mutableIntStateOf(expression.length) }

    // When the user enters new characters (expression lengthened), scroll to end
    // so the active typing position is visible. When user manually scrolls left,
    // expression.length is unchanged, so the user's scroll inspection is preserved.
    LaunchedEffect(scrollState.maxValue, expression.length) {
        if (expression.length > prevLength && scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
        prevLength = expression.length
    }

    val displayExpression = CalculatorDisplayFormatter.formatExpression(expression, useGroupingSeparators)
    val displayResult = CalculatorDisplayFormatter.formatResult(result, useGroupingSeparators)

    // Stepped adaptive font sizes (28sp down to 16sp)
    val fontSizes = remember { listOf(28f, 25f, 22f, 19f, 16f) }
    val baseStyle = MaterialTheme.typography.displaySmall
    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (hasMemory) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = "M",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        val expressionText = if (displayExpression.isEmpty()) "0" else displayExpression

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val availableWidthPx = constraints.maxWidth.toFloat()

            // Deterministic stepped font sizing calculation
            val adaptiveFontSizeSp = remember(expressionText, availableWidthPx) {
                if (availableWidthPx <= 0f || expressionText.length <= 1) {
                    fontSizes.first()
                } else {
                    var selected = fontSizes.last()
                    for (size in fontSizes) {
                        val style = baseStyle.copy(fontSize = size.sp)
                        val measured = textMeasurer.measure(
                            text = expressionText,
                            style = style,
                            constraints = Constraints(maxWidth = Int.MAX_VALUE)
                        ).size.width.toFloat()

                        if (measured <= availableWidthPx) {
                            selected = size
                            break
                        }
                    }
                    selected
                }
            }

            // Active Expression text (horizontal scrollable, adaptive font)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = expressionText,
                    style = baseStyle.copy(
                        fontSize = adaptiveFontSizeSp.sp,
                        lineHeight = (adaptiveFontSizeSp * 1.25f).sp
                    ),
                    color = if (displayExpression.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main Result / Live preview
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.End,
                fontWeight = FontWeight.Medium
            )
        } else {
            Text(
                text = if (displayResult.isEmpty()) "" else "= $displayResult",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }
    }
}

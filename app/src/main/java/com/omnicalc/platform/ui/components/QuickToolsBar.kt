package com.omnicalc.platform.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicalc.platform.core.registry.ToolDefinition
import com.omnicalc.platform.core.registry.ToolRegistry

@Composable
fun QuickToolsBar(
    onSelectTool: (String) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val quickShortcuts = remember {
        listOf(
            Triple("currency", "Currency", Icons.Default.CurrencyExchange),
            Triple("units", "Units", Icons.Default.Straighten),
            Triple("finance/discount", "Discount", Icons.Default.LocalOffer),
            Triple("finance/tax_vat", "Tax & VAT", Icons.Default.ReceiptLong),
            Triple("finance/loan", "Loan", Icons.Default.AccountBalance),
            Triple("dates/age", "Age", Icons.Default.Cake),
            Triple("health/bmi", "BMI", Icons.Default.FitnessCenter),
            Triple("fuel/cost", "Fuel", Icons.Default.LocalGasStation),
            Triple("programmer", "Programmer", Icons.Default.Code),
            Triple("constants", "Constants", Icons.Default.Science)
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Universal Search Pill
        FilledTonalButton(
            onClick = onOpenSearch,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Search Tools",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Quick Shortcuts
        quickShortcuts.forEach { (route, title, icon) ->
            Surface(
                onClick = { onSelectTool(route) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

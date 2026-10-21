package com.omnicalc.platform.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicalc.platform.core.math.AngleMode
import com.omnicalc.platform.core.preferences.AppTheme
import com.omnicalc.platform.core.preferences.UserPreferences
import com.omnicalc.platform.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesRepository: UserPreferencesRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = UserPreferences())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
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
            // Appearance
            Text("Appearance & Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppTheme.values().forEachIndexed { index, theme ->
                    SegmentedButton(
                        selected = prefs.theme == theme,
                        onClick = { scope.launch { preferencesRepository.setTheme(theme) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = AppTheme.values().size)
                    ) {
                        Text(theme.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }

            Divider()

            // Keypad & Math Settings
            Text("Calculation & Keypad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Haptic Feedback", fontWeight = FontWeight.Medium)
                    Text("Vibrate subtly on key presses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = prefs.hapticFeedbackEnabled,
                    onCheckedChange = { checked -> scope.launch { preferencesRepository.setHapticFeedback(checked) } }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Grouping Separators", fontWeight = FontWeight.Medium)
                    Text("Display thousands separators (e.g. 1,000.00)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = prefs.useGroupingSeparators,
                    onCheckedChange = { checked -> scope.launch { preferencesRepository.setGrouping(checked) } }
                )
            }

            // Decimal Precision
            Column {
                Text("Default Decimal Precision", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 4, 6, 8).forEach { prec ->
                        FilterChip(
                            selected = prefs.decimalPrecision == prec,
                            onClick = { scope.launch { preferencesRepository.setDecimalPrecision(prec) } },
                            label = { Text("$prec places") }
                        )
                    }
                }
            }

            Divider()

            // Privacy & Data
            Text("Privacy & Offline Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "• 100% Offline-First Architecture\n• No account or registration required\n• Calculations, history, and preferences never leave your phone\n• Zero third-party behavioral analytics or invasive tracking",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()

            // Currency Dataset Metadata
            Text("Static Currency Dataset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val meta = com.omnicalc.platform.core.currency.StaticFallbackRateProvider.METADATA
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dataset Version: ${meta.version}", fontWeight = FontWeight.Bold)
                    Text("Edition Date: ${meta.datasetDate} (Updated: ${meta.lastUpdateDate})", style = MaterialTheme.typography.bodySmall)
                    Text("Catalog Coverage: ${meta.currencyCount} ISO 4217 Currencies", style = MaterialTheme.typography.bodySmall)
                    Text("Source Reference: ${meta.sourceReference}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        meta.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()

            // About & Data Governance
            Text("About OmniCalc", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("OmniCalc Platform v1.0.0", fontWeight = FontWeight.Bold)
                    Text("High-Precision Scientific, Financial, and Multi-Conversion Suite for Android.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Data Standards & Citations:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Text("• Unit Conversion Factors: BIPM / NIST Special Publication 811\n• Physical Constants: CODATA 2018 / 2022 Committee on Data of the International Science Council\n• Exchange Reference Rates: Open Exchange & Central Bank reference datasets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

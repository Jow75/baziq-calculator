package com.omnicalc.platform.feature.settings

import com.omnicalc.platform.BuildConfig
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.omnicalc.platform.core.preferences.LocalCurrencySettings
import com.omnicalc.platform.core.backup.BackupCodec
import com.omnicalc.platform.core.backup.BackupFile
import com.omnicalc.platform.core.backup.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    backupRepository: BackupRepository,
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
                // Wraps whole chips onto a second line on narrow screens; in a Row the last one
                // was squeezed until its label broke inside the word ("8 plac/es")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 4, 6, 8).forEach { prec ->
                        FilterChip(
                            selected = prefs.decimalPrecision == prec,
                            onClick = { scope.launch { preferencesRepository.setDecimalPrecision(prec) } },
                            label = { Text("$prec places", maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }

            Divider()

            // Currency: base currency from the current country
            Text("Currency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val localSettings by preferencesRepository.localCurrencySettingsFlow.collectAsState(initial = LocalCurrencySettings())
            val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                scope.launch {
                    preferencesRepository.setLocationPromptAnswered()
                    if (granted) preferencesRepository.setAutoLocalCurrency(true)
                }
                if (!granted) {
                    Toast.makeText(context, "Location not allowed. Choose currencies manually, or allow location in Android settings.", Toast.LENGTH_LONG).show()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use my current country's currency", fontWeight = FontWeight.Medium)
                    Text(
                        "When you open the converter in a new country, its currency becomes your base currency. Uses approximate location; only the country is kept.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = localSettings.autoLocalCurrency,
                    onCheckedChange = { checked ->
                        val granted = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                        if (checked && !granted) {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        } else {
                            scope.launch { preferencesRepository.setAutoLocalCurrency(checked) }
                        }
                    }
                )
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
                        text = "• Calculations work offline\n• No account or registration required\n• History and settings are stored on your phone. They leave it only in a backup file you export, or in Android's own device backup if you have turned that on\n• No analytics or advertising trackers\n• Exchange rates are downloaded from public rate services\n• Local currency: when you allow it, your approximate position is passed to the phone's geocoding service to find the country; only the country code is saved",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()

            BackupSection(backupRepository)

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
            Text("About Baziq Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Baziq Calculator v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                    Text("by Baziq Hue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text("High-Precision Scientific, Financial, and Multi-Conversion Suite for Android.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Data Standards & Citations:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Text("• Unit Conversion Factors: BIPM / NIST Special Publication 811\n• Physical Constants: CODATA 2018 / 2022 Committee on Data of the International Science Council\n• Exchange Reference Rates: Open Exchange & Central Bank reference datasets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Saves the user's data (history, currency list, custom rates, settings) to a file they choose,
 * and merges such a file back in. Used to move data to a new installation; nothing is deleted.
 */
@Composable
private fun BackupSection(backupRepository: BackupRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<BackupFile?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = try {
                val backup = backupRepository.export()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(BackupCodec.encode(backup).toByteArray()) }
                        ?: throw java.io.IOException("The file could not be opened.")
                }
                "Backup saved: ${backup.history.size} history entries, ${backup.currencies.size} currencies, " +
                    "${backup.customRates.size} custom rates."
            } catch (e: Exception) {
                "Backup failed: ${e.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                // Read with a size limit and parse off the main thread
                pendingImport = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.use { BackupCodec.readText(it) }
                        ?: throw java.io.IOException("The file could not be opened.")
                    BackupCodec.decode(text)
                }
            } catch (e: Exception) {
                status = "Import failed: ${e.message}"
            }
        }
    }

    Text("Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Save your history, currency list, custom rates and settings to a file, for example before moving to a new phone or reinstalling. The file stays where you save it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            exportLauncher.launch("baziq-calculator-backup-$day.json")
        }) { Text("Export backup") }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
            Text("Import backup")
        }
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

    pendingImport?.let { backup ->
        val made = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(backup.exportedAtMs))
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import this backup?") },
            text = {
                Text(
                    "Made on $made with version ${backup.appVersion}: ${backup.history.size} history entries, " +
                        "${backup.currencies.size} currencies, ${backup.customRates.size} custom rates and your settings.\n\n" +
                        "History entries you already have are not duplicated. The backup's currency list and settings " +
                        "replace the current ones; nothing else is deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        status = try {
                            val result = backupRepository.import(backup)
                            "Backup imported: ${result.historyAdded} history entries added " +
                                "(${result.historyAlreadyPresent} already present), ${result.currencies} currencies."
                        } catch (e: Exception) {
                            "Import failed: ${e.message}"
                        }
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } }
        )
    }
}

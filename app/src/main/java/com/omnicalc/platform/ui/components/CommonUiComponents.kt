package com.omnicalc.platform.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicalc.platform.core.currency.ConversionResolution
import com.omnicalc.platform.core.currency.ConversionSourceType
import com.omnicalc.platform.core.currency.FreshnessStatus

@Composable
fun ResultCard(
    title: String,
    primaryValue: String,
    secondaryValue: String? = null,
    formula: String? = null,
    explanation: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(title, primaryValue)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied $primaryValue", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy result",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = primaryValue,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            if (!secondaryValue.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = secondaryValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!formula.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Formula: $formula",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (!explanation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun NumberInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            prefix = prefix?.let { { Text(it, fontWeight = FontWeight.Bold) } },
            suffix = suffix?.let { { Text(it) } },
            singleLine = true,
            isError = isError,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            keyboardActions = keyboardActions,
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear input", modifier = Modifier.size(18.dp))
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (isError && !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun DisclaimerCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Information Notice",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun OfflineStatusBadge(
    status: FreshnessStatus,
    timestampMs: Long,
    providerName: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon) = when (status) {
        FreshnessStatus.CURRENT -> Triple(
            Color(0xFF059669).copy(alpha = 0.15f),
            Color(0xFF059669),
            Icons.Default.CloudDone
        )
        FreshnessStatus.RECENT -> Triple(
            Color(0xFF0284C7).copy(alpha = 0.15f),
            Color(0xFF0284C7),
            Icons.Default.Schedule
        )
        FreshnessStatus.OFFLINE -> Triple(
            Color(0xFFD97706).copy(alpha = 0.15f),
            Color(0xFFD97706),
            Icons.Default.CloudOff
        )
        FreshnessStatus.STALE -> Triple(
            Color(0xFFDC2626).copy(alpha = 0.15f),
            Color(0xFFDC2626),
            Icons.Default.Warning
        )
        FreshnessStatus.UNAVAILABLE -> Triple(
            Color(0xFF6B7280).copy(alpha = 0.15f),
            Color(0xFF6B7280),
            Icons.Default.CloudOff
        )
    }

    val ageMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val ageText = when {
        timestampMs <= 0L -> "No timestamp"
        ageMs < 60_000L -> "Just now"
        ageMs < 3600_000L -> "${ageMs / 60_000L}m ago"
        ageMs < 86400_000L -> "${ageMs / 3600_000L}h ago"
        else -> "${ageMs / 86400_000L}d ago"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${status.label} • $providerName • $ageText",
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ConversionSourceBadge(
    resolution: ConversionResolution,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (resolution.sourceType) {
        ConversionSourceType.LIVE -> Pair(
            Color(0xFF047857).copy(alpha = 0.12f),
            Color(0xFF047857)
        )
        ConversionSourceType.MIXED -> Pair(
            Color(0xFFD97706).copy(alpha = 0.15f),
            Color(0xFFB45309)
        )
        ConversionSourceType.RECENT -> Pair(
            Color(0xFF0284C7).copy(alpha = 0.12f),
            Color(0xFF0284C7)
        )
        ConversionSourceType.STALE -> Pair(
            Color(0xFFDC2626).copy(alpha = 0.12f),
            Color(0xFFDC2626)
        )
        ConversionSourceType.OFFLINE_REFERENCE -> Pair(
            Color(0xFF6B7280).copy(alpha = 0.12f),
            Color(0xFF4B5563)
        )
        ConversionSourceType.MANUAL -> Pair(
            Color(0xFF7C3AED).copy(alpha = 0.12f),
            Color(0xFF7C3AED)
        )
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = resolution.summary,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}


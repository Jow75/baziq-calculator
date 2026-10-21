package com.omnicalc.platform.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calculation_history")
data class CalculationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val toolId: String,
    val expression: String,
    val result: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val inputsJson: String = "",
    val isFavorite: Boolean = false
)

@Entity(tableName = "favorite_tools")
data class FavoriteToolEntity(
    @PrimaryKey
    val toolId: String,
    val orderIndex: Int = 0,
    val addedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_currency_rates")
data class CachedRateEntity(
    @PrimaryKey
    val currencyCode: String,
    val rateToBase: Double,
    val baseCurrency: String = "USD",
    val providerName: String = "StaticReference",
    val timestampMs: Long = System.currentTimeMillis(),
    val status: String = "RECENT"
)

@Entity(tableName = "currency_workspace_items")
data class WorkspaceCurrencyEntity(
    @PrimaryKey
    val currencyCode: String,
    val orderIndex: Int,
    val isPinned: Boolean = true
)

@Entity(tableName = "custom_rate_overrides")
data class CustomRateOverrideEntity(
    @PrimaryKey
    val currencyCode: String,
    val customRateToBase: Double,
    val baseCurrency: String = "USD",
    val updatedAtMs: Long = System.currentTimeMillis()
)

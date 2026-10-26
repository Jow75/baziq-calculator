package com.omnicalc.platform

import com.omnicalc.platform.core.currency.CurrencyRateProvider
import com.omnicalc.platform.core.currency.CurrencyRateSet
import com.omnicalc.platform.core.currency.FreshnessStatus
import com.omnicalc.platform.core.database.daos.CurrencyDao
import com.omnicalc.platform.core.database.entities.CachedRateEntity
import com.omnicalc.platform.core.database.entities.CustomRateOverrideEntity
import com.omnicalc.platform.core.database.entities.WorkspaceCurrencyEntity
import com.omnicalc.platform.core.preferences.LocalCurrencyPreferences
import com.omnicalc.platform.core.preferences.LocalCurrencySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Room-free currency DAO shared by the converter tests. */
internal class FakeCurrencyDao : CurrencyDao() {
    private val rates = MutableStateFlow<List<CachedRateEntity>>(emptyList())
    private val workspace = MutableStateFlow<List<WorkspaceCurrencyEntity>>(emptyList())
    private val overrides = MutableStateFlow<List<CustomRateOverrideEntity>>(emptyList())

    override fun getAllCachedRatesFlow(): Flow<List<CachedRateEntity>> = rates
    override suspend fun getAllCachedRates(): List<CachedRateEntity> = rates.value
    override suspend fun insertRates(rates: List<CachedRateEntity>) {
        val byCode = this.rates.value.associateBy { it.currencyCode }.toMutableMap()
        rates.forEach { byCode[it.currencyCode] = it }
        this.rates.value = byCode.values.toList()
    }
    override fun getWorkspaceCurrencies(): Flow<List<WorkspaceCurrencyEntity>> = workspace
    override suspend fun getWorkspaceCurrenciesList(): List<WorkspaceCurrencyEntity> = workspace.value.sortedBy { it.orderIndex }
    override suspend fun insertWorkspaceItem(item: WorkspaceCurrencyEntity) { workspace.value = workspace.value + item }
    override suspend fun insertWorkspaceItems(items: List<WorkspaceCurrencyEntity>) { workspace.value = workspace.value + items }
    override suspend fun clearWorkspaceItems() { workspace.value = emptyList() }
    override suspend fun removeWorkspaceItem(code: String) { workspace.value = workspace.value.filterNot { it.currencyCode == code } }
    override fun getCustomOverrides(): Flow<List<CustomRateOverrideEntity>> = overrides
    override suspend fun setCustomOverride(override: CustomRateOverrideEntity) { overrides.value = overrides.value + override }
    override suspend fun removeCustomOverride(code: String) { overrides.value = overrides.value.filterNot { it.currencyCode == code } }
    override suspend fun clearCachedRates() { rates.value = emptyList() }
}

/** Always returns the same USD-based rates; counts how often it was asked. */
internal class FixedRateProvider(
    private val rates: Map<String, Double> = mapOf("USD" to 1.0, "EUR" to 0.9, "KES" to 130.0, "TZS" to 2600.0, "KMF" to 440.0)
) : CurrencyRateProvider {
    override val providerName = "Fixed"
    var calls = 0
    override suspend fun fetchRates(baseCurrency: String): Result<CurrencyRateSet> {
        calls++
        return Result.success(CurrencyRateSet(baseCurrency, rates, System.currentTimeMillis(), providerName, FreshnessStatus.CURRENT))
    }
}

/** In-memory stand-in for the DataStore-backed preferences. */
internal class FakeLocalCurrencyPrefs(initial: LocalCurrencySettings = LocalCurrencySettings()) : LocalCurrencyPreferences {
    val state = MutableStateFlow(initial)
    override val localCurrencySettingsFlow: Flow<LocalCurrencySettings> = state
    override suspend fun setAutoLocalCurrency(enabled: Boolean) { state.value = state.value.copy(autoLocalCurrency = enabled) }
    override suspend fun setLocationPromptAnswered() { state.value = state.value.copy(locationPromptAnswered = true) }
    override suspend fun setLastAppliedCountry(countryCode: String) { state.value = state.value.copy(lastAppliedCountry = countryCode) }
    override suspend fun setBaseCurrency(currencyCode: String) { state.value = state.value.copy(baseCurrency = currencyCode) }
}

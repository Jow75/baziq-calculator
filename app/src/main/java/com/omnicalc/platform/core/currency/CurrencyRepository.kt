package com.omnicalc.platform.core.currency

import com.omnicalc.platform.core.database.daos.CurrencyDao
import com.omnicalc.platform.core.database.entities.CachedRateEntity
import com.omnicalc.platform.core.database.entities.CustomRateOverrideEntity
import com.omnicalc.platform.core.database.entities.WorkspaceCurrencyEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class CurrencyRepository(
    private val currencyDao: CurrencyDao,
    private val primaryProvider: CurrencyRateProvider = ApiCurrencyRateProvider(),
    private val secondaryProvider: CurrencyRateProvider = SecondaryCurrencyRateProvider(),
    private val fallbackProvider: CurrencyRateProvider = StaticFallbackRateProvider(),
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val MC = MathContext(18, RoundingMode.HALF_EVEN)
    private val workspaceMutex = Mutex()

    @Volatile
    private var isWorkspaceSeeded = false

    val workspaceCurrencies: Flow<List<CurrencyInfo>> = currencyDao.getWorkspaceCurrencies()
        .map { list ->
            if (list.isEmpty()) {
                DEFAULT_WORKSPACE_CODES.map { CurrencyCatalog.find(it) }
            } else {
                list.map { CurrencyCatalog.find(it.currencyCode) }
            }
        }

    val cachedRatesFlow: Flow<List<CachedRateEntity>> = currencyDao.getAllCachedRatesFlow()

    val customOverridesFlow: Flow<Map<String, Double>> = currencyDao.getCustomOverrides()
        .map { list -> list.associate { it.currencyCode to it.customRateToBase } }

    private val _rateState = MutableStateFlow<CurrencyRateSet>(
        CurrencyRateSet(
            baseCurrency = "USD",
            rates = emptyMap(),
            timestampMs = System.currentTimeMillis() - 3600_000L,
            providerName = "Offline Reference",
            status = FreshnessStatus.OFFLINE
        )
    )
    val rateState: StateFlow<CurrencyRateSet> = _rateState.asStateFlow()

    init {
        externalScope.launch {
            initSeedData()
        }
    }

    private fun computeFreshness(timestampMs: Long, isOnlineSuccess: Boolean): FreshnessStatus {
        if (!isOnlineSuccess) {
            val ageMs = System.currentTimeMillis() - timestampMs
            return if (ageMs >= 24 * 3600_000L) FreshnessStatus.STALE else FreshnessStatus.OFFLINE
        }
        val ageMs = System.currentTimeMillis() - timestampMs
        return when {
            ageMs < 6 * 3600_000L -> FreshnessStatus.CURRENT
            ageMs < 24 * 3600_000L -> FreshnessStatus.RECENT
            else -> FreshnessStatus.STALE
        }
    }


    suspend fun ensureDefaultsSeeded() = withContext(ioDispatcher) {
        workspaceMutex.withLock {
            if (isWorkspaceSeeded) return@withLock
            val currentWorkspace = currencyDao.getWorkspaceCurrenciesList()
            if (currentWorkspace.isEmpty()) {
                val defaults = DEFAULT_WORKSPACE_CODES.mapIndexed { index, code ->
                    WorkspaceCurrencyEntity(
                        currencyCode = code,
                        orderIndex = index,
                        isPinned = true
                    )
                }
                currencyDao.replaceWorkspaceItems(defaults)
            }
            isWorkspaceSeeded = true
        }
    }

    private suspend fun initSeedData() = withContext(ioDispatcher) {
        ensureDefaultsSeeded()
        val existingRates = currencyDao.getAllCachedRates()
        if (existingRates.isEmpty()) {
            val fallback = fallbackProvider.fetchRates("USD").getOrNull()
            if (fallback != null) {
                val entities = fallback.rates.map { (code, rate) ->
                    CachedRateEntity(
                        currencyCode = code,
                        rateToBase = rate,
                        baseCurrency = "USD",
                        providerName = fallbackProvider.providerName,
                        timestampMs = fallback.timestampMs,
                        status = RateSource.OFFLINE_REFERENCE.name
                    )
                }
                currencyDao.insertRates(entities)
                _rateState.value = fallback.copy(
                    status = FreshnessStatus.OFFLINE,
                    rateSources = fallback.rates.keys.associateWith { RateSource.OFFLINE_REFERENCE }
                )
            }
        } else {
            val ratesMap = existingRates.associate { it.currencyCode to it.rateToBase }
            val first = existingRates.first()
            val isOfflineReference = existingRates.all { it.status == RateSource.OFFLINE_REFERENCE.name } || first.providerName == fallbackProvider.providerName
            val freshness = if (isOfflineReference) {
                FreshnessStatus.OFFLINE
            } else {
                computeFreshness(first.timestampMs, isOnlineSuccess = false)
            }
            val isStale = freshness == FreshnessStatus.STALE

            val sourcesMap = existingRates.associate { entity ->
                val src = when (entity.status) {
                    RateSource.OFFLINE_REFERENCE.name -> RateSource.OFFLINE_REFERENCE
                    RateSource.MANUAL.name -> RateSource.MANUAL
                    else -> if (isStale) RateSource.STALE else RateSource.RECENT
                }
                entity.currencyCode to src
            }

            _rateState.value = CurrencyRateSet(
                baseCurrency = first.baseCurrency,
                rates = ratesMap,
                timestampMs = first.timestampMs,
                providerName = first.providerName,
                status = freshness,
                rateSources = sourcesMap
            )
        }
    }

    suspend fun refreshRates(force: Boolean = true): Result<CurrencyRateSet> = withContext(ioDispatcher) {
        if (!force && _rateState.value.status == FreshnessStatus.CURRENT && (System.currentTimeMillis() - _rateState.value.timestampMs) < 3600_000L) {
            return@withContext Result.success(_rateState.value)
        }
        // Tier 1: Try Primary Provider (Open Exchange)
        val primaryResult = primaryProvider.fetchRates("USD")
        if (primaryResult.isSuccess) {
            val rateSet = primaryResult.getOrThrow()
            val liveKeys = rateSet.rates.keys.toSet()

            // Merge on top of static reference so all 170+ currencies remain accessible
            val mergedRates = StaticFallbackRateProvider.STATIC_RATES.toMutableMap().apply {
                putAll(rateSet.rates)
            }
            val freshness = computeFreshness(rateSet.timestampMs, isOnlineSuccess = true)

            // Differentiate LIVE from OFFLINE_REFERENCE
            val sourcesMap = mutableMapOf<String, RateSource>()
            sourcesMap["USD"] = if (freshness == FreshnessStatus.CURRENT) RateSource.LIVE else RateSource.RECENT
            mergedRates.forEach { (code, _) ->
                if (code != "USD") {
                    sourcesMap[code] = if (liveKeys.contains(code)) {
                        if (freshness == FreshnessStatus.CURRENT) RateSource.LIVE else RateSource.RECENT
                    } else {
                        RateSource.OFFLINE_REFERENCE
                    }
                }
            }

            val updatedRateSet = rateSet.copy(
                rates = mergedRates,
                status = freshness,
                rateSources = sourcesMap
            )
            saveRatesToDb(updatedRateSet)
            _rateState.value = updatedRateSet
            return@withContext Result.success(updatedRateSet)
        }

        // Tier 2: Try Secondary Provider (Frankfurter / ECB)
        val secondaryResult = secondaryProvider.fetchRates("USD")
        if (secondaryResult.isSuccess) {
            val rateSet = secondaryResult.getOrThrow()
            val liveKeys = rateSet.rates.keys.toSet()

            val mergedRates = StaticFallbackRateProvider.STATIC_RATES.toMutableMap().apply {
                putAll(_rateState.value.rates)
                putAll(rateSet.rates)
            }
            val freshness = computeFreshness(rateSet.timestampMs, isOnlineSuccess = true)

            val sourcesMap = mutableMapOf<String, RateSource>()
            sourcesMap["USD"] = if (freshness == FreshnessStatus.CURRENT) RateSource.LIVE else RateSource.RECENT
            mergedRates.forEach { (code, _) ->
                if (code != "USD") {
                    sourcesMap[code] = if (liveKeys.contains(code)) {
                        if (freshness == FreshnessStatus.CURRENT) RateSource.LIVE else RateSource.RECENT
                    } else {
                        _rateState.value.rateSources[code] ?: RateSource.OFFLINE_REFERENCE
                    }
                }
            }

            val updatedRateSet = rateSet.copy(
                rates = mergedRates,
                status = freshness,
                rateSources = sourcesMap
            )
            saveRatesToDb(updatedRateSet)
            _rateState.value = updatedRateSet
            return@withContext Result.success(updatedRateSet)
        }

        // Tier 3: Local Cached Rates from Room DB
        val existingRates = currencyDao.getAllCachedRates()
        if (existingRates.isNotEmpty()) {
            val ratesMap = existingRates.associate { it.currencyCode to it.rateToBase }
            val first = existingRates.first()
            val isOfflineReference = existingRates.all { it.status == RateSource.OFFLINE_REFERENCE.name } || first.providerName == fallbackProvider.providerName
            val freshness = if (isOfflineReference) {
                FreshnessStatus.OFFLINE
            } else {
                computeFreshness(first.timestampMs, isOnlineSuccess = false)
            }
            val isStale = freshness == FreshnessStatus.STALE

            val sourcesMap = existingRates.associate { entity ->
                val src = when (entity.status) {
                    RateSource.OFFLINE_REFERENCE.name -> RateSource.OFFLINE_REFERENCE
                    RateSource.MANUAL.name -> RateSource.MANUAL
                    else -> if (isStale) RateSource.STALE else RateSource.RECENT
                }
                entity.currencyCode to src
            }

            val cachedSet = CurrencyRateSet(
                baseCurrency = first.baseCurrency,
                rates = ratesMap,
                timestampMs = first.timestampMs,
                providerName = first.providerName,
                status = freshness,
                rateSources = sourcesMap
            )
            _rateState.value = cachedSet
            return@withContext Result.success(cachedSet)
        }

        // Tier 4: Static Fallback Reference Dataset
        val fb = fallbackProvider.fetchRates("USD").getOrNull()
        if (fb != null) {
            val offlineSet = fb.copy(
                status = FreshnessStatus.OFFLINE,
                rateSources = fb.rates.keys.associateWith { RateSource.OFFLINE_REFERENCE }
            )
            _rateState.value = offlineSet
            return@withContext Result.success(offlineSet)
        }

        Result.failure(primaryResult.exceptionOrNull() ?: Exception("All currency rate providers failed"))
    }

    private suspend fun saveRatesToDb(rateSet: CurrencyRateSet) {
        val entities = rateSet.rates.map { (code, rate) ->
            val source = rateSet.rateSources[code] ?: RateSource.OFFLINE_REFERENCE
            CachedRateEntity(
                currencyCode = code,
                rateToBase = rate,
                baseCurrency = rateSet.baseCurrency,
                providerName = if (source == RateSource.OFFLINE_REFERENCE) "StaticReference" else rateSet.providerName,
                timestampMs = rateSet.timestampMs,
                status = source.name
            )
        }
        currencyDao.insertRates(entities)
    }

    suspend fun addCurrency(code: String) = withContext(ioDispatcher) {
        workspaceMutex.withLock {
            val upper = code.uppercase()
            val current = currencyDao.getWorkspaceCurrenciesList()
            if (current.any { it.currencyCode.equals(upper, ignoreCase = true) }) return@withLock
            val nextIndex = (current.maxOfOrNull { it.orderIndex } ?: -1) + 1
            currencyDao.insertWorkspaceItem(
                WorkspaceCurrencyEntity(
                    currencyCode = upper,
                    orderIndex = nextIndex,
                    isPinned = true
                )
            )
        }
    }

    suspend fun reorderWorkspace(orderedCodes: List<String>) = withContext(ioDispatcher) {
        workspaceMutex.withLock {
            val currentWorkspace = currencyDao.getWorkspaceCurrenciesList()
            val currentCodes = currentWorkspace.map { it.currencyCode.uppercase() }
            if (currentCodes.isEmpty()) return@withLock

            // 1. Retain specified order for codes that exist in current workspace
            val orderedUpper = orderedCodes.map { it.uppercase() }.distinct()
            val validReordered = orderedUpper.filter { it in currentCodes }

            // 2. Critical state integrity: any existing workspace currency omitted from orderedCodes
            // is preserved and appended to the end, strictly guaranteeing MEMBERSHIP CANNOT BE LOST.
            val missingFromOrder = currentCodes.filter { it !in validReordered }
            val completeOrder = validReordered + missingFromOrder

            val entities = completeOrder.mapIndexed { index, code ->
                WorkspaceCurrencyEntity(
                    currencyCode = code,
                    orderIndex = index,
                    isPinned = true
                )
            }
            // Atomic: delete all + insert new complete order — prevents phantom currencies
            currencyDao.replaceWorkspaceItems(entities)
        }
    }

    suspend fun removeCurrency(code: String) = withContext(ioDispatcher) {
        workspaceMutex.withLock {
            val current = currencyDao.getWorkspaceCurrenciesList()
            if (current.size <= 2) return@withLock // Minimum two currencies invariant
            val filtered = current.filter { !it.currencyCode.equals(code, ignoreCase = true) }
            val reIndexed = filtered.sortedBy { it.orderIndex }.mapIndexed { index, entity ->
                entity.copy(orderIndex = index)
            }
            currencyDao.replaceWorkspaceItems(reIndexed)
        }
    }

    suspend fun setCustomOverride(code: String, rateToBase: Double) = withContext(ioDispatcher) {
        currencyDao.setCustomOverride(
            CustomRateOverrideEntity(
                currencyCode = code.uppercase(),
                customRateToBase = rateToBase,
                baseCurrency = "USD",
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeCustomOverride(code: String) = withContext(ioDispatcher) {
        currencyDao.removeCustomOverride(code.uppercase())
    }

    fun convert(
        amount: BigDecimal,
        fromCode: String,
        toCode: String,
        ratesMap: Map<String, Double>,
        overrides: Map<String, Double> = emptyMap()
    ): BigDecimal {
        if (fromCode.equals(toCode, ignoreCase = true)) return amount
        if (amount.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO

        val fromUpper = fromCode.uppercase()
        val toUpper = toCode.uppercase()

        val fromRate = overrides[fromUpper]
            ?: ratesMap[fromUpper]
            ?: StaticFallbackRateProvider.STATIC_RATES[fromUpper]
            ?: 1.0
        val toRate = overrides[toUpper]
            ?: ratesMap[toUpper]
            ?: StaticFallbackRateProvider.STATIC_RATES[toUpper]
            ?: 1.0

        if (fromRate <= 0.0 || toRate <= 0.0) {
            throw ArithmeticException("Invalid exchange rate for conversion: $fromUpper ($fromRate) to $toUpper ($toRate)")
        }

        // converted = amount * (toRate / fromRate)
        val fromRateBd = BigDecimal(fromRate.toString())
        val toRateBd = BigDecimal(toRate.toString())

        return amount.multiply(toRateBd, MC).divide(fromRateBd, MC)
    }

    fun convertWithResolution(
        amount: BigDecimal,
        fromCode: String,
        toCode: String,
        rateSet: CurrencyRateSet,
        overrides: Map<String, Double> = emptyMap()
    ): Pair<BigDecimal, ConversionResolution> {
        val converted = convert(amount, fromCode, toCode, rateSet.rates, overrides)
        val resolution = rateSet.resolveConversion(fromCode, toCode, overrides)
        return Pair(converted, resolution)
    }

    companion object {
        val DEFAULT_WORKSPACE_CODES = listOf("USD", "EUR", "KES", "TZS", "KMF")
    }
}

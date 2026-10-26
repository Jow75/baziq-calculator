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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val MC = MathContext(18, RoundingMode.HALF_EVEN)
    private val workspaceMutex = Mutex()

    @Volatile
    private var isWorkspaceSeeded = false

    @Volatile
    private var lastAutoRefreshAttemptMs = Long.MIN_VALUE

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

    // Until the cache is read, conversions use the built-in reference table, labelled as such.
    private val _rateState = MutableStateFlow(
        CurrencyRateSet(
            baseCurrency = "USD",
            rates = StaticFallbackRateProvider.STATIC_RATES,
            timestampMs = StaticFallbackRateProvider.METADATA_TIMESTAMP_MS,
            providerName = fallbackProvider.providerName,
            status = FreshnessStatus.OFFLINE,
            rateSources = StaticFallbackRateProvider.STATIC_RATES.keys.associateWith { RateSource.OFFLINE_REFERENCE }
        )
    )
    val rateState: StateFlow<CurrencyRateSet> = _rateState.asStateFlow()

    private val seedJob = externalScope.launch {
        initSeedData()
    }

    private fun computeFreshness(timestampMs: Long, isOnlineSuccess: Boolean): FreshnessStatus {
        val ageMs = clock() - timestampMs
        if (!isOnlineSuccess) {
            return if (ageMs >= 24 * 3600_000L) FreshnessStatus.STALE else FreshnessStatus.OFFLINE
        }
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
            _rateState.value = rateSetFromCache(existingRates)
        }
    }

    /** Rebuilds a rate set from cached rows, labelled as saved (not freshly downloaded) rates. */
    private fun rateSetFromCache(rows: List<CachedRateEntity>): CurrencyRateSet {
        val ratesMap = rows.associate { it.currencyCode to it.rateToBase }
        // Reference rows are stored under their own provider name and timestamp, so describe
        // the set by a row that came from a live provider when there is one.
        val liveRow = rows.firstOrNull { it.status != RateSource.OFFLINE_REFERENCE.name }
        val representative = liveRow ?: rows.first()
        val isOfflineReference = liveRow == null || representative.providerName == fallbackProvider.providerName
        val freshness = if (isOfflineReference) {
            FreshnessStatus.OFFLINE
        } else {
            computeFreshness(representative.timestampMs, isOnlineSuccess = false)
        }
        val isStale = freshness == FreshnessStatus.STALE

        val sourcesMap = rows.associate { entity ->
            val src = when (entity.status) {
                RateSource.OFFLINE_REFERENCE.name -> RateSource.OFFLINE_REFERENCE
                RateSource.MANUAL.name -> RateSource.MANUAL
                else -> if (isStale) RateSource.STALE else RateSource.RECENT
            }
            entity.currencyCode to src
        }

        return CurrencyRateSet(
            baseCurrency = representative.baseCurrency,
            rates = ratesMap,
            timestampMs = representative.timestampMs,
            providerName = representative.providerName,
            status = freshness,
            rateSources = sourcesMap
        )
    }

    /** Suspends until the cached rates (or the built-in reference) have been loaded. */
    suspend fun awaitInitialized() = seedJob.join()

    /**
     * Freshness policy for automatic refreshes. Saved rates are always usable immediately;
     * a download is due only when this session has no download younger than
     * [AUTO_REFRESH_INTERVAL_MS], and is attempted at most once per [AUTO_RETRY_INTERVAL_MS]
     * so an offline device is not retried on every visit.
     *
     * @return true when an automatic refresh is due; the attempt is recorded, so the caller
     *         is expected to call [refreshRates].
     */
    suspend fun claimAutoRefresh(): Boolean {
        awaitInitialized()
        val now = clock()
        val lastFetch = _rateState.value.fetchedAtMs
        if (lastFetch != null && now - lastFetch < AUTO_REFRESH_INTERVAL_MS) return false
        if (lastAutoRefreshAttemptMs != Long.MIN_VALUE && now - lastAutoRefreshAttemptMs < AUTO_RETRY_INTERVAL_MS) return false
        lastAutoRefreshAttemptMs = now
        return true
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
                rateSources = sourcesMap,
                fetchedAtMs = clock()
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
                rateSources = sourcesMap,
                fetchedAtMs = clock()
            )
            saveRatesToDb(updatedRateSet)
            _rateState.value = updatedRateSet
            return@withContext Result.success(updatedRateSet)
        }

        // Tier 3: Local Cached Rates from Room DB
        val existingRates = currencyDao.getAllCachedRates()
        if (existingRates.isNotEmpty()) {
            val cachedSet = rateSetFromCache(existingRates)
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

        // A currency without any known rate is an error, never an assumed 1:1 rate.
        val fromRate = rateOf(fromUpper, ratesMap, overrides)
            ?: throw ArithmeticException("No exchange rate available for $fromUpper")
        val toRate = rateOf(toUpper, ratesMap, overrides)
            ?: throw ArithmeticException("No exchange rate available for $toUpper")

        if (fromRate <= 0.0 || toRate <= 0.0) {
            throw ArithmeticException("Invalid exchange rate for conversion: $fromUpper ($fromRate) to $toUpper ($toRate)")
        }

        // converted = amount * (toRate / fromRate)
        val fromRateBd = BigDecimal(fromRate.toString())
        val toRateBd = BigDecimal(toRate.toString())

        return amount.multiply(toRateBd, MC).divide(fromRateBd, MC)
    }

    /** The rate of [code] against the set's base: manual override, then downloaded, then built-in. */
    private fun rateOf(code: String, ratesMap: Map<String, Double>, overrides: Map<String, Double>): Double? =
        overrides[code] ?: ratesMap[code] ?: StaticFallbackRateProvider.STATIC_RATES[code]

    /**
     * The number shown in "1 FROM = x TO" labels. Rates are stored as Double, whose
     * toString() is the shortest decimal that round-trips, i.e. the provider's own literal
     * (129.84 stays 129.84); the label keeps those digits instead of rounding them.
     */
    fun formatUnitRate(
        fromCode: String,
        toCode: String,
        ratesMap: Map<String, Double>,
        overrides: Map<String, Double> = emptyMap()
    ): String {
        val unitRate = convert(BigDecimal.ONE, fromCode, toCode, ratesMap, overrides)
        val providerValues = listOfNotNull(
            rateOf(fromCode.uppercase(), ratesMap, overrides),
            rateOf(toCode.uppercase(), ratesMap, overrides)
        ).map { BigDecimal(it.toString()) }
        return CurrencyFormatter.formatRate(unitRate, CurrencyFormatter.rateSignificantDigits(*providerValues.toTypedArray()))
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

        /** Downloads younger than this are reused without contacting the rate service. */
        const val AUTO_REFRESH_INTERVAL_MS = 6 * 3600_000L

        /** Minimum gap between automatic attempts, so failures are not retried on every visit. */
        const val AUTO_RETRY_INTERVAL_MS = 15 * 60_000L
    }
}

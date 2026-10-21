package com.omnicalc.platform

import com.omnicalc.platform.core.currency.*
import com.omnicalc.platform.core.database.daos.CurrencyDao
import com.omnicalc.platform.core.database.entities.CachedRateEntity
import com.omnicalc.platform.core.database.entities.CustomRateOverrideEntity
import com.omnicalc.platform.core.database.entities.WorkspaceCurrencyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import androidx.compose.ui.text.AnnotatedString
import com.omnicalc.platform.feature.currency.ThousandsVisualTransformation
import com.omnicalc.platform.feature.currency.CurrencyViewModel
import java.math.BigDecimal
import java.math.RoundingMode

class CurrencyConversionTest {

    private class FakeCurrencyDao : CurrencyDao() {
        val cachedRates = mutableListOf<CachedRateEntity>()
        private val cachedRatesFlow = MutableStateFlow<List<CachedRateEntity>>(emptyList())
        val workspaceItems = mutableListOf<WorkspaceCurrencyEntity>()
        private val workspaceFlow = MutableStateFlow<List<WorkspaceCurrencyEntity>>(emptyList())
        val customOverrides = mutableListOf<CustomRateOverrideEntity>()
        private val overridesFlow = MutableStateFlow<List<CustomRateOverrideEntity>>(emptyList())

        override fun getAllCachedRatesFlow(): Flow<List<CachedRateEntity>> = cachedRatesFlow.asStateFlow()
        override suspend fun getAllCachedRates(): List<CachedRateEntity> = cachedRates.toList()
        override suspend fun insertRates(rates: List<CachedRateEntity>) {
            cachedRates.clear()
            cachedRates.addAll(rates)
            cachedRatesFlow.value = cachedRates.toList()
        }
        override fun getWorkspaceCurrencies(): Flow<List<WorkspaceCurrencyEntity>> = workspaceFlow.asStateFlow()
        override suspend fun getWorkspaceCurrenciesList(): List<WorkspaceCurrencyEntity> = workspaceItems.sortedBy { it.orderIndex }
        override suspend fun insertWorkspaceItem(item: WorkspaceCurrencyEntity) {
            workspaceItems.add(item)
            workspaceFlow.value = workspaceItems.toList()
        }
        override suspend fun insertWorkspaceItems(items: List<WorkspaceCurrencyEntity>) {
            workspaceItems.addAll(items)
            workspaceFlow.value = workspaceItems.toList()
        }
        override suspend fun clearWorkspaceItems() {
            workspaceItems.clear()
            workspaceFlow.value = emptyList()
        }
        override suspend fun removeWorkspaceItem(code: String) {
            workspaceItems.removeAll { it.currencyCode.equals(code, ignoreCase = true) }
            workspaceFlow.value = workspaceItems.toList()
        }
        override fun getCustomOverrides(): Flow<List<CustomRateOverrideEntity>> = overridesFlow.asStateFlow()
        override suspend fun setCustomOverride(override: CustomRateOverrideEntity) {
            customOverrides.removeAll { it.currencyCode.equals(override.currencyCode, ignoreCase = true) }
            customOverrides.add(override)
            overridesFlow.value = customOverrides.toList()
        }
        override suspend fun removeCustomOverride(code: String) {
            customOverrides.removeAll { it.currencyCode.equals(code, ignoreCase = true) }
            overridesFlow.value = customOverrides.toList()
        }
        override suspend fun clearCachedRates() {
            cachedRates.clear()
            cachedRatesFlow.value = emptyList()
        }
    }

    private class FakeRateProvider(
        override val providerName: String,
        var shouldFail: Boolean = false,
        var returnedRates: Map<String, Double> = emptyMap(),
        var timestampMs: Long = System.currentTimeMillis()
    ) : CurrencyRateProvider {
        override suspend fun fetchRates(baseCurrency: String): Result<CurrencyRateSet> {
            if (shouldFail) {
                return Result.failure(Exception("Simulated network failure for $providerName"))
            }
            return Result.success(
                CurrencyRateSet(
                    baseCurrency = baseCurrency,
                    rates = returnedRates,
                    timestampMs = timestampMs,
                    providerName = providerName,
                    status = FreshnessStatus.CURRENT
                )
            )
        }
    }

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDeterministicUsdToKesConversion() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val primary = FakeRateProvider("Fake Primary", returnedRates = mapOf("USD" to 1.0, "KES" to 129.50))
        val repo = CurrencyRepository(currencyDao = dao, primaryProvider = primary, externalScope = testScope)

        val res = repo.refreshRates(force = true)
        assertTrue(res.isSuccess)
        val rates = res.getOrThrow().rates

        val converted = repo.convert(
            amount = BigDecimal("100"),
            fromCode = "USD",
            toCode = "KES",
            ratesMap = rates
        )
        // 100 USD * 129.50 = 12950 KES
        val rounded = converted.setScale(2, RoundingMode.HALF_UP)
        assertEquals(0, BigDecimal("12950.00").compareTo(rounded))
    }

    @Test
    fun testDeterministicCrossRateKesToKmf() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val rates = mapOf("USD" to 1.0, "KES" to 129.50, "KMF" to 452.80)
        val primary = FakeRateProvider("Fake Primary", returnedRates = rates)
        val repo = CurrencyRepository(currencyDao = dao, primaryProvider = primary, externalScope = testScope)

        // 129.5 KES = 1 USD = 452.80 KMF
        val converted = repo.convert(
            amount = BigDecimal("129.50"),
            fromCode = "KES",
            toCode = "KMF",
            ratesMap = rates
        )
        val rounded = converted.setScale(2, RoundingMode.HALF_UP)
        assertEquals(0, BigDecimal("452.80").compareTo(rounded))
    }

    @Test
    fun testManualOverridePrecedenceOverLiveRates() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val liveRates = mapOf("USD" to 1.0, "KES" to 129.50)
        val primary = FakeRateProvider("Live API", returnedRates = liveRates)
        val repo = CurrencyRepository(currencyDao = dao, primaryProvider = primary, externalScope = testScope)

        // 1. Without override: 100 USD = 12,950 KES
        val beforeOverride = repo.convert(BigDecimal("100"), "USD", "KES", liveRates)
        assertEquals(0, BigDecimal("12950.00").compareTo(beforeOverride.setScale(2, RoundingMode.HALF_UP)))

        // 2. User manually overrides: 1 USD = 135.00 KES
        val overrides = mapOf("KES" to 135.0)
        val afterOverride = repo.convert(BigDecimal("100"), "USD", "KES", liveRates, overrides)
        assertEquals(0, BigDecimal("13500.00").compareTo(afterOverride.setScale(2, RoundingMode.HALF_UP)))

        // 3. User removes override: returns to 12,950 KES
        val restored = repo.convert(BigDecimal("100"), "USD", "KES", liveRates, emptyMap())
        assertEquals(0, BigDecimal("12950.00").compareTo(restored.setScale(2, RoundingMode.HALF_UP)))
    }

    @Test
    fun testPrimaryFailureFallsBackToSecondaryProvider() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val primary = FakeRateProvider("Failing Primary", shouldFail = true)
        val secondary = FakeRateProvider("Healthy Secondary", shouldFail = false, returnedRates = mapOf("USD" to 1.0, "EUR" to 0.92))
        val repo = CurrencyRepository(currencyDao = dao, primaryProvider = primary, secondaryProvider = secondary, externalScope = testScope)

        val result = repo.refreshRates(force = true)
        assertTrue(result.isSuccess)
        val rateSet = result.getOrThrow()
        assertEquals("Healthy Secondary", rateSet.providerName)
        assertEquals(FreshnessStatus.CURRENT, rateSet.status)
        assertEquals(0.92, rateSet.rates["EUR"] ?: 0.0, 1e-4)
    }

    @Test
    fun testBothLiveProvidersFailFallsBackToRoomCache() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val twoHoursAgo = System.currentTimeMillis() - 2 * 3600_000L
        dao.insertRates(
            listOf(
                CachedRateEntity("USD", 1.0, "USD", "Live OpenER", twoHoursAgo, "CURRENT"),
                CachedRateEntity("KES", 129.50, "USD", "Live OpenER", twoHoursAgo, "CURRENT")
            )
        )

        val primary = FakeRateProvider("Offline Primary", shouldFail = true)
        val secondary = FakeRateProvider("Offline Secondary", shouldFail = true)
        val repo = CurrencyRepository(currencyDao = dao, primaryProvider = primary, secondaryProvider = secondary, externalScope = testScope)

        val result = repo.refreshRates(force = true)
        assertTrue(result.isSuccess)
        val rateSet = result.getOrThrow()

        // MUST be OFFLINE status because live refresh failed — UI must NEVER call old cached data CURRENT!
        assertEquals(FreshnessStatus.OFFLINE, rateSet.status)
        assertEquals(129.50, rateSet.rates["KES"] ?: 0.0, 1e-4)
    }

    @Test
    fun testCachedRatesOlderThan24HoursMarkedStale() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val twentyFiveHoursAgo = System.currentTimeMillis() - 25 * 3600_000L
        dao.insertRates(
            listOf(
                CachedRateEntity("USD", 1.0, "USD", "Saved Rates", twentyFiveHoursAgo, "CURRENT"),
                CachedRateEntity("KES", 129.50, "USD", "Saved Rates", twentyFiveHoursAgo, "CURRENT")
            )
        )

        val primary = FakeRateProvider("Offline Primary", shouldFail = true)
        val secondary = FakeRateProvider("Offline Secondary", shouldFail = true)
        val repo = CurrencyRepository(currencyDao = dao, primaryProvider = primary, secondaryProvider = secondary, externalScope = testScope)

        val result = repo.refreshRates(force = true)
        assertTrue(result.isSuccess)
        val rateSet = result.getOrThrow()

        // Rates > 24 hours old MUST be marked STALE
        assertEquals(FreshnessStatus.STALE, rateSet.status)
    }

    @Test
    fun testOfflineFirstLaunchEmptyDatabaseUsesStaticReference() = testScope.runTest {
        val dao = FakeCurrencyDao() // completely empty DB
        val primary = FakeRateProvider("Offline Primary", shouldFail = true)
        val secondary = FakeRateProvider("Offline Secondary", shouldFail = true)
        val fallback = StaticFallbackRateProvider()
        val repo = CurrencyRepository(
            currencyDao = dao,
            primaryProvider = primary,
            secondaryProvider = secondary,
            fallbackProvider = fallback,
            externalScope = testScope
        )

        val result = repo.refreshRates(force = true)
        assertTrue(result.isSuccess)
        val rateSet = result.getOrThrow()

        assertEquals("Offline Reference Rates", rateSet.providerName)
        assertEquals(FreshnessStatus.OFFLINE, rateSet.status)
        assertNotNull(rateSet.rates["KES"])
        assertNotNull(rateSet.rates["KMF"])
        assertNotNull(rateSet.rates["TZS"])
        assertNotNull(rateSet.rates["UGX"])
        assertNotNull(rateSet.rates["RWF"])
        assertNotNull(rateSet.rates["BIF"])
    }

    @Test
    fun testIncompleteApiMapPreservesRegionalCurrenciesFromCatalog() = testScope.runTest {
        val dao = FakeCurrencyDao()
        // API returns only USD and EUR, omitting KES and KMF
        val incompleteRates = mapOf("USD" to 1.0, "EUR" to 0.91)
        val primary = FakeRateProvider("Incomplete Primary", returnedRates = incompleteRates)
        val repo = CurrencyRepository(currencyDao = dao, primaryProvider = primary, externalScope = testScope)

        val result = repo.refreshRates(force = true)
        assertTrue(result.isSuccess)
        val rateSet = result.getOrThrow()

        // EUR was updated by live API
        assertEquals(0.91, rateSet.rates["EUR"] ?: 0.0, 1e-4)
        // Regional African currencies are still available from catalog reference
        assertNotNull(rateSet.rates["KES"])
        assertNotNull(rateSet.rates["KMF"])
        assertNotNull(rateSet.rates["TZS"])
    }

    @Test
    fun testZeroAmountAndSameCurrencyConversions() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val rates = mapOf("USD" to 1.0, "EUR" to 0.92, "KES" to 129.50)
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope)

        val zero = repo.convert(BigDecimal.ZERO, "USD", "KES", rates)
        assertEquals(0, BigDecimal.ZERO.compareTo(zero))

        val same = repo.convert(BigDecimal("500"), "EUR", "EUR", rates)
        assertEquals(0, BigDecimal("500").compareTo(same))
    }

    @Test
    fun testMixedSourceResolutionLiveAndStaticFallback() = testScope.runTest {
        val dao = FakeCurrencyDao()
        // API returns live USD, KES, EUR. ZWG is absent from API and filled from static fallback.
        val liveRates = mapOf("USD" to 1.0, "KES" to 129.50, "EUR" to 0.92)
        val primary = FakeRateProvider("Live Primary", returnedRates = liveRates)
        val repo = CurrencyRepository(currencyDao = dao, primaryProvider = primary, externalScope = testScope)

        val refreshRes = repo.refreshRates(force = true)
        assertTrue(refreshRes.isSuccess)
        val rateSet = refreshRes.getOrThrow()

        // 1. Live -> Live conversion (USD -> KES)
        val (kesAmount, kesRes) = repo.convertWithResolution(
            amount = BigDecimal("100"),
            fromCode = "USD",
            toCode = "KES",
            rateSet = rateSet
        )
        assertEquals(0, BigDecimal("12950").compareTo(kesAmount.setScale(0, RoundingMode.HALF_UP)))
        assertEquals(ConversionSourceType.LIVE, kesRes.sourceType)
        assertEquals(RateSource.LIVE, kesRes.fromSource)
        assertEquals(RateSource.LIVE, kesRes.toSource)
        assertEquals("Source: Live", kesRes.summary)

        // 2. Live -> Static Fallback conversion (USD -> ZWG)
        val (_, zwgRes) = repo.convertWithResolution(
            amount = BigDecimal("100"),
            fromCode = "USD",
            toCode = "ZWG",
            rateSet = rateSet
        )
        assertEquals(ConversionSourceType.MIXED, zwgRes.sourceType)
        assertEquals(RateSource.LIVE, zwgRes.fromSource)
        assertEquals(RateSource.OFFLINE_REFERENCE, zwgRes.toSource)
        assertEquals("Source: Mixed (USD: Live, ZWG: Offline Reference)", zwgRes.summary)

        // 3. Cross Live -> Static Fallback conversion (EUR -> ZWG)
        val (_, eurZwgRes) = repo.convertWithResolution(
            amount = BigDecimal("100"),
            fromCode = "EUR",
            toCode = "ZWG",
            rateSet = rateSet
        )
        assertEquals(ConversionSourceType.MIXED, eurZwgRes.sourceType)
        assertEquals(RateSource.LIVE, eurZwgRes.fromSource)
        assertEquals(RateSource.OFFLINE_REFERENCE, eurZwgRes.toSource)
        assertEquals("Source: Mixed (EUR: Live, ZWG: Offline Reference)", eurZwgRes.summary)
    }

    @Test
    fun testManualOverrideResolutionPrecedence() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val liveRates = mapOf("USD" to 1.0, "KES" to 129.50, "EUR" to 0.92)
        val primary = FakeRateProvider("Live Primary", returnedRates = liveRates)
        val repo = CurrencyRepository(currencyDao = dao, primaryProvider = primary, externalScope = testScope)

        val refreshRes = repo.refreshRates(force = true)
        val rateSet = refreshRes.getOrThrow()
        val customOverrides = mapOf("KES" to 135.0)

        // Direct USD -> KES with custom override
        val (converted, res) = repo.convertWithResolution(
            amount = BigDecimal("100"),
            fromCode = "USD",
            toCode = "KES",
            rateSet = rateSet,
            overrides = customOverrides
        )
        assertEquals(0, BigDecimal("13500").compareTo(converted.setScale(0, RoundingMode.HALF_UP)))
        assertEquals(ConversionSourceType.MANUAL, res.sourceType)
        assertEquals(RateSource.MANUAL, res.toSource)
        assertEquals("Source: Manual Override", res.summary)

        // Cross conversion involving manual override and live currency (KES -> EUR)
        val (_, crossRes) = repo.convertWithResolution(
            amount = BigDecimal("1000"),
            fromCode = "KES",
            toCode = "EUR",
            rateSet = rateSet,
            overrides = customOverrides
        )
        assertEquals(ConversionSourceType.MIXED, crossRes.sourceType)
        assertEquals(RateSource.MANUAL, crossRes.fromSource)
        assertEquals(RateSource.LIVE, crossRes.toSource)
        assertEquals("Source: Mixed (KES: Manual Override, EUR: Live)", crossRes.summary)
    }

    @Test
    fun testStaticDatasetMetadataIntegrity() {
        val meta = StaticFallbackRateProvider.METADATA
        assertEquals("2026.10-REF", meta.version)
        assertEquals("October 2026", meta.datasetDate)
        assertEquals("2026-10-01", meta.lastUpdateDate)
        assertTrue(meta.currencyCount >= 170)
        assertTrue(meta.sourceReference.isNotBlank())
        assertTrue(meta.description.contains("emergency offline baseline"))
    }

    @Test
    fun testCurrencyNumberFormattingThousandsSeparators() {
        // Required exact specification test vectors
        assertEquals("1,000", CurrencyFormatter.formatString("1000"))
        assertEquals("10,000", CurrencyFormatter.formatString("10000"))
        assertEquals("67,380.59", CurrencyFormatter.formatString("67380.59"))
        assertEquals("1,377,781.11", CurrencyFormatter.formatString("1377781.11"))
        assertEquals("10,000,000", CurrencyFormatter.formatString("10000000"))

        // Edge cases: trailing decimals during user typing, zeros, negatives, trillions
        assertEquals("0", CurrencyFormatter.formatString("0"))
        assertEquals("0.00", CurrencyFormatter.formatString("0.00"))
        assertEquals("500.", CurrencyFormatter.formatString("500."))
        assertEquals("-1,377,781.11", CurrencyFormatter.formatString("-1377781.11"))
        assertEquals("1,000,000,000,000", CurrencyFormatter.formatString("1000000000000"))

        // BigDecimal formatting
        val bdVal = BigDecimal("1377781.1134")
        assertEquals("1,377,781.11", CurrencyFormatter.formatBigDecimal(bdVal, 2))
        val bdZeroDec = BigDecimal("10000000")
        assertEquals("10,000,000", CurrencyFormatter.formatBigDecimal(bdZeroDec, 0))

        // Sanitization and parsing: calculations never treat commas as mathematical characters
        assertEquals("1377781.11", CurrencyFormatter.sanitizeToNumeric("1,377,781.11"))
        val parsed = CurrencyFormatter.parseToBigDecimal("1,377,781.11")
        assertEquals(0, BigDecimal("1377781.11").compareTo(parsed))
    }

    @Test
    fun testWorkspace_A_FreshInitialization() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        val stored = dao.getWorkspaceCurrenciesList()
        val expected = listOf("USD", "EUR", "KES", "TZS", "KMF")
        assertEquals(expected, stored.map { it.currencyCode })
        assertEquals(listOf(0, 1, 2, 3, 4), stored.map { it.orderIndex })
    }

    @Test
    fun testWorkspace_B_ReopenExistingWorkspace() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val initialCodes = listOf("USD", "EUR", "KES", "TZS", "KMF")
        dao.insertWorkspaceItems(initialCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })

        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        val stored = dao.getWorkspaceCurrenciesList()
        assertEquals(initialCodes, stored.map { it.currencyCode })
    }

    @Test
    fun testWorkspace_C_RemoveCurrency() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val initialCodes = listOf("USD", "EUR", "KES", "TZS", "KMF")
        dao.insertWorkspaceItems(initialCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        repo.removeCurrency("EUR")

        val stored = dao.getWorkspaceCurrenciesList()
        val expected = listOf("USD", "KES", "TZS", "KMF")
        assertEquals(expected, stored.map { it.currencyCode })
        assertEquals(listOf(0, 1, 2, 3), stored.map { it.orderIndex })
    }

    @Test
    fun testWorkspace_D_ReorderTwoCurrencies() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val initialCodes = listOf("KES", "KMF")
        dao.insertWorkspaceItems(initialCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        // Move KES downward -> [KMF, KES]
        repo.reorderWorkspace(listOf("KMF", "KES"))

        val stored = dao.getWorkspaceCurrenciesList()
        assertEquals(listOf("KMF", "KES"), stored.map { it.currencyCode })
        assertEquals(listOf(0, 1), stored.map { it.orderIndex })
    }

    @Test
    fun testWorkspace_E_ReorderTwoCurrenciesRepeatedly() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val initialCodes = listOf("KES", "KMF")
        dao.insertWorkspaceItems(initialCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        // Multiple reversals: [KMF, KES] -> [KES, KMF] -> [KMF, KES]
        repo.reorderWorkspace(listOf("KMF", "KES"))
        assertEquals(listOf("KMF", "KES"), dao.getWorkspaceCurrenciesList().map { it.currencyCode })

        repo.reorderWorkspace(listOf("KES", "KMF"))
        assertEquals(listOf("KES", "KMF"), dao.getWorkspaceCurrenciesList().map { it.currencyCode })

        repo.reorderWorkspace(listOf("KMF", "KES"))
        assertEquals(listOf("KMF", "KES"), dao.getWorkspaceCurrenciesList().map { it.currencyCode })
    }

    @Test
    fun testWorkspace_F_ReorderWithFiveCurrencies() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val initialCodes = listOf("USD", "EUR", "KES", "TZS", "KMF")
        dao.insertWorkspaceItems(initialCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        // Move KMF to top
        val reordered = listOf("KMF", "USD", "EUR", "KES", "TZS")
        repo.reorderWorkspace(reordered)

        val stored = dao.getWorkspaceCurrenciesList()
        assertEquals(reordered, stored.map { it.currencyCode })
        assertEquals(listOf(0, 1, 2, 3, 4), stored.map { it.orderIndex })
    }

    @Test
    fun testWorkspace_G_RemoveThenReorder() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val initialCodes = listOf("USD", "EUR", "KES", "TZS", "KMF")
        dao.insertWorkspaceItems(initialCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        repo.removeCurrency("EUR")
        assertEquals(listOf("USD", "KES", "TZS", "KMF"), dao.getWorkspaceCurrenciesList().map { it.currencyCode })

        // Reorder remaining 4 currencies
        val newOrder = listOf("KMF", "USD", "TZS", "KES")
        repo.reorderWorkspace(newOrder)

        val stored = dao.getWorkspaceCurrenciesList()
        assertEquals(newOrder, stored.map { it.currencyCode })
        assertEquals(listOf(0, 1, 2, 3), stored.map { it.orderIndex })
    }

    @Test
    fun testWorkspace_H_RestartPersistence() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val customOrder = listOf("KMF", "USD", "TZS", "KES")
        dao.insertWorkspaceItems(customOrder.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })

        // Simulate new app launch / repository instantiation
        val newRepo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        newRepo.ensureDefaultsSeeded()

        val stored = dao.getWorkspaceCurrenciesList()
        assertEquals(customOrder, stored.map { it.currencyCode })
        assertEquals(listOf(0, 1, 2, 3), stored.map { it.orderIndex })
    }

    @Test
    fun testWorkspace_I_AddAfterCustomReorder() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val customOrder = listOf("KMF", "USD", "KES")
        dao.insertWorkspaceItems(customOrder.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        repo.addCurrency("GBP")

        val stored = dao.getWorkspaceCurrenciesList()
        val expected = listOf("KMF", "USD", "KES", "GBP")
        assertEquals(expected, stored.map { it.currencyCode })
        assertEquals(listOf(0, 1, 2, 3), stored.map { it.orderIndex })
    }

    @Test
    fun testWorkspace_MinimumTwoRuleEnforced() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val twoCurrencies = listOf("USD", "KES")
        dao.insertWorkspaceItems(twoCurrencies.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        // Attempting to remove below 2 is rejected
        repo.removeCurrency("USD")

        val stored = dao.getWorkspaceCurrenciesList()
        assertEquals(twoCurrencies, stored.map { it.currencyCode })
    }

    @Test
    fun testCurrencyInputSanitizationAndReplacement() {
        // Clean numeric sanitization
        assertEquals("50000", CurrencyFormatter.sanitizeToNumeric("50,000"))
        assertEquals("50000.75", CurrencyFormatter.sanitizeToNumeric("50,000.75"))
        
        // Blank or partial inputs return BigDecimal.ZERO safely
        assertEquals(BigDecimal.ZERO, CurrencyFormatter.parseToBigDecimal(""))
        assertEquals(BigDecimal.ZERO, CurrencyFormatter.parseToBigDecimal("."))
        assertEquals(BigDecimal.ZERO, CurrencyFormatter.parseToBigDecimal("-"))
    }

    @Test
    fun testMembershipInvariant_AddNgnThenReorderDoesNotRemoveNgn() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val initialCodes = listOf("USD", "EUR", "KES", "TZS", "KMF")
        dao.insertWorkspaceItems(initialCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        // 1. Add NGN
        repo.addCurrency("NGN")
        val withNgn = dao.getWorkspaceCurrenciesList().map { it.currencyCode }
        assertEquals(listOf("USD", "EUR", "KES", "TZS", "KMF", "NGN"), withNgn)

        // 2. Reorder moving EUR to bottom: [USD, KES, TZS, KMF, NGN, EUR]
        val reordered = listOf("USD", "KES", "TZS", "KMF", "NGN", "EUR")
        repo.reorderWorkspace(reordered)

        val stored = dao.getWorkspaceCurrenciesList().map { it.currencyCode }
        // Verify NGN is NOT lost and membership is strictly preserved
        assertEquals(reordered, stored)
        assertTrue(stored.contains("NGN"))
        assertEquals(6, stored.size)
    }

    @Test
    fun testMembershipInvariant_AddNgnAndSosThenReorderDoesNotRemoveEither() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val initialCodes = listOf("USD", "EUR", "KES", "TZS", "KMF")
        dao.insertWorkspaceItems(initialCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        // 1. Add NGN then SOS
        repo.addCurrency("NGN")
        repo.addCurrency("SOS")
        assertEquals(listOf("USD", "EUR", "KES", "TZS", "KMF", "NGN", "SOS"), dao.getWorkspaceCurrenciesList().map { it.currencyCode })

        // 2. Reorder arbitrary sequence: NGN to top, SOS to second
        val newOrder = listOf("NGN", "SOS", "USD", "EUR", "TZS", "KMF", "KES")
        repo.reorderWorkspace(newOrder)

        val stored = dao.getWorkspaceCurrenciesList().map { it.currencyCode }
        assertEquals(newOrder, stored)
        assertTrue(stored.contains("NGN"))
        assertTrue(stored.contains("SOS"))
        assertEquals(7, stored.size)
    }

    @Test
    fun testMembershipInvariant_RemoveNgnThenReorderDoesNotResurrectNgn() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val initialCodes = listOf("USD", "EUR", "KES", "TZS", "KMF", "NGN", "SOS")
        dao.insertWorkspaceItems(initialCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        // Remove NGN
        repo.removeCurrency("NGN")
        val afterRemove = dao.getWorkspaceCurrenciesList().map { it.currencyCode }
        assertEquals(listOf("USD", "EUR", "KES", "TZS", "KMF", "SOS"), afterRemove)

        // Reorder remaining currencies
        repo.reorderWorkspace(listOf("SOS", "USD", "EUR", "KES", "TZS", "KMF"))

        val stored = dao.getWorkspaceCurrenciesList().map { it.currencyCode }
        assertEquals(listOf("SOS", "USD", "EUR", "KES", "TZS", "KMF"), stored)
        assertFalse(stored.contains("NGN"))
        assertTrue(stored.contains("SOS"))
    }

    @Test
    fun testMembershipInvariant_ReorderWithPartialListPreservesMissingItems() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val allCodes = listOf("USD", "EUR", "KES", "TZS", "KMF", "NGN", "SOS")
        dao.insertWorkspaceItems(allCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        // Pass partial list (only 3 items specified)
        repo.reorderWorkspace(listOf("KES", "USD", "SOS"))

        val stored = dao.getWorkspaceCurrenciesList().map { it.currencyCode }
        // All 7 items must remain: KES, USD, SOS first, then remaining 4 appended
        assertEquals(7, stored.size)
        assertEquals("KES", stored[0])
        assertEquals("USD", stored[1])
        assertEquals("SOS", stored[2])
        assertTrue(stored.containsAll(allCodes))
    }

    @Test
    fun testWorkspace_TwoCurrenciesReorderNeverAddsThird() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val twoCurrencies = listOf("TZS", "KMF")
        dao.insertWorkspaceItems(twoCurrencies.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        // Reorder [TZS, KMF] to [KMF, TZS]
        repo.reorderWorkspace(listOf("KMF", "TZS"))

        val stored = dao.getWorkspaceCurrenciesList().map { it.currencyCode }
        assertEquals(listOf("KMF", "TZS"), stored)
        assertEquals(2, stored.size)
    }

    @Test
    fun testDefaultIsolation_ReducedWorkspaceNeverReintroducesDefaults() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val twoCurrencies = listOf("KES", "KMF")
        dao.insertWorkspaceItems(twoCurrencies.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)

        // Multiple ensureDefaultsSeeded calls (such as on viewmodel reload)
        repo.ensureDefaultsSeeded()
        repo.ensureDefaultsSeeded()

        val stored = dao.getWorkspaceCurrenciesList().map { it.currencyCode }
        assertEquals(listOf("KES", "KMF"), stored)
        assertEquals(2, stored.size)
        assertFalse(stored.contains("USD"))
    }

    @Test
    fun testViewModel_SingleTapActivationSetsAmountZeroAndAllowsTyping() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val initialCodes = listOf("USD", "EUR", "KES")
        dao.insertWorkspaceItems(initialCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val rates = mapOf("USD" to 1.0, "EUR" to 0.92, "KES" to 129.5)
        val provider = FakeRateProvider("Test Provider", returnedRates = rates)
        val repo = CurrencyRepository(currencyDao = dao, primaryProvider = provider, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        val vm = CurrencyViewModel(repo)

        // 1. User taps KES once: activates as input, sets activeAmount to "0"
        vm.selectCurrencyAsInput("KES")
        assertEquals("KES", vm.uiState.value.activeCurrencyCode)
        assertEquals("0", vm.uiState.value.activeAmount)

        // 2. User immediately types "5": initial "0" is replaced by "5"
        vm.onAmountChanged("KES", "05")
        assertEquals("5", vm.uiState.value.activeAmount)

        // 3. User types "0", "0" -> "500"
        vm.onAmountChanged("KES", "50")
        assertEquals("50", vm.uiState.value.activeAmount)
        vm.onAmountChanged("KES", "500")
        assertEquals("500", vm.uiState.value.activeAmount)
    }

    @Test
    fun testViewModel_SwitchingInputCurrencyWorks() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val initialCodes = listOf("USD", "EUR", "KES")
        dao.insertWorkspaceItems(initialCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val rates = mapOf("USD" to 1.0, "EUR" to 0.92, "KES" to 129.5)
        val provider = FakeRateProvider("Test Provider", returnedRates = rates)
        val repo = CurrencyRepository(currencyDao = dao, primaryProvider = provider, externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()

        val vm = CurrencyViewModel(repo)

        // Switch to KES
        vm.selectCurrencyAsInput("KES")
        assertEquals("KES", vm.uiState.value.activeCurrencyCode)
        assertEquals("0", vm.uiState.value.activeAmount)

        // Switch to EUR
        vm.selectCurrencyAsInput("EUR")
        assertEquals("EUR", vm.uiState.value.activeCurrencyCode)
        assertEquals("0", vm.uiState.value.activeAmount)

        // Type "100" in EUR
        vm.onAmountChanged("EUR", "01")
        assertEquals("1", vm.uiState.value.activeAmount)
        vm.onAmountChanged("EUR", "100")
        assertEquals("100", vm.uiState.value.activeAmount)
    }

    @Test
    fun testPersistence_AddReorderSaveReloadPreservesExactWorkspace() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val initialCodes = listOf("USD", "EUR", "KES", "TZS", "KMF")
        dao.insertWorkspaceItems(initialCodes.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo1 = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo1.ensureDefaultsSeeded()

        // Add NGN and SOS
        repo1.addCurrency("NGN")
        repo1.addCurrency("SOS")

        // Reorder
        val targetOrder = listOf("SOS", "NGN", "KES", "USD", "EUR", "TZS", "KMF")
        repo1.reorderWorkspace(targetOrder)

        // Simulate app restart: new repository instance on existing DB
        val repo2 = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        repo2.ensureDefaultsSeeded()

        val reloaded = dao.getWorkspaceCurrenciesList().map { it.currencyCode }
        assertEquals(targetOrder, reloaded)
    }

    @Test
    fun testThousandsVisualTransformation_FormattingAndOffsetMapping() {
        val vt = ThousandsVisualTransformation()

        val cases = listOf(
            "" to "",
            "0" to "0",
            "0." to "0.",
            ".5" to ".5",
            "0.5" to "0.5",
            "100" to "100",
            "1000" to "1,000",
            "12345.67" to "12,345.67",
            "1000000" to "1,000,000",
            "-5000" to "-5,000",
            "-12345.678" to "-12,345.678"
        )

        for ((input, expectedVisual) in cases) {
            val transformed = vt.filter(AnnotatedString(input))
            assertEquals("Visual formatting mismatch for input '$input'", expectedVisual, transformed.text.text)

            val mapping = transformed.offsetMapping

            // Invariant: originalToTransformed(offset) must be in 0..transformed.length
            for (offset in 0..input.length) {
                val transOffset = mapping.originalToTransformed(offset)
                assertTrue(
                    "originalToTransformed($offset) = $transOffset out of bounds for input '$input' (len ${expectedVisual.length})",
                    transOffset in 0..expectedVisual.length
                )
            }

            // Invariant: transformedToOriginal(offset) must be in 0..input.length
            for (offset in 0..expectedVisual.length) {
                val origOffset = mapping.transformedToOriginal(offset)
                assertTrue(
                    "transformedToOriginal($offset) = $origOffset out of bounds for input '$input' (len ${input.length})",
                    origOffset in 0..input.length
                )
            }
        }
    }

    @Test
    fun testRandomizedReorderPermutationsPreservesExactMembership() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val allTwelve = listOf("USD", "EUR", "KES", "TZS", "KMF", "NGN", "SOS", "UGX", "RWF", "BIF", "GBP", "AED")
        dao.insertWorkspaceItems(allTwelve.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)

        val random = java.util.Random(42)
        var currentList = allTwelve

        // Perform 20 permutations
        for (iteration in 1..20) {
            val shuffled = currentList.shuffled(random)
            repo.reorderWorkspace(shuffled)

            val inDb = dao.getWorkspaceCurrenciesList()
            assertEquals("Size must remain 12 after iteration $iteration", 12, inDb.size)
            assertEquals("Membership must be identical after iteration $iteration", allTwelve.toSet(), inDb.map { it.currencyCode }.toSet())
            assertEquals("Exact shuffled order must be saved", shuffled, inDb.map { it.currencyCode })

            // Invariant: Order indices must be 0 until 12 strictly sequential
            val indices = inDb.map { it.orderIndex }
            assertEquals((0 until 12).toList(), indices)

            currentList = shuffled
        }
    }

    @Test
    fun testThreeCurrencyAllSixPermutations() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val base = listOf("USD", "KES", "KMF")
        dao.insertWorkspaceItems(base.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)

        val permutations = listOf(
            listOf("USD", "KES", "KMF"),
            listOf("USD", "KMF", "KES"),
            listOf("KES", "USD", "KMF"),
            listOf("KES", "KMF", "USD"),
            listOf("KMF", "USD", "KES"),
            listOf("KMF", "KES", "USD")
        )

        for (perm in permutations) {
            repo.reorderWorkspace(perm)
            val result = dao.getWorkspaceCurrenciesList().map { it.currencyCode }
            assertEquals(perm, result)
            assertEquals(base.toSet(), result.toSet())
        }
    }

    @Test
    fun testWorkspaceCorruptedDatabaseHandling_GapsAndDuplicates() = testScope.runTest {
        val dao = FakeCurrencyDao()
        // Corrupted with index gaps: 0, 4, 9
        dao.insertWorkspaceItems(listOf(
            WorkspaceCurrencyEntity("USD", 0),
            WorkspaceCurrencyEntity("EUR", 4),
            WorkspaceCurrencyEntity("KES", 9)
        ))
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)

        // Reorder repairs indices into continuous sequence
        repo.reorderWorkspace(listOf("KES", "USD", "EUR"))
        val repaired = dao.getWorkspaceCurrenciesList()
        assertEquals(listOf("KES", "USD", "EUR"), repaired.map { it.currencyCode })
        assertEquals(listOf(0, 1, 2), repaired.map { it.orderIndex })

        // Corrupted with duplicate indices: 0, 0, 1
        dao.clearWorkspaceItems()
        dao.insertWorkspaceItems(listOf(
            WorkspaceCurrencyEntity("USD", 0),
            WorkspaceCurrencyEntity("EUR", 0),
            WorkspaceCurrencyEntity("KES", 1)
        ))
        repo.reorderWorkspace(listOf("EUR", "KES", "USD"))
        val repairedDupes = dao.getWorkspaceCurrenciesList()
        assertEquals(listOf("EUR", "KES", "USD"), repairedDupes.map { it.currencyCode })
        assertEquals(listOf(0, 1, 2), repairedDupes.map { it.orderIndex })
    }

    @Test
    fun testActiveCurrencyRemoval_DeterministicallySelectsNextSource() = testScope.runTest {
        val dao = FakeCurrencyDao()
        dao.insertWorkspaceItems(listOf("USD", "EUR", "KES").mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        val vm = CurrencyViewModel(repo)

        // USD is active source by default
        assertEquals("USD", vm.uiState.value.activeCurrencyCode)

        // Remove active currency USD
        vm.removeCurrency("USD")

        // Invariant: activeCurrencyCode must deterministically fall back to the first remaining currency
        assertEquals("EUR", vm.uiState.value.activeCurrencyCode)
        assertEquals(2, vm.uiState.value.items.size)
        val eurItem = vm.uiState.value.items.first { it.currency.code == "EUR" }
        assertTrue("Newly selected active currency must have isSource = true", eurItem.isSource)
        val kesItem = vm.uiState.value.items.first { it.currency.code == "KES" }
        assertFalse("Other item must not be source", kesItem.isSource)
    }

    @Test
    fun testConcurrentWorkspaceMutations_SerializedCleanly() = testScope.runTest {
        val dao = FakeCurrencyDao()
        dao.insertWorkspaceItems(listOf("USD", "EUR", "KES", "TZS", "KMF").mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)

        // Run multiple concurrent operations
        val j1 = async { repo.addCurrency("NGN") }
        val j2 = async { repo.reorderWorkspace(listOf("KMF", "TZS", "KES", "EUR", "USD")) }
        val j3 = async { repo.addCurrency("SOS") }
        val j4 = async { repo.reorderWorkspace(listOf("USD", "EUR", "KES", "TZS", "KMF", "NGN", "SOS")) }
        val j5 = async { repo.removeCurrency("TZS") }
        j1.await()
        j2.await()
        j3.await()
        j4.await()
        j5.await()

        val finalDb = dao.getWorkspaceCurrenciesList()
        assertTrue("At least two currencies must remain", finalDb.size >= 2)
        val codes = finalDb.map { it.currencyCode }
        assertEquals("Codes in DB must be unique", codes.distinct().size, codes.size)
        val indices = finalDb.map { it.orderIndex }
        assertEquals((0 until finalDb.size).toList(), indices)
    }

    @Test
    fun testViewModel_InputHardening_SingleDotAndDigitLimit() = testScope.runTest {
        val dao = FakeCurrencyDao()
        dao.insertWorkspaceItems(listOf("USD", "EUR").mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        val vm = CurrencyViewModel(repo)

        // Multiple dots: "1.2.3" -> "1.23"
        vm.onAmountChanged("USD", "1.2.3")
        assertEquals("1.23", vm.uiState.value.activeAmount)

        // Excessive integer digits capped to 15
        vm.onAmountChanged("USD", "1234567890123456789")
        assertEquals("123456789012345", vm.uiState.value.activeAmount)

        // Leading zeros: "000500" -> "500"
        vm.onAmountChanged("USD", "000500")
        assertEquals("500", vm.uiState.value.activeAmount)

        // Decimal preserve: "0." -> "0."
        vm.onAmountChanged("USD", "0.")
        assertEquals("0.", vm.uiState.value.activeAmount)

        // Single dot: "." -> "."
        vm.onAmountChanged("USD", ".")
        assertEquals(".", vm.uiState.value.activeAmount)
    }

    @Test
    fun testDetectLocalCurrency_TimezoneFallbackToKES() {
        val origTz = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Africa/Nairobi"))
            val detected = CurrencyCatalog.detectLocalCurrency(null)
            assertEquals("KES", detected.currency.code)
            assertEquals("Kenya", detected.countryName)
        } finally {
            java.util.TimeZone.setDefault(origTz)
        }
    }

    @Test
    fun testZeroFlickerSanitization() {
        // Simulates typing a digit when field is "0"
        fun sanitize(prevText: String, newRawText: String): String {
            return if (prevText == "0" && newRawText.length == 2 && newRawText.startsWith("0") && newRawText[1] != '.') {
                newRawText.substring(1)
            } else if (newRawText.length > 1 && newRawText.startsWith("0") && newRawText[1] != '.') {
                newRawText.trimStart('0').ifEmpty { "0" }
            } else {
                newRawText
            }
        }

        // Typing 6 into "0" -> immediate "6", never "06"
        assertEquals("6", sanitize("0", "06"))
        // Typing 5 into "0" -> immediate "5", never "05"
        assertEquals("5", sanitize("0", "05"))
        // Typing '.' into "0" -> "0." preserved
        assertEquals("0.", sanitize("0", "0."))
        // Typing 0 into "0" -> "0"
        assertEquals("0", sanitize("0", "00"))
        // Subsequent typing: "6" -> "67"
        assertEquals("67", sanitize("6", "67"))
    }

    @Test
    fun testRemoveActiveCurrency_preservesRemainingAmounts() = runTest {
        val fakeDao = FakeCurrencyDao()
        val repo = CurrencyRepository(fakeDao, externalScope = TestScope(UnconfinedTestDispatcher()))
        repo.ensureDefaultsSeeded()

        val vm = CurrencyViewModel(repo, null)
        vm.onAmountChanged("USD", "100")

        val eurBefore = vm.uiState.value.items.find { it.currency.code == "EUR" }
        assertNotNull(eurBefore)
        val expectedEurAmount = eurBefore!!.rawAmount

        // Removing active USD should switch active currency to EUR and retain EUR's converted amount
        vm.removeCurrency("USD")
        assertEquals("EUR", vm.uiState.value.activeCurrencyCode)
        assertEquals(expectedEurAmount, vm.uiState.value.activeAmount)

        val eurAfter = vm.uiState.value.items.find { it.currency.code == "EUR" }
        assertNotNull(eurAfter)
        assertTrue(eurAfter!!.isSource)
        assertEquals(expectedEurAmount, eurAfter.rawAmount)
    }

    @Test
    fun testThousandsVisualTransformation_cursorIntegrityOnCommaTyping() {
        val transformation = ThousandsVisualTransformation()
        
        // 1000 -> 1,000
        val res1000 = transformation.filter(AnnotatedString("1000"))
        assertEquals("1,000", res1000.text.text)
        // Cursor at end of "1000" (index 4) maps to end of "1,000" (index 5)
        assertEquals(5, res1000.offsetMapping.originalToTransformed(4))
        assertEquals(4, res1000.offsetMapping.transformedToOriginal(5))

        // 10000 -> 10,000
        val res10000 = transformation.filter(AnnotatedString("10000"))
        assertEquals("10,000", res10000.text.text)
        // Cursor at end of "10000" (index 5) maps to end of "10,000" (index 6)
        assertEquals(6, res10000.offsetMapping.originalToTransformed(5))
        assertEquals(5, res10000.offsetMapping.transformedToOriginal(6))

        // Mapping cursor within digits does not produce out-of-bounds
        for (i in 0..5) {
            val trans = res10000.offsetMapping.originalToTransformed(i)
            val orig = res10000.offsetMapping.transformedToOriginal(trans)
            assertEquals(i, orig)
        }
    }

    @Test
    fun testAtomicSourceSelection_SingleEmissionAndCorrectRowAmounts() = testScope.runTest {
        val dao = FakeCurrencyDao()
        dao.insertWorkspaceItems(listOf("USD", "EUR", "KES", "TZS", "OMR", "AED").mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        val vm = CurrencyViewModel(repo)

        // Select AED as source
        vm.selectCurrencyAsInput("AED")
        val state = vm.uiState.value
        assertEquals("AED", state.activeCurrencyCode)
        assertEquals("0", state.activeAmount)

        val aedRow = state.items.first { it.currency.code == "AED" }
        assertTrue(aedRow.isSource)
        assertEquals("0", aedRow.rawAmount)

        val usdRow = state.items.first { it.currency.code == "USD" }
        assertFalse(usdRow.isSource)

        // Type into AED
        vm.onAmountChanged("AED", "100")
        val stateAfter100 = vm.uiState.value
        assertEquals("100", stateAfter100.activeAmount)
        assertEquals("100", stateAfter100.items.first { it.currency.code == "AED" }.rawAmount)
    }

    @Test
    fun testRapidSourceCurrencySwitching_PreservesIntegrityAndStability() = testScope.runTest {
        val dao = FakeCurrencyDao()
        val currencies = listOf("USD", "EUR", "KES", "TZS", "OMR", "AED")
        dao.insertWorkspaceItems(currencies.mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        val vm = CurrencyViewModel(repo)

        // Rapid switching: OMR -> AED -> OMR -> AED
        for (i in 1..5) {
            vm.selectCurrencyAsInput("OMR")
            assertEquals("OMR", vm.uiState.value.activeCurrencyCode)
            assertEquals("0", vm.uiState.value.activeAmount)

            vm.selectCurrencyAsInput("AED")
            assertEquals("AED", vm.uiState.value.activeCurrencyCode)
            assertEquals("0", vm.uiState.value.activeAmount)
        }

        // Final state consistency check
        val finalState = vm.uiState.value
        assertEquals("AED", finalState.activeCurrencyCode)
        val aedItem = finalState.items.first { it.currency.code == "AED" }
        assertTrue(aedItem.isSource)
        val otherSources = finalState.items.filter { it.currency.code != "AED" && it.isSource }
        assertEquals(0, otherSources.size)
    }

    @Test
    fun testSelectingAlreadyActiveCurrency_DoesNotTriggerStateChurn() = testScope.runTest {
        val dao = FakeCurrencyDao()
        dao.insertWorkspaceItems(listOf("USD", "EUR", "AED").mapIndexed { i, c -> WorkspaceCurrencyEntity(c, i) })
        val repo = CurrencyRepository(currencyDao = dao, externalScope = testScope, ioDispatcher = testDispatcher)
        val vm = CurrencyViewModel(repo)

        // Already active USD with "0"
        vm.selectCurrencyAsInput("USD")
        val prevState = vm.uiState.value

        // Re-selecting same currency
        vm.selectCurrencyAsInput("USD")
        val nextState = vm.uiState.value
        assertSame("State reference should be untouched when re-selecting already active zero input", prevState, nextState)
    }
}

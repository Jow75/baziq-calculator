package com.omnicalc.platform

import com.omnicalc.platform.core.currency.ApiCurrencyRateProvider
import com.omnicalc.platform.core.currency.CurrencyCatalog
import com.omnicalc.platform.core.currency.CurrencyRateProvider
import com.omnicalc.platform.core.currency.CountryLocator
import com.omnicalc.platform.core.currency.CountryLookup
import com.omnicalc.platform.core.preferences.LocalCurrencyPreferences
import com.omnicalc.platform.core.preferences.LocalCurrencySettings
import com.omnicalc.platform.core.currency.SecondaryCurrencyRateProvider
import com.omnicalc.platform.feature.currency.CurrencyViewModel
import com.omnicalc.platform.core.currency.CurrencyRateSet
import com.omnicalc.platform.core.currency.CurrencyRepository
import com.omnicalc.platform.core.currency.FreshnessStatus
import com.omnicalc.platform.core.currency.StaticFallbackRateProvider
import com.omnicalc.platform.core.database.daos.CurrencyDao
import com.omnicalc.platform.core.database.entities.CachedRateEntity
import com.omnicalc.platform.core.database.entities.CustomRateOverrideEntity
import com.omnicalc.platform.core.database.entities.WorkspaceCurrencyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/**
 * Regression tests for currency defects confirmed during the Baziq Hue takeover review.
 */
class CurrencyRegressionTest {

    private class InMemoryCurrencyDao : CurrencyDao() {
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

    private class CountingRateProvider(
        override val providerName: String,
        var shouldFail: Boolean = false,
        var rates: Map<String, Double> = mapOf("USD" to 1.0, "EUR" to 0.9, "KES" to 130.0),
        var timestampMs: Long = System.currentTimeMillis()
    ) : CurrencyRateProvider {
        var calls = 0
        override suspend fun fetchRates(baseCurrency: String): Result<CurrencyRateSet> {
            calls++
            if (shouldFail) return Result.failure(Exception("offline"))
            return Result.success(CurrencyRateSet(baseCurrency, rates, timestampMs, providerName, FreshnessStatus.CURRENT))
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

    // Defect: the embedded reference dataset was labelled "October 2026" while its timestamp
    // (and its values) date from 1 October 2024, so two-year-old rates were presented as current.
    @Test
    fun staticDatasetLabelMatchesEmbeddedTimestamp() {
        val timestampDate = Instant.ofEpochMilli(StaticFallbackRateProvider.METADATA_TIMESTAMP_MS)
            .atZone(ZoneOffset.UTC).toLocalDate().toString()
        assertEquals(timestampDate, StaticFallbackRateProvider.METADATA.lastUpdateDate)
        assertTrue(StaticFallbackRateProvider.METADATA.version.startsWith(timestampDate.take(4)))
    }

    // Defect: converting to a currency with no known rate silently assumed a 1:1 rate.
    @Test
    fun conversionWithoutAnyKnownRateIsRejected() = testScope.runTest {
        val repo = CurrencyRepository(currencyDao = InMemoryCurrencyDao(), externalScope = testScope, ioDispatcher = testDispatcher)
        try {
            repo.convert(BigDecimal("100"), "USD", "XYZ", mapOf("USD" to 1.0))
            fail("Expected conversion to an unknown currency to be rejected instead of assuming parity")
        } catch (_: ArithmeticException) {
            // expected
        }
    }

    @Test
    fun everyCatalogCurrencyHasAReferenceRate() {
        val missing = CurrencyCatalog.ALL_CURRENCIES.map { it.code }
            .filterNot { StaticFallbackRateProvider.STATIC_RATES.containsKey(it) }
        assertTrue("Catalog currencies without a reference rate: $missing", missing.isEmpty())
    }

    // --- Rate service responses (shapes captured from the live endpoints on 2026-10-24) ---

    @Test
    fun parsesOpenExchangeRatesResponse() {
        val body = """{"result":"success","provider":"https://www.exchangerate-api.com","time_last_update_unix":1792800151,""" +
            """"time_next_update_unix":1792887421,"base_code":"USD","rates":{"USD":1,"EUR":0.892784,"KES":129.84786,"TZS":2643.054368,"KMF":439.220851}}"""
        val set = ApiCurrencyRateProvider().parse(body, "USD")
        assertEquals("USD", set.baseCurrency)
        assertEquals(129.84786, set.rates.getValue("KES"), 1e-9)
        assertEquals(1.0, set.rates.getValue("USD"), 0.0)
        assertEquals(1792800151000L, set.timestampMs)
    }

    @Test
    fun rejectsOpenExchangeRatesErrorResult() {
        val body = """{"result":"error","error-type":"unsupported-code"}"""
        try {
            ApiCurrencyRateProvider().parse(body, "USD")
            fail("An error result must not be accepted as a rate set")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun parsesFrankfurterResponseAndAddsBaseRate() {
        val body = """{"amount":1.0,"base":"USD","date":"2026-10-23","rates":{"EUR":0.89342,"GBP":0.74804,"JPY":156.8}}"""
        val set = SecondaryCurrencyRateProvider().parse(body, "USD")
        assertEquals(0.89342, set.rates.getValue("EUR"), 1e-9)
        assertEquals(1.0, set.rates.getValue("USD"), 0.0)
    }

    // --- Freshness policy ---

    @Test
    fun beforeTheCacheLoadsRatesAreLabelledAsBuiltInReference() {
        // A scope that never runs the seeding job, so the initial state stays observable
        val idleScope = TestScope()
        val repo = CurrencyRepository(currencyDao = InMemoryCurrencyDao(), externalScope = idleScope)
        val initial = repo.rateState.value
        assertTrue(initial.isReferenceOnly)
        assertEquals(FreshnessStatus.OFFLINE, initial.status)
        assertEquals(StaticFallbackRateProvider.METADATA_TIMESTAMP_MS, initial.timestampMs)
        assertEquals(null, initial.fetchedAtMs)
    }

    @Test
    fun autoRefreshIsDueOncePerSessionThenThrottled() = testScope.runTest {
        var now = 1_800_000_000_000L
        val primary = CountingRateProvider("Primary")
        val repo = CurrencyRepository(
            currencyDao = InMemoryCurrencyDao(),
            primaryProvider = primary,
            secondaryProvider = CountingRateProvider("Secondary"),
            externalScope = testScope,
            ioDispatcher = testDispatcher,
            clock = { now }
        )

        // Nothing downloaded yet in this session: due
        assertTrue(repo.claimAutoRefresh())
        assertTrue(repo.refreshRates(force = true).getOrThrow().fetchedAtMs == now)

        // A fresh download is reused, however often the screen is opened
        now += 60 * 60_000L
        assertFalse(repo.claimAutoRefresh())

        // Older than the refresh interval: due again
        now += CurrencyRepository.AUTO_REFRESH_INTERVAL_MS
        assertTrue(repo.claimAutoRefresh())
    }

    @Test
    fun failedAutoRefreshIsNotRetriedOnEveryVisit() = testScope.runTest {
        var now = 1_800_000_000_000L
        val repo = CurrencyRepository(
            currencyDao = InMemoryCurrencyDao(),
            primaryProvider = CountingRateProvider("Primary", shouldFail = true),
            secondaryProvider = CountingRateProvider("Secondary", shouldFail = true),
            externalScope = testScope,
            ioDispatcher = testDispatcher,
            clock = { now }
        )

        assertTrue(repo.claimAutoRefresh())
        repo.refreshRates(force = true)

        now += 5 * 60_000L
        assertFalse(repo.claimAutoRefresh())

        now += CurrencyRepository.AUTO_RETRY_INTERVAL_MS
        assertTrue(repo.claimAutoRefresh())
    }

    @Test
    fun refreshFallingBackToSavedRatesIsNotReportedAsDownloaded() = testScope.runTest {
        val repo = CurrencyRepository(
            currencyDao = InMemoryCurrencyDao(),
            primaryProvider = CountingRateProvider("Primary", shouldFail = true),
            secondaryProvider = CountingRateProvider("Secondary", shouldFail = true),
            externalScope = testScope,
            ioDispatcher = testDispatcher
        )
        val result = repo.refreshRates(force = true)
        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrThrow().fetchedAtMs)
    }

    // Defect: the view model announced "Rates updated successfully" whenever the repository
    // returned a result, including when both providers failed and saved rates were used.
    @Test
    fun manualRefreshWhileOfflineSaysSavedRatesAreUsed() = testScope.runTest {
        val repo = CurrencyRepository(
            currencyDao = InMemoryCurrencyDao(),
            primaryProvider = CountingRateProvider("Primary", shouldFail = true),
            secondaryProvider = CountingRateProvider("Secondary", shouldFail = true),
            externalScope = testScope,
            ioDispatcher = testDispatcher
        )
        val vm = CurrencyViewModel(repo)
        vm.refreshRates()
        assertEquals("Offline: Using saved rates", vm.uiState.value.statusMessage)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun manualRefreshOnlineSaysUpdated() = testScope.runTest {
        val repo = CurrencyRepository(
            currencyDao = InMemoryCurrencyDao(),
            primaryProvider = CountingRateProvider("Primary"),
            externalScope = testScope,
            ioDispatcher = testDispatcher
        )
        val vm = CurrencyViewModel(repo)
        vm.refreshRates()
        assertEquals("Rates updated successfully", vm.uiState.value.statusMessage)
        assertEquals(130.0, vm.uiState.value.rateSet.rates.getValue("KES"), 0.0)
    }

    @Test
    fun openingTheConverterRefreshesOnceAndTypingNeverDoes() = testScope.runTest {
        val primary = CountingRateProvider("Primary")
        val repo = CurrencyRepository(
            currencyDao = InMemoryCurrencyDao(),
            primaryProvider = primary,
            externalScope = testScope,
            ioDispatcher = testDispatcher
        )
        val vm = CurrencyViewModel(repo)

        vm.refreshRatesIfDue()
        assertEquals(1, primary.calls)
        assertEquals(null, vm.uiState.value.statusMessage) // automatic refreshes stay quiet

        vm.selectCurrencyAsInput("USD")
        "1234.56".forEachIndexed { i, _ -> vm.onAmountChanged("USD", "1234.56".take(i + 1)) }
        vm.refreshRatesIfDue() // screen reopened right away
        assertEquals(1, primary.calls)
    }

    // --- Input and display ---

    // Defect: the source field could show text the converter did not use (e.g. "1.2.3" while
    // converting 1.23); onAmountChanged now returns the normalized amount for the field.
    @Test
    fun amountChangeReturnsTheNormalizedAmountActuallyConverted() = testScope.runTest {
        val repo = CurrencyRepository(currencyDao = InMemoryCurrencyDao(), externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()
        val vm = CurrencyViewModel(repo)

        assertEquals("1.23", vm.onAmountChanged("USD", "1.2.3"))
        assertEquals("1.23", vm.uiState.value.activeAmount)
        assertEquals("123456789012345", vm.onAmountChanged("USD", "1234567890123456"))
        assertEquals("5", vm.onAmountChanged("USD", "05"))
    }

    @Test
    fun onlyNonTypingChangesBumpTheInputRevision() = testScope.runTest {
        val repo = CurrencyRepository(currencyDao = InMemoryCurrencyDao(), externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()
        val vm = CurrencyViewModel(repo)
        val start = vm.uiState.value.inputRevision

        vm.onAmountChanged("USD", "25")
        assertEquals(start, vm.uiState.value.inputRevision)

        vm.selectCurrencyAsInput("EUR")
        assertEquals(start + 1, vm.uiState.value.inputRevision)
    }

    @Test
    fun rateLabelUsesTheSameRatesAsTheAmount() = testScope.runTest {
        val repo = CurrencyRepository(
            currencyDao = InMemoryCurrencyDao(),
            primaryProvider = CountingRateProvider("Primary", rates = mapOf("USD" to 1.0, "EUR" to 0.9, "KES" to 130.0, "TZS" to 2600.0, "KMF" to 450.0)),
            externalScope = testScope,
            ioDispatcher = testDispatcher
        )
        repo.ensureDefaultsSeeded()
        repo.refreshRates(force = true)
        val vm = CurrencyViewModel(repo)
        vm.onAmountChanged("EUR", "10")

        val kes = vm.uiState.value.items.first { it.currency.code == "KES" }
        // 10 EUR × (130 / 0.9) = 1,444.44 KES
        assertEquals("1444.44", kes.rawAmount)
        assertEquals("1,444.44", kes.amount)
        // The label keeps the decimals of 130 / 0.9 = 144.44... (1.1.0 showed a rounded "144")
        assertEquals("1 EUR = 144.44 KES", kes.rateLabel)
        assertEquals(CurrencyViewModel.SOURCE_RATE_LABEL, vm.uiState.value.items.first { it.currency.code == "EUR" }.rateLabel)
    }

    @Test
    fun rowWithoutAnyRateShowsUnavailableInsteadOfZero() = testScope.runTest {
        val repo = CurrencyRepository(currencyDao = InMemoryCurrencyDao(), externalScope = testScope, ioDispatcher = testDispatcher)
        repo.ensureDefaultsSeeded()
        val vm = CurrencyViewModel(repo)
        vm.onAmountChanged("USD", "100")
        vm.addCurrency("XYZ")

        val row = vm.uiState.value.items.first { it.currency.code == "XYZ" }
        assertEquals(CurrencyViewModel.UNAVAILABLE_AMOUNT, row.rawAmount)
        assertEquals("Rate unavailable", row.rateLabel)
    }

    // --- Release 1.1.1: rate labels keep their decimals (fresh and cached alike) ---

    private val kenyaRates = mapOf("USD" to 1.0, "EUR" to 0.892784, "KES" to 129.84, "TZS" to 2643.054368, "KMF" to 439.22, "UGX" to 3550.0)

    private fun labelsOf(vm: CurrencyViewModel) = vm.uiState.value.items.associate { it.currency.code to it.rateLabel }

    @Test
    fun freshlyDownloadedRateLabelShows129Point84Not130() = testScope.runTest {
        val repo = CurrencyRepository(
            currencyDao = InMemoryCurrencyDao(),
            primaryProvider = CountingRateProvider("Primary", rates = kenyaRates),
            externalScope = testScope,
            ioDispatcher = testDispatcher
        )
        repo.ensureDefaultsSeeded()
        repo.refreshRates(force = true)
        val vm = CurrencyViewModel(repo)
        vm.onAmountChanged("USD", "1")

        val labels = labelsOf(vm)
        assertEquals("1 USD = 129.84 KES", labels["KES"])
        assertEquals("1 USD = 2,643.054368 TZS", labels["TZS"])
        assertEquals("1 USD = 439.22 KMF", labels["KMF"])
        assertEquals("1 USD = 0.892784 EUR", labels["EUR"])
    }

    @Test
    fun cachedRateLabelFollowsTheSameRules() = testScope.runTest {
        val dao = InMemoryCurrencyDao()
        val hourAgo = System.currentTimeMillis() - 3600_000L
        dao.insertRates(kenyaRates.map { (code, r) -> CachedRateEntity(code, r, "USD", "Saved Rates", hourAgo, "CURRENT") })
        val repo = CurrencyRepository(
            currencyDao = dao,
            primaryProvider = CountingRateProvider("Primary", shouldFail = true),
            secondaryProvider = CountingRateProvider("Secondary", shouldFail = true),
            externalScope = testScope,
            ioDispatcher = testDispatcher
        )
        repo.ensureDefaultsSeeded()
        repo.refreshRates(force = true) // offline: cached rates are used
        val vm = CurrencyViewModel(repo)
        vm.onAmountChanged("USD", "1")

        assertEquals("1 USD = 129.84 KES", labelsOf(vm)["KES"])
    }

    @Test
    fun inverseAndCrossRateLabelsAreNoiseFree() = testScope.runTest {
        val repo = CurrencyRepository(
            currencyDao = InMemoryCurrencyDao(),
            primaryProvider = CountingRateProvider("Primary", rates = kenyaRates),
            externalScope = testScope,
            ioDispatcher = testDispatcher
        )
        repo.ensureDefaultsSeeded()
        repo.refreshRates(force = true)
        val vm = CurrencyViewModel(repo)
        vm.onAmountChanged("KES", "1")

        val labels = labelsOf(vm)
        assertEquals("1 KES = 0.0077018 USD", labels["USD"])   // 1 / 129.84
        assertEquals("1 KES = 3.3828 KMF", labels["KMF"])      // 439.22 / 129.84 = 3.38278...
        assertEquals("1 KES = 20.35624128 TZS", labels["TZS"]) // 2643.054368 / 129.84 = 20.356241282...
        // Converted amounts still use the target currency's minor units
        assertEquals("0.01", vm.uiState.value.items.first { it.currency.code == "USD" }.rawAmount)
    }

    // --- Release 1.1.1: location-driven base currency ---

    private class FakeLocator(var lookup: CountryLookup, var permission: Boolean = true) : CountryLocator {
        var calls = 0
        override fun hasPermission() = permission
        override suspend fun currentCountry(): CountryLookup {
            calls++
            return if (permission) lookup else CountryLookup.PermissionDenied
        }
    }

    private class FakeLocalPrefs(initial: LocalCurrencySettings = LocalCurrencySettings()) : LocalCurrencyPreferences {
        val state = MutableStateFlow(initial)
        override val localCurrencySettingsFlow: Flow<LocalCurrencySettings> = state
        override suspend fun setAutoLocalCurrency(enabled: Boolean) { state.value = state.value.copy(autoLocalCurrency = enabled) }
        override suspend fun setLocationPromptAnswered() { state.value = state.value.copy(locationPromptAnswered = true) }
        override suspend fun setLastAppliedCountry(countryCode: String) { state.value = state.value.copy(lastAppliedCountry = countryCode) }
        override suspend fun setBaseCurrency(currencyCode: String) { state.value = state.value.copy(baseCurrency = currencyCode) }
    }

    private suspend fun seededRepo(): CurrencyRepository {
        val repo = CurrencyRepository(
            currencyDao = InMemoryCurrencyDao(),
            primaryProvider = CountingRateProvider("Primary", rates = kenyaRates),
            externalScope = testScope,
            ioDispatcher = testDispatcher
        )
        repo.ensureDefaultsSeeded()
        repo.refreshRates(force = true)
        return repo
    }

    @Test
    fun travelChangesTheBaseCurrencyWhenAutomaticModeIsOn() = testScope.runTest {
        var now = 0L
        val locator = FakeLocator(CountryLookup.Found("KM", "Comoros"))
        val prefs = FakeLocalPrefs(LocalCurrencySettings(autoLocalCurrency = true, locationPromptAnswered = true))
        val vm = CurrencyViewModel(seededRepo(), countryLocator = locator, localCurrencyPreferences = prefs, nowMs = { now })

        vm.onConverterVisible()
        assertEquals("KMF", vm.uiState.value.activeCurrencyCode)
        assertEquals("KM", prefs.state.value.lastAppliedCountry)

        // Travel to the United States (simulated lookup), converter reopened later
        now += CurrencyViewModel.BACKGROUND_CHECK_INTERVAL_MS
        locator.lookup = CountryLookup.Found("US", "United States")
        vm.onConverterVisible()
        assertEquals("USD", vm.uiState.value.activeCurrencyCode)
        assertTrue(vm.uiState.value.statusMessage!!.contains("United States"))
        assertEquals("📍 United States · USD", vm.uiState.value.locationLabel)

        // Then Uganda: UGX was not in the list and is added, other currencies stay
        now += CurrencyViewModel.BACKGROUND_CHECK_INTERVAL_MS
        locator.lookup = CountryLookup.Found("UG", "Uganda")
        vm.onConverterVisible()
        assertEquals("UGX", vm.uiState.value.activeCurrencyCode)
        val codes = vm.uiState.value.items.map { it.currency.code }
        assertTrue(codes.containsAll(listOf("USD", "EUR", "KES", "TZS", "KMF", "UGX")))
    }

    @Test
    fun manualChoiceIsRespectedWhileStayingInTheSameCountry() = testScope.runTest {
        var now = 0L
        val locator = FakeLocator(CountryLookup.Found("KE", "Kenya"))
        val prefs = FakeLocalPrefs(LocalCurrencySettings(autoLocalCurrency = true, locationPromptAnswered = true))
        val vm = CurrencyViewModel(seededRepo(), countryLocator = locator, localCurrencyPreferences = prefs, nowMs = { now })

        vm.onConverterVisible()
        assertEquals("KES", vm.uiState.value.activeCurrencyCode)

        vm.selectCurrencyAsInput("EUR") // user picks a base by hand
        now += CurrencyViewModel.BACKGROUND_CHECK_INTERVAL_MS
        vm.onConverterVisible()
        assertEquals("EUR", vm.uiState.value.activeCurrencyCode)
    }

    @Test
    fun automaticModeOffNeverLooksUpLocationInTheBackground() = testScope.runTest {
        val locator = FakeLocator(CountryLookup.Found("UG", "Uganda"))
        val prefs = FakeLocalPrefs(LocalCurrencySettings(autoLocalCurrency = false, locationPromptAnswered = true))
        val vm = CurrencyViewModel(seededRepo(), countryLocator = locator, localCurrencyPreferences = prefs)

        vm.onConverterVisible()
        assertEquals(0, locator.calls)
        assertEquals("USD", vm.uiState.value.activeCurrencyCode)
    }

    @Test
    fun locationIsNotCheckedOnEveryVisit() = testScope.runTest {
        var now = 0L
        val locator = FakeLocator(CountryLookup.Found("KE", "Kenya"))
        val prefs = FakeLocalPrefs(LocalCurrencySettings(autoLocalCurrency = true, locationPromptAnswered = true))
        val vm = CurrencyViewModel(seededRepo(), countryLocator = locator, localCurrencyPreferences = prefs, nowMs = { now })

        vm.onConverterVisible()
        now += 60_000
        vm.onConverterVisible()
        vm.onAmountChanged("KES", "500")
        assertEquals(1, locator.calls)
    }

    @Test
    fun deniedPermissionKeepsTheConverterUsableAndTheBaseUnchanged() = testScope.runTest {
        val locator = FakeLocator(CountryLookup.Found("UG", "Uganda"), permission = false)
        val prefs = FakeLocalPrefs()
        val vm = CurrencyViewModel(seededRepo(), countryLocator = locator, localCurrencyPreferences = prefs)
        assertTrue(vm.uiState.value.showLocationPrompt)

        vm.onLocationPermissionResult(granted = false, enableAuto = true)
        assertFalse(vm.uiState.value.showLocationPrompt)
        assertTrue(prefs.state.value.locationPromptAnswered) // not asked again
        assertFalse(prefs.state.value.autoLocalCurrency)
        assertEquals("USD", vm.uiState.value.activeCurrencyCode)
        assertTrue(vm.uiState.value.statusMessage!!.contains("manually"))
        assertFalse(vm.uiState.value.isLocating)

        vm.onConverterVisible() // no background prompting or lookups
        assertEquals(0, locator.calls)

        vm.onAmountChanged("USD", "10")
        assertEquals("1298.40", vm.uiState.value.items.first { it.currency.code == "KES" }.rawAmount)
    }

    @Test
    fun permissionGrantedLaterWorksWithoutReinstall() = testScope.runTest {
        val locator = FakeLocator(CountryLookup.Found("RW", "Rwanda"), permission = false)
        val prefs = FakeLocalPrefs()
        val vm = CurrencyViewModel(seededRepo(), countryLocator = locator, localCurrencyPreferences = prefs)
        vm.onLocationPermissionResult(granted = false, enableAuto = true)

        locator.permission = true // allowed later (e.g. in Android settings)
        vm.onLocationPermissionResult(granted = true, enableAuto = true)
        assertEquals("RWF", vm.uiState.value.activeCurrencyCode)
        assertTrue(prefs.state.value.autoLocalCurrency)
    }

    @Test
    fun unavailableLocationKeepsTheBaseAndExplains() = testScope.runTest {
        val locator = FakeLocator(CountryLookup.NoLocation)
        val vm = CurrencyViewModel(seededRepo(), countryLocator = locator, localCurrencyPreferences = FakeLocalPrefs())
        vm.selectCurrencyAsInput("TZS")

        vm.detectLocalCurrencyFromLocation()
        assertEquals("TZS", vm.uiState.value.activeCurrencyCode)
        assertTrue(vm.uiState.value.statusMessage!!.contains("not changed"))
        assertFalse(vm.uiState.value.isLocating)
        assertNull(vm.uiState.value.locationLabel)
    }

    @Test
    fun locatorCrashIsReportedAsUnavailableLocation() = testScope.runTest {
        val locator = object : CountryLocator {
            override fun hasPermission() = true
            override suspend fun currentCountry(): CountryLookup = throw IllegalStateException("geocoder died")
        }
        val vm = CurrencyViewModel(seededRepo(), countryLocator = locator, localCurrencyPreferences = FakeLocalPrefs())
        vm.detectLocalCurrencyFromLocation()
        assertEquals("USD", vm.uiState.value.activeCurrencyCode)
        assertFalse(vm.uiState.value.isLocating)
    }

    @Test
    fun applyingTheLocalCurrencyCarriesTheAmountOver() = testScope.runTest {
        val locator = FakeLocator(CountryLookup.Found("KE", "Kenya"))
        val vm = CurrencyViewModel(seededRepo(), countryLocator = locator, localCurrencyPreferences = FakeLocalPrefs())
        vm.onAmountChanged("USD", "10")

        vm.detectLocalCurrencyFromLocation()
        assertEquals("KES", vm.uiState.value.activeCurrencyCode)
        assertEquals("1298.4", vm.uiState.value.activeAmount)
        assertEquals("10.00", vm.uiState.value.items.first { it.currency.code == "USD" }.rawAmount)
    }
}

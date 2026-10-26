package com.omnicalc.platform

import com.omnicalc.platform.core.currency.CountryLocator
import com.omnicalc.platform.core.currency.CountryLookup
import com.omnicalc.platform.core.currency.CurrencyRepository
import com.omnicalc.platform.core.preferences.LocalCurrencyPreferences
import com.omnicalc.platform.core.preferences.LocalCurrencySettings
import com.omnicalc.platform.feature.currency.CurrencyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Defect: the base currency returned to USD whenever the app restarted. A "restart" here is a
 * new view model over the same saved preferences and currency list.
 */
class BaseCurrencyPersistenceTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val dao = FakeCurrencyDao()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeLocator(var lookup: CountryLookup, var permission: Boolean = true) : CountryLocator {
        override fun hasPermission() = permission
        override suspend fun currentCountry(): CountryLookup = if (permission) lookup else CountryLookup.PermissionDenied
    }

    private suspend fun repo(): CurrencyRepository {
        val repo = CurrencyRepository(
            currencyDao = dao,
            primaryProvider = FixedRateProvider(),
            secondaryProvider = FixedRateProvider(),
            externalScope = testScope,
            ioDispatcher = testDispatcher
        )
        repo.ensureDefaultsSeeded() // USD, EUR, KES, TZS, KMF
        repo.refreshRates(force = true)
        return repo
    }

    private suspend fun start(prefs: LocalCurrencyPreferences, locator: CountryLocator? = null, now: () -> Long = { 0L }) =
        CurrencyViewModel(repo(), countryLocator = locator, localCurrencyPreferences = prefs, nowMs = now)

    @Test
    fun chosenBaseCurrencySurvivesARestart() = testScope.runTest {
        val prefs = FakeLocalCurrencyPrefs()
        val first = start(prefs)
        assertEquals("USD", first.uiState.value.activeCurrencyCode)
        first.selectCurrencyAsInput("KMF")
        assertEquals("KMF", prefs.state.value.baseCurrency)

        val restarted = start(prefs)
        assertEquals("KMF", restarted.uiState.value.activeCurrencyCode)
        assertEquals("1", restarted.uiState.value.activeAmount)
        val kmfRow = restarted.uiState.value.items.first { it.currency.code == "KMF" }
        assertEquals(true, kmfRow.isSource)
        // 1 KMF = 130 / 440 KES
        assertEquals("0.30", restarted.uiState.value.items.first { it.currency.code == "KES" }.rawAmount)
    }

    @Test
    fun typingIntoAnotherCurrencyAlsoSavesIt() = testScope.runTest {
        val prefs = FakeLocalCurrencyPrefs()
        val vm = start(prefs)
        vm.onAmountChanged("EUR", "5")
        assertEquals("EUR", prefs.state.value.baseCurrency)
        assertEquals("EUR", start(prefs).uiState.value.activeCurrencyCode)
    }

    @Test
    fun removedBaseCurrencyIsNotRestored() = testScope.runTest {
        val prefs = FakeLocalCurrencyPrefs(LocalCurrencySettings(baseCurrency = "AED"))
        assertEquals("USD", start(prefs).uiState.value.activeCurrencyCode)
    }

    @Test
    fun removingTheBaseCurrencySavesTheNewBase() = testScope.runTest {
        val prefs = FakeLocalCurrencyPrefs()
        val vm = start(prefs)
        vm.selectCurrencyAsInput("KES")
        vm.removeCurrency("KES")
        assertEquals("USD", vm.uiState.value.activeCurrencyCode)
        assertEquals("USD", prefs.state.value.baseCurrency)
    }

    // A choice made before the saved preferences finish loading must not be overridden by them
    @Test
    fun lateRestoreNeverOverridesAChoiceAlreadyMade() = testScope.runTest {
        val gate = MutableStateFlow<LocalCurrencySettings?>(null)
        val prefs = object : LocalCurrencyPreferences {
            override val localCurrencySettingsFlow: Flow<LocalCurrencySettings> = gate.filterNotNull()
            override suspend fun setAutoLocalCurrency(enabled: Boolean) {}
            override suspend fun setLocationPromptAnswered() {}
            override suspend fun setLastAppliedCountry(countryCode: String) {}
            override suspend fun setBaseCurrency(currencyCode: String) {}
        }
        val vm = start(prefs)
        vm.selectCurrencyAsInput("TZS")
        gate.value = LocalCurrencySettings(baseCurrency = "KMF")
        assertEquals("TZS", vm.uiState.value.activeCurrencyCode)
    }

    // Automatic mode, same country as before the restart: the manual choice is kept
    @Test
    fun automaticModeKeepsAManualChoiceWithinTheSameCountry() = testScope.runTest {
        val prefs = FakeLocalCurrencyPrefs(LocalCurrencySettings(autoLocalCurrency = true, locationPromptAnswered = true))
        val locator = FakeLocator(CountryLookup.Found("KM", "Comoros"))
        val first = start(prefs, locator)
        first.onConverterVisible()
        assertEquals("KMF", first.uiState.value.activeCurrencyCode)
        first.selectCurrencyAsInput("EUR") // manual choice in Comoros

        val restarted = start(prefs, locator)
        assertEquals("EUR", restarted.uiState.value.activeCurrencyCode)
        restarted.onConverterVisible()
        assertEquals("EUR", restarted.uiState.value.activeCurrencyCode)
    }

    // Automatic mode, new country after the restart: its currency becomes the base, and is saved
    @Test
    fun automaticModeStillFollowsACountryChange() = testScope.runTest {
        val prefs = FakeLocalCurrencyPrefs(
            LocalCurrencySettings(autoLocalCurrency = true, locationPromptAnswered = true, lastAppliedCountry = "KM", baseCurrency = "KMF")
        )
        val vm = start(prefs, FakeLocator(CountryLookup.Found("TZ", "Tanzania")))
        assertEquals("KMF", vm.uiState.value.activeCurrencyCode)
        vm.onConverterVisible()
        assertEquals("TZS", vm.uiState.value.activeCurrencyCode)
        assertEquals("TZS", prefs.state.value.baseCurrency)
        assertEquals("TZ", prefs.state.value.lastAppliedCountry)
    }

    @Test
    fun deniedPermissionKeepsTheRestoredBaseAndManualSelection() = testScope.runTest {
        val prefs = FakeLocalCurrencyPrefs(LocalCurrencySettings(autoLocalCurrency = true, locationPromptAnswered = true, baseCurrency = "KES"))
        val vm = start(prefs, FakeLocator(CountryLookup.Found("TZ", "Tanzania"), permission = false))
        vm.onConverterVisible()
        assertEquals("KES", vm.uiState.value.activeCurrencyCode)
        vm.selectCurrencyAsInput("EUR")
        assertEquals("7", vm.onAmountChanged("EUR", "7"))
        assertEquals("EUR", prefs.state.value.baseCurrency)
    }

    @Test
    fun undeterminedCountryKeepsTheRestoredBase() = testScope.runTest {
        val prefs = FakeLocalCurrencyPrefs(LocalCurrencySettings(autoLocalCurrency = true, locationPromptAnswered = true, baseCurrency = "KES"))
        val vm = start(prefs, FakeLocator(CountryLookup.CountryUnresolved))
        vm.onConverterVisible()
        assertEquals("KES", vm.uiState.value.activeCurrencyCode)
        vm.detectLocalCurrencyFromLocation()
        assertEquals("KES", vm.uiState.value.activeCurrencyCode)
        // The converter keeps working from the restored base: 130 KES = 1 USD
        vm.onAmountChanged("KES", "130")
        assertEquals("1.00", vm.uiState.value.items.first { it.currency.code == "USD" }.rawAmount)
    }
}

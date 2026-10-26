package com.omnicalc.platform.feature.currency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnicalc.platform.core.currency.*
import com.omnicalc.platform.core.preferences.LocalCurrencyPreferences
import com.omnicalc.platform.core.preferences.LocalCurrencySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

data class CurrencyRowItem(
    val currency: CurrencyInfo,
    val amount: String,
    val rawAmount: String = "",
    val isSource: Boolean = false,
    val hasCustomOverride: Boolean = false,
    val customRate: Double? = null,
    val conversionResolution: ConversionResolution? = null,
    /** Relation to the source currency, e.g. "1 USD = 129.85 KES". */
    val rateLabel: String = ""
)

data class CurrencyUiState(
    val items: List<CurrencyRowItem> = emptyList(),
    val rateSet: CurrencyRateSet = CurrencyRateSet("USD", emptyMap(), System.currentTimeMillis(), "Offline", FreshnessStatus.OFFLINE),
    val activeCurrencyCode: String = "USD",
    val activeAmount: String = "1",
    val isRefreshing: Boolean = false,
    val isReorderMode: Boolean = false,
    val searchQuery: String = "",
    val availableCurrencies: List<CurrencyInfo> = CurrencyCatalog.ALL_ALPHABETICAL,
    val detectedLocation: CurrencyCatalog.DetectedLocationInfo? = null,
    val statusMessage: String? = null,
    val customOverrides: Map<String, Double> = emptyMap(),
    /** Incremented whenever the source amount is replaced by something other than typing. */
    val inputRevision: Int = 0,
    /** Detected country and its currency, e.g. "📍 Uganda · UGX"; null until location has been used. */
    val locationLabel: String? = null,
    val isLocating: Boolean = false,
    /** One-time card explaining why location is requested. */
    val showLocationPrompt: Boolean = false,
    val autoLocalCurrency: Boolean = false
)

class CurrencyViewModel(
    private val currencyRepository: CurrencyRepository,
    private val countryLocator: CountryLocator? = null,
    private val localCurrencyPreferences: LocalCurrencyPreferences? = null,
    private val nowMs: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(CurrencyUiState(rateSet = currencyRepository.rateState.value))
    val uiState: StateFlow<CurrencyUiState> = _uiState.asStateFlow()

    private var localSettings: LocalCurrencySettings? = null
    private var locationJob: kotlinx.coroutines.Job? = null
    private var lastBackgroundCheckMs: Long? = null

    /** The base currency was chosen in this session, so a saved one is no longer restored. */
    private var baseChosenThisSession = false
    private var baseRestoreStarted = false

    init {
        // The local currency comes from the device location only (see checkLocalCurrency):
        // SIM, time zone and language settings can name a different country than the one
        // the user is in.
        if (localCurrencyPreferences != null) {
            viewModelScope.launch {
                localCurrencyPreferences.localCurrencySettingsFlow.collect { settings ->
                    localSettings = settings
                    if (!baseRestoreStarted) {
                        baseRestoreStarted = true
                        settings.baseCurrency?.let { saved -> launch { restoreBaseCurrency(saved) } }
                    }
                    if (countryLocator == null) return@collect
                    _uiState.update {
                        it.copy(
                            autoLocalCurrency = settings.autoLocalCurrency,
                            showLocationPrompt = !settings.locationPromptAnswered
                        )
                    }
                }
            }
        }

        // Combine workspace currencies, latest rates, and custom overrides
        viewModelScope.launch {
            combine(
                currencyRepository.workspaceCurrencies,
                currencyRepository.rateState,
                currencyRepository.customOverridesFlow
            ) { currencies, rateSet, overrides ->
                Triple(currencies, rateSet, overrides)
            }.collect { (currencies, rateSet, overrides) ->
                val activeCode = _uiState.value.activeCurrencyCode
                val activeAmtStr = _uiState.value.activeAmount
                val activeAmt = CurrencyFormatter.parseToBigDecimal(activeAmtStr)

                val rows = currencies.map { info ->
                    buildRow(info, activeCode, activeAmtStr, activeAmt, rateSet, overrides)
                }

                _uiState.update {
                    it.copy(
                        items = rows,
                        rateSet = rateSet,
                        customOverrides = overrides
                    )
                }
            }
        }
    }

    fun selectCurrencyAsInput(code: String) {
        if (_uiState.value.activeCurrencyCode.equals(code, ignoreCase = true) && _uiState.value.activeAmount == "0") {
            return
        }
        rememberBaseCurrency(code)
        _uiState.update { current ->
            val updated = computeUpdatedRows(code, "0", current.items, current.rateSet, current.customOverrides)
            current.copy(
                activeCurrencyCode = code,
                activeAmount = "0",
                items = updated,
                inputRevision = current.inputRevision + 1
            )
        }
    }

    /**
     * Applies typed input to the source currency and recomputes every row.
     *
     * @return the normalized amount actually used for conversion, so the input field can
     *         show exactly that value (e.g. "1.2.3" becomes "1.23").
     */
    fun onAmountChanged(code: String, newAmount: String): String {
        val clean = CurrencyFormatter.sanitizeToNumeric(newAmount)
        val filtered = clean.filter { it.isDigit() || it == '.' }

        // Allow at most one decimal point
        val dotIndex = filtered.indexOf('.')
        val singleDot = if (dotIndex != -1) {
            filtered.substring(0, dotIndex + 1) + filtered.substring(dotIndex + 1).replace(".", "")
        } else {
            filtered
        }

        // Cap input length: max 15 integer digits + optional decimal with fractional digits
        val parts = singleDot.split(".", limit = 2)
        val intPart = parts[0].take(15)
        val decPart = if (parts.size > 1) parts[1].take(6) else null
        val bounded = if (decPart != null) "$intPart.$decPart" else if (singleDot.endsWith(".")) "$intPart." else intPart

        // Strip unnecessary leading zeros (e.g., "05" → "5", "0100" → "100")
        // Preserve "0", "0.", "0.5" patterns
        val finalAmount = if (bounded.length > 1 && bounded[0] == '0' && bounded.getOrNull(1) != '.') {
            val trimmed = bounded.trimStart('0')
            when {
                trimmed.isEmpty() -> "0"
                trimmed.startsWith('.') -> "0$trimmed"
                else -> trimmed
            }
        } else {
            bounded
        }

        if (!_uiState.value.activeCurrencyCode.equals(code, ignoreCase = true)) rememberBaseCurrency(code)
        _uiState.update { current ->
            val updated = computeUpdatedRows(code, finalAmount, current.items, current.rateSet, current.customOverrides)
            current.copy(
                activeCurrencyCode = code,
                activeAmount = finalAmount,
                items = updated
            )
        }
        return finalAmount
    }

    fun reorderCurrencies(newOrder: List<String>) {
        val currentItems = _uiState.value.items
        val currentMap = currentItems.associateBy { it.currency.code.uppercase() }
        val orderedUpper = newOrder.map { it.uppercase() }.distinct()
        val reordered = orderedUpper.mapNotNull { currentMap[it] }

        // Membership invariant: keep any item that was omitted from newOrder
        val missing = currentItems.filter { item -> reordered.none { it.currency.code.equals(item.currency.code, ignoreCase = true) } }
        val completeList = reordered + missing

        if (completeList.isNotEmpty()) {
            _uiState.update { it.copy(items = completeList) }
        }
        val completeCodes = completeList.map { it.currency.code }
        viewModelScope.launch {
            currencyRepository.reorderWorkspace(completeCodes)
        }
    }

    private fun computeUpdatedRows(
        sourceCode: String,
        amountStr: String,
        currentItems: List<CurrencyRowItem>,
        rateSet: CurrencyRateSet,
        overrides: Map<String, Double>
    ): List<CurrencyRowItem> {
        val activeAmt = CurrencyFormatter.parseToBigDecimal(amountStr)
        return currentItems.map { item ->
            buildRow(item.currency, sourceCode, amountStr, activeAmt, rateSet, overrides)
        }
    }

    /** Builds one row: the typed amount for the source currency, a conversion for the others. */
    private fun buildRow(
        info: CurrencyInfo,
        sourceCode: String,
        sourceAmountStr: String,
        sourceAmount: BigDecimal,
        rateSet: CurrencyRateSet,
        overrides: Map<String, Double>
    ): CurrencyRowItem {
        val base = CurrencyRowItem(
            currency = info,
            amount = "",
            hasCustomOverride = overrides.containsKey(info.code),
            customRate = overrides[info.code]
        )
        if (info.code.equals(sourceCode, ignoreCase = true)) {
            return base.copy(
                amount = CurrencyFormatter.formatString(sourceAmountStr),
                rawAmount = sourceAmountStr,
                isSource = true,
                rateLabel = SOURCE_RATE_LABEL
            )
        }
        return try {
            val (converted, resolution) = currencyRepository.convertWithResolution(
                amount = sourceAmount,
                fromCode = sourceCode,
                toCode = info.code,
                rateSet = rateSet,
                overrides = overrides
            )
            val unitRate = currencyRepository.formatUnitRate(sourceCode, info.code, rateSet.rates, overrides)
            val raw = converted.setScale(info.decimalPlaces, RoundingMode.HALF_EVEN).toPlainString()
            base.copy(
                amount = CurrencyFormatter.formatString(raw),
                rawAmount = raw,
                conversionResolution = resolution,
                rateLabel = "1 ${sourceCode.uppercase()} = $unitRate ${info.code}"
            )
        } catch (_: Exception) {
            // Never show an invented figure (such as 0.00) when no usable rate exists
            base.copy(amount = UNAVAILABLE_AMOUNT, rawAmount = UNAVAILABLE_AMOUNT, rateLabel = "Rate unavailable")
        }
    }

    private var refreshJob: kotlinx.coroutines.Job? = null

    /** Manual refresh from the toolbar button. */
    fun refreshRates() {
        if (refreshJob?.isActive == true) return // Guard against concurrent overlapping refresh calls
        refreshJob = viewModelScope.launch { runRefresh(userInitiated = true) }
    }

    /**
     * Called when the converter becomes visible. Refreshes in the background only when the
     * repository's freshness policy says a download is due; saved rates stay usable meanwhile.
     */
    fun refreshRatesIfDue() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            if (currencyRepository.claimAutoRefresh()) {
                runRefresh(userInitiated = false)
            }
        }
    }

    private suspend fun runRefresh(userInitiated: Boolean) {
        _uiState.update { it.copy(isRefreshing = true, statusMessage = if (userInitiated) null else it.statusMessage) }
        val result = currencyRepository.refreshRates(force = true)
        // A successful result can still be the cache or the built-in table when both
        // providers fail; only a download in this session counts as an update.
        val downloaded = result.getOrNull()?.fetchedAtMs != null
        _uiState.update {
            it.copy(
                isRefreshing = false,
                rateSet = result.getOrNull() ?: it.rateSet,
                statusMessage = when {
                    !userInitiated -> it.statusMessage
                    downloaded -> "Rates updated successfully"
                    else -> "Offline: Using saved rates"
                }
            )
        }
    }

    fun addCurrency(code: String) {
        val upper = code.uppercase()
        val currentItems = _uiState.value.items
        if (currentItems.any { it.currency.code.equals(upper, ignoreCase = true) }) return

        val state = _uiState.value
        val newItem = buildRow(
            info = CurrencyCatalog.find(upper),
            sourceCode = state.activeCurrencyCode,
            sourceAmountStr = state.activeAmount,
            sourceAmount = CurrencyFormatter.parseToBigDecimal(state.activeAmount),
            rateSet = state.rateSet,
            overrides = state.customOverrides
        )

        // Optimistic UI update ensures workspace state immediately reflects the addition
        _uiState.update { it.copy(items = currentItems + newItem) }

        viewModelScope.launch {
            currencyRepository.addCurrency(upper)
        }
    }

    fun removeCurrency(code: String) {
        if (_uiState.value.items.size <= 2) return
        val upper = code.uppercase()
        val currentItems = _uiState.value.items
        val remaining = currentItems.filter { !it.currency.code.equals(upper, ignoreCase = true) }
        if (remaining.size < 2) return

        val wasActive = _uiState.value.activeCurrencyCode.equals(upper, ignoreCase = true)
        val newActiveCode = if (wasActive) remaining.first().currency.code else _uiState.value.activeCurrencyCode

        // Forensic Fix: When active currency is removed, use the converted amount of the new active currency,
        // preventing corruption of all remaining currency amounts on screen.
        val newAmount = if (wasActive) {
            val firstRemaining = remaining.first()
            if (CurrencyFormatter.parseToBigDecimal(firstRemaining.rawAmount).signum() > 0) {
                firstRemaining.rawAmount
            } else {
                "1"
            }
        } else {
            _uiState.value.activeAmount
        }

        if (wasActive) rememberBaseCurrency(newActiveCode)

        // Optimistic atomic UI update
        val finalItems = if (wasActive) {
            computeUpdatedRows(newActiveCode, newAmount, remaining, _uiState.value.rateSet, _uiState.value.customOverrides)
        } else {
            remaining
        }

        _uiState.update {
            it.copy(
                items = finalItems,
                activeCurrencyCode = newActiveCode,
                activeAmount = newAmount,
                inputRevision = if (wasActive) it.inputRevision + 1 else it.inputRevision
            )
        }
        viewModelScope.launch {
            currencyRepository.removeCurrency(upper)
        }
    }

    fun searchCurrencies(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                availableCurrencies = CurrencyCatalog.search(query)
            )
        }
    }

    fun setReorderMode(enabled: Boolean) {
        _uiState.update { it.copy(isReorderMode = enabled) }
    }

    fun toggleReorderMode() {
        _uiState.update { it.copy(isReorderMode = !it.isReorderMode) }
    }

    fun moveCurrency(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.items.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val moved = current.removeAt(fromIndex)
            current.add(toIndex, moved)
            _uiState.update { it.copy(items = current) }
            val completeCodes = current.map { it.currency.code }
            viewModelScope.launch {
                currencyRepository.reorderWorkspace(completeCodes)
            }
        }
    }

    /**
     * Called when the converter becomes visible. With automatic mode on and permission granted,
     * checks the country at most once per [BACKGROUND_CHECK_INTERVAL_MS]; never on keystrokes.
     */
    fun onConverterVisible() {
        val settings = localSettings ?: return
        val locator = countryLocator ?: return
        if (!settings.autoLocalCurrency || !locator.hasPermission()) return
        val last = lastBackgroundCheckMs
        if (last != null && nowMs() - last < BACKGROUND_CHECK_INTERVAL_MS) return
        checkLocalCurrency(userRequested = false)
    }

    /** "Local Currency" button; the screen has already asked for permission when needed. */
    fun detectLocalCurrencyFromLocation() {
        checkLocalCurrency(userRequested = true)
    }

    /**
     * Result of the system permission dialog.
     *
     * @param enableAuto true when the request came from the one-time card or the automatic
     *        setting, i.e. the user asked for the base currency to follow their country.
     */
    fun onLocationPermissionResult(granted: Boolean, enableAuto: Boolean) {
        _uiState.update { it.copy(showLocationPrompt = false) }
        viewModelScope.launch {
            localCurrencyPreferences?.setLocationPromptAnswered()
            if (granted && enableAuto) localCurrencyPreferences?.setAutoLocalCurrency(true)
        }
        if (granted) {
            if (enableAuto) localSettings = (localSettings ?: LocalCurrencySettings()).copy(autoLocalCurrency = true)
            checkLocalCurrency(userRequested = true)
        } else {
            _uiState.update { it.copy(statusMessage = LocalCurrencyPolicy.failureMessage(CountryLookup.PermissionDenied)) }
        }
    }

    /** "Not now" on the one-time card: manual selection only; the card does not come back. */
    fun dismissLocationPrompt() {
        _uiState.update { it.copy(showLocationPrompt = false) }
        viewModelScope.launch { localCurrencyPreferences?.setLocationPromptAnswered() }
    }

    fun setAutoLocalCurrency(enabled: Boolean) {
        localSettings = (localSettings ?: LocalCurrencySettings()).copy(autoLocalCurrency = enabled)
        _uiState.update { it.copy(autoLocalCurrency = enabled) }
        viewModelScope.launch { localCurrencyPreferences?.setAutoLocalCurrency(enabled) }
    }

    private fun checkLocalCurrency(userRequested: Boolean) {
        val locator = countryLocator ?: return
        if (locationJob?.isActive == true) return
        lastBackgroundCheckMs = nowMs()
        locationJob = viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true) }
            val lookup = try {
                locator.currentCountry()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                CountryLookup.NoLocation
            }
            val settings = localSettings ?: LocalCurrencySettings()
            val decision = LocalCurrencyPolicy.decide(lookup, settings.autoLocalCurrency, userRequested, settings.lastAppliedCountry)
            decision.rememberCountry?.let { country ->
                localSettings = settings.copy(lastAppliedCountry = country)
                localCurrencyPreferences?.setLastAppliedCountry(country)
            }
            decision.applyCurrency?.let { applyBaseCurrency(it) }
            val detected = (lookup as? CountryLookup.Found)?.let { found ->
                CountryCurrency.currencyFor(found.countryCode)?.let {
                    CurrencyCatalog.DetectedLocationInfo(CurrencyCatalog.find(it), found.countryName, found.countryCode)
                }
            }
            _uiState.update {
                it.copy(
                    isLocating = false,
                    locationLabel = decision.detectedLabel ?: it.locationLabel,
                    detectedLocation = detected ?: it.detectedLocation,
                    statusMessage = decision.message ?: it.statusMessage
                )
            }
        }
    }

    /**
     * Makes [code] the base (source) currency, adding it to the list when missing. The amount
     * carries over as its value in the new currency, so the figures on screen stay the same.
     */
    private fun applyBaseCurrency(code: String) {
        val upper = code.uppercase()
        rememberBaseCurrency(upper)
        _uiState.update { current ->
            if (current.activeCurrencyCode.equals(upper, ignoreCase = true)) return@update current
            val carried = current.items.firstOrNull { it.currency.code.equals(upper, ignoreCase = true) }
                ?.rawAmount
                ?.takeIf { it != UNAVAILABLE_AMOUNT && CurrencyFormatter.parseToBigDecimal(it).signum() > 0 }
                ?.let { CurrencyFormatter.parseToBigDecimal(it).stripTrailingZeros().toPlainString() }
                ?: "1"
            current.copy(
                activeCurrencyCode = upper,
                activeAmount = carried,
                items = computeUpdatedRows(upper, carried, current.items, current.rateSet, current.customOverrides),
                inputRevision = current.inputRevision + 1
            )
        }
        addCurrency(upper)
    }

    /** Saves the base currency so the next app start opens with it instead of USD. */
    private fun rememberBaseCurrency(code: String) {
        baseChosenThisSession = true
        val upper = code.uppercase()
        viewModelScope.launch { localCurrencyPreferences?.setBaseCurrency(upper) }
    }

    /**
     * Restores the base currency saved by an earlier session, with an amount of 1. It is skipped
     * when the currency is no longer in the list, or when a base was already chosen in this
     * session (by the user, or by the automatic local currency).
     */
    private suspend fun restoreBaseCurrency(saved: String) {
        val upper = saved.uppercase()
        val workspace = currencyRepository.workspaceCurrencies.first()
        if (baseChosenThisSession || workspace.none { it.code.equals(upper, ignoreCase = true) }) return
        _uiState.update { current ->
            if (current.activeCurrencyCode.equals(upper, ignoreCase = true)) return@update current
            current.copy(
                activeCurrencyCode = upper,
                activeAmount = "1",
                items = computeUpdatedRows(upper, "1", current.items, current.rateSet, current.customOverrides),
                inputRevision = current.inputRevision + 1
            )
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun setCustomRateOverride(code: String, customRate: Double) {
        viewModelScope.launch {
            currencyRepository.setCustomOverride(code, customRate)
        }
    }

    fun removeCustomRateOverride(code: String) {
        viewModelScope.launch {
            currencyRepository.removeCustomOverride(code)
        }
    }

    companion object {
        const val UNAVAILABLE_AMOUNT = "—"
        const val SOURCE_RATE_LABEL = "Base Input"

        /** Minimum gap between automatic location checks when the converter is reopened. */
        const val BACKGROUND_CHECK_INTERVAL_MS = 10 * 60_000L
    }
}

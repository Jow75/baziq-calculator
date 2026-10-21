package com.omnicalc.platform.feature.currency

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnicalc.platform.core.currency.*
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
    val conversionResolution: ConversionResolution? = null
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
    val customOverrides: Map<String, Double> = emptyMap()
)

class CurrencyViewModel(
    private val currencyRepository: CurrencyRepository,
    private val context: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CurrencyUiState())
    val uiState: StateFlow<CurrencyUiState> = _uiState.asStateFlow()

    init {
        // Pre-detect local currency on background IO to prevent any main thread hitch
        viewModelScope.launch(Dispatchers.IO) {
            val detected = CurrencyCatalog.detectLocalCurrency(context)
            _uiState.update { it.copy(detectedLocation = detected) }
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
                    val isSource = info.code.equals(activeCode, ignoreCase = true)
                    val (displayAmt, rawAmt, resolution) = if (isSource) {
                        val disp = CurrencyFormatter.formatString(activeAmtStr)
                        Triple(disp, activeAmtStr, null)
                    } else {
                        try {
                            val (converted, res) = currencyRepository.convertWithResolution(
                                amount = activeAmt,
                                fromCode = activeCode,
                                toCode = info.code,
                                rateSet = rateSet,
                                overrides = overrides
                            )
                            val raw = converted.setScale(info.decimalPlaces, RoundingMode.HALF_EVEN).toPlainString()
                            val disp = CurrencyFormatter.formatString(raw)
                            Triple(disp, raw, res)
                        } catch (e: Exception) {
                            Triple("0.00", "0.00", null)
                        }
                    }

                    CurrencyRowItem(
                        currency = info,
                        amount = displayAmt,
                        rawAmount = rawAmt,
                        isSource = isSource,
                        hasCustomOverride = overrides.containsKey(info.code),
                        customRate = overrides[info.code],
                        conversionResolution = resolution
                    )
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
        _uiState.update { current ->
            val updated = computeUpdatedRows(code, "0", current.items, current.rateSet, current.customOverrides)
            current.copy(
                activeCurrencyCode = code,
                activeAmount = "0",
                items = updated
            )
        }
    }

    fun onAmountChanged(code: String, newAmount: String) {
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

        _uiState.update { current ->
            val updated = computeUpdatedRows(code, finalAmount, current.items, current.rateSet, current.customOverrides)
            current.copy(
                activeCurrencyCode = code,
                activeAmount = finalAmount,
                items = updated
            )
        }
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
            val isSource = item.currency.code.equals(sourceCode, ignoreCase = true)
            val (displayAmt, rawAmt, resolution) = if (isSource) {
                val disp = CurrencyFormatter.formatString(amountStr)
                Triple(disp, amountStr, null)
            } else {
                try {
                    val (converted, res) = currencyRepository.convertWithResolution(
                        amount = activeAmt,
                        fromCode = sourceCode,
                        toCode = item.currency.code,
                        rateSet = rateSet,
                        overrides = overrides
                    )
                    val raw = converted.setScale(item.currency.decimalPlaces, RoundingMode.HALF_EVEN).toPlainString()
                    val disp = CurrencyFormatter.formatString(raw)
                    Triple(disp, raw, res)
                } catch (_: Exception) {
                    Triple("0.00", "0.00", null)
                }
            }
            item.copy(amount = displayAmt, rawAmount = rawAmt, isSource = isSource, conversionResolution = resolution)
        }
    }


    private var refreshJob: kotlinx.coroutines.Job? = null

    fun refreshRates() {
        if (refreshJob?.isActive == true) return // Guard against concurrent overlapping refresh calls

        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, statusMessage = null) }
            val res = currencyRepository.refreshRates(force = true)
            if (res.isSuccess) {
                val set = res.getOrThrow()
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        rateSet = set,
                        statusMessage = "Rates updated successfully"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        statusMessage = "Offline: Using saved rates"
                    )
                }
            }
        }
    }

    fun addCurrency(code: String) {
        val upper = code.uppercase()
        val currentItems = _uiState.value.items
        if (currentItems.any { it.currency.code.equals(upper, ignoreCase = true) }) return

        val info = CurrencyCatalog.find(upper)
        val activeCode = _uiState.value.activeCurrencyCode
        val activeAmtStr = _uiState.value.activeAmount
        val activeAmt = CurrencyFormatter.parseToBigDecimal(activeAmtStr)
        val rateSet = _uiState.value.rateSet
        val overrides = _uiState.value.customOverrides

        val (displayAmt, rawAmt, resolution) = try {
            val (converted, res) = currencyRepository.convertWithResolution(
                amount = activeAmt,
                fromCode = activeCode,
                toCode = info.code,
                rateSet = rateSet,
                overrides = overrides
            )
            val raw = converted.setScale(info.decimalPlaces, RoundingMode.HALF_EVEN).toPlainString()
            val disp = CurrencyFormatter.formatString(raw)
            Triple(disp, raw, res)
        } catch (_: Exception) {
            Triple("0.00", "0.00", null)
        }

        val newItem = CurrencyRowItem(
            currency = info,
            amount = displayAmt,
            rawAmount = rawAmt,
            isSource = false,
            hasCustomOverride = overrides.containsKey(info.code),
            customRate = overrides[info.code],
            conversionResolution = resolution
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
            if (firstRemaining.rawAmount.isNotEmpty() && firstRemaining.rawAmount != "0") {
                firstRemaining.rawAmount
            } else {
                "1"
            }
        } else {
            _uiState.value.activeAmount
        }

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
                activeAmount = newAmount
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

    fun detectAndSelectLocalCurrency(callerContext: Context? = null): CurrencyCatalog.DetectedLocationInfo {
        val ctx = callerContext ?: context
        val loc = CurrencyCatalog.detectLocalCurrency(ctx)
        _uiState.update { it.copy(detectedLocation = loc) }
        val currentCodes = _uiState.value.items.map { it.currency.code.uppercase() }
        if (!currentCodes.contains(loc.currency.code.uppercase())) {
            addCurrency(loc.currency.code)
        }
        selectCurrencyAsInput(loc.currency.code)
        _uiState.update {
            it.copy(statusMessage = "📍 Detected: ${loc.countryName} (${loc.currency.code})")
        }
        return loc
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
}

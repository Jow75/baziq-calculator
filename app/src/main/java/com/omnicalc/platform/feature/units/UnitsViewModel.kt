package com.omnicalc.platform.feature.units

import androidx.lifecycle.ViewModel
import com.omnicalc.platform.core.units.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal

data class UnitBreakdownItem(
    val unit: UnitDefinition,
    val formattedValue: String
)

data class UnitsUiState(
    val selectedDimension: Dimension = Dimension.LENGTH,
    val inputValue: String = "1",
    val fromUnit: UnitDefinition = UnitConverterEngine.findUnit("km")!!,
    val toUnit: UnitDefinition = UnitConverterEngine.findUnit("mi")!!,
    val resultValue: String = "0.621371",
    val formulaExplanation: String = "1 km × 0.621371 ≈ 0.621371 mi",
    val allBreakdownItems: List<UnitBreakdownItem> = emptyList(),
    val errorMessage: String? = null
)

class UnitsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UnitsUiState())
    val uiState: StateFlow<UnitsUiState> = _uiState.asStateFlow()

    init {
        selectDimension(Dimension.LENGTH)
    }

    fun selectDimension(dim: Dimension) {
        val units = UnitConverterEngine.getUnitsForDimension(dim)
        val defaultFrom = units.getOrNull(0) ?: return
        val defaultTo = units.getOrNull(1) ?: defaultFrom

        _uiState.update {
            it.copy(
                selectedDimension = dim,
                fromUnit = defaultFrom,
                toUnit = defaultTo
            )
        }
        recalculate()
    }

    fun onInputValueChanged(newVal: String) {
        val sanitized = newVal.filter { it.isDigit() || it == '.' || it == '-' }
        _uiState.update { it.copy(inputValue = sanitized) }
        recalculate()
    }

    fun selectFromUnit(unit: UnitDefinition) {
        _uiState.update { it.copy(fromUnit = unit) }
        recalculate()
    }

    fun selectToUnit(unit: UnitDefinition) {
        _uiState.update { it.copy(toUnit = unit) }
        recalculate()
    }

    fun swapUnits() {
        _uiState.update {
            val oldFrom = it.fromUnit
            val oldTo = it.toUnit
            it.copy(fromUnit = oldTo, toUnit = oldFrom)
        }
        recalculate()
    }

    private fun recalculate() {
        val current = _uiState.value
        val inputStr = current.inputValue.trim()
        if (inputStr.isEmpty() || inputStr == "-") {
            _uiState.update {
                it.copy(
                    resultValue = "0",
                    formulaExplanation = "",
                    allBreakdownItems = emptyList(),
                    errorMessage = null
                )
            }
            return
        }

        try {
            val valueBd = BigDecimal(inputStr)
            val convertedBd = UnitConverterEngine.convert(valueBd, current.fromUnit, current.toUnit)
            val formatted = UnitConverterEngine.formatResult(convertedBd)

            val explanation = "${current.fromUnit.symbol} → ${current.toUnit.symbol}"

            // Generate breakdown for all units in this dimension
            val allUnits = UnitConverterEngine.getUnitsForDimension(current.selectedDimension)
            val breakdown = allUnits.map { targetUnit ->
                val res = try {
                    val c = UnitConverterEngine.convert(valueBd, current.fromUnit, targetUnit)
                    UnitConverterEngine.formatResult(c)
                } catch (_: Exception) {
                    "—"
                }
                UnitBreakdownItem(unit = targetUnit, formattedValue = res)
            }

            _uiState.update {
                it.copy(
                    resultValue = formatted,
                    formulaExplanation = explanation,
                    allBreakdownItems = breakdown,
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    resultValue = "Error",
                    errorMessage = e.message ?: "Invalid conversion input"
                )
            }
        }
    }
}

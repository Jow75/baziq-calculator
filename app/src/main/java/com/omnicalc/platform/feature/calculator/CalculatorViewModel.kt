package com.omnicalc.platform.feature.calculator

import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnicalc.platform.core.database.daos.HistoryDao
import com.omnicalc.platform.core.database.entities.CalculationHistoryEntity
import com.omnicalc.platform.core.math.AngleMode
import com.omnicalc.platform.core.math.BigDecimalMath
import com.omnicalc.platform.core.math.EvalResult
import com.omnicalc.platform.core.math.ExpressionEvaluator
import com.omnicalc.platform.core.preferences.PreferencesRepository
import com.omnicalc.platform.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class CalculatorUiState(
    val expression: String = "",
    val liveResult: String = "",
    val errorMessage: String? = null,
    val memory: BigDecimal = BigDecimal.ZERO,
    val angleMode: AngleMode = AngleMode.DEGREES,
    val isScientificExpanded: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val useGroupingSeparators: Boolean = true
)

class CalculatorViewModel(
    private val historyDaoProvider: () -> HistoryDao,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    constructor(
        historyDao: HistoryDao,
        preferencesRepository: PreferencesRepository
    ) : this({ historyDao }, preferencesRepository)

    private val historyDao: HistoryDao by lazy { historyDaoProvider() }

    private val evaluator = ExpressionEvaluator(angleMode = AngleMode.DEGREES)

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                evaluator.angleMode = prefs.angleMode
                _uiState.update {
                    it.copy(
                        angleMode = prefs.angleMode,
                        hapticsEnabled = prefs.hapticFeedbackEnabled,
                        useGroupingSeparators = prefs.useGroupingSeparators
                    )
                }
            }
        }
    }

    fun onDigit(digit: String) {
        val tStart = try { SystemClock.elapsedRealtime() } catch (_: Throwable) { 0L }
        _uiState.update { current ->
            val expr = current.expression
            val newExpr = if (digit == ".") {
                val lastToken = expr.split(' ').lastOrNull() ?: ""
                if (lastToken.contains('.')) {
                    return@update current
                }
                if (expr.isEmpty() || expr.endsWith(" ") || expr == "-") {
                    expr + "0."
                } else {
                    expr + "."
                }
            } else {
                if (expr == "0") digit else expr + digit
            }
            val preview = calculatePreview(newExpr)
            current.copy(
                expression = newExpr,
                liveResult = preview.first,
                errorMessage = preview.second
            )
        }
        try {
            Choreographer.getInstance().postFrameCallback {
                val tFrame = SystemClock.elapsedRealtime()
                Log.i("PERF_OMNICALC", "KEYPAD_METRIC: digit '$digit' rendered in ${tFrame - tStart} ms")
            }
        } catch (_: Throwable) {
            // Guard for host JVM test runner
        }
    }

    fun onOperator(op: String) {
        val tStart = try { SystemClock.elapsedRealtime() } catch (_: Throwable) { 0L }
        _uiState.update { current ->
            val expr = current.expression.trimEnd()
            if (expr.isEmpty()) {
                if (op == "−" || op == "-") {
                    return@update current.copy(expression = "-")
                }
                return@update current
            }
            // Check if current expression already ends with an operator: e.g. "5 +", "5 ×", "5 ÷", "5 −"
            val operatorEndRegex = Regex("""\s([+\-−×*÷/])\s?$""")
            val newExpr = if (operatorEndRegex.containsMatchIn(expr)) {
                expr.replace(operatorEndRegex, " $op ")
            } else if (expr == "-") {
                if (op == "−" || op == "-") "-" else ""
            } else {
                "$expr $op "
            }
            current.copy(expression = newExpr, errorMessage = null)
        }
        try {
            Choreographer.getInstance().postFrameCallback {
                val tFrame = SystemClock.elapsedRealtime()
                Log.i("PERF_OMNICALC", "KEYPAD_METRIC: operator '$op' rendered in ${tFrame - tStart} ms")
            }
        } catch (_: Throwable) {
            // Guard for host JVM test runner
        }
    }

    fun onScientificFunction(func: String) {
        _uiState.update { current ->
            val newExpr = current.expression + func
            current.copy(expression = newExpr, errorMessage = null)
        }
    }

    fun onParentheses() {
        _uiState.update { current ->
            val expr = current.expression
            val openCount = expr.count { it == '(' }
            val closeCount = expr.count { it == ')' }
            val lastChar = expr.lastOrNull()
            val charToAdd = if (openCount > closeCount && (lastChar?.isDigit() == true || lastChar == ')' || lastChar == '%')) {
                ")"
            } else if (expr.isEmpty() || expr.endsWith(" ") || lastChar == '(') {
                "("
            } else {
                " × ("
            }
            val newExpr = expr + charToAdd
            val preview = calculatePreview(newExpr)
            current.copy(
                expression = newExpr,
                liveResult = preview.first,
                errorMessage = preview.second
            )
        }
    }

    fun onPercentage() {
        _uiState.update { current ->
            val expr = current.expression
            if (expr.isEmpty() || expr.endsWith("%") || expr.endsWith(" ") || expr.endsWith("-")) return@update current
            val newExpr = expr + "%"
            val preview = calculatePreview(newExpr)
            current.copy(
                expression = newExpr,
                liveResult = preview.first,
                errorMessage = preview.second
            )
        }
    }

    fun onSignChange() {
        _uiState.update { current ->
            val expr = current.expression.trim()
            if (expr.isEmpty() || expr == "0") return@update current

            val newExpr = when {
                expr.startsWith("-(") && expr.endsWith(")") -> {
                    expr.removePrefix("-(").removeSuffix(")")
                }
                expr.startsWith("-") && !expr.drop(1).contains(' ') -> {
                    expr.removePrefix("-")
                }
                !expr.contains(' ') && !expr.startsWith("-") -> {
                    "-$expr"
                }
                else -> {
                    "-($expr)"
                }
            }
            val preview = calculatePreview(newExpr)
            current.copy(
                expression = newExpr,
                liveResult = preview.first,
                errorMessage = preview.second
            )
        }
    }

    fun onBackspace() {
        _uiState.update { current ->
            if (current.expression.isEmpty()) return@update current
            var newExpr = current.expression.trimEnd()
            if (newExpr.isNotEmpty()) {
                newExpr = newExpr.dropLast(1).trimEnd()
            }
            val preview = if (newExpr.isNotEmpty()) calculatePreview(newExpr) else Pair("", null)
            current.copy(
                expression = newExpr,
                liveResult = preview.first,
                errorMessage = preview.second
            )
        }
    }

    fun onClear() {
        _uiState.update {
            it.copy(expression = "", liveResult = "", errorMessage = null)
        }
    }

    fun onEquals() {
        val current = _uiState.value
        if (current.expression.isBlank()) return

        when (val result = evaluator.evaluate(current.expression)) {
            is EvalResult.Success -> {
                viewModelScope.launch {
                    historyDao.insertHistory(
                        CalculationHistoryEntity(
                            toolId = "basic_calc",
                            expression = current.expression,
                            result = result.formatted
                        )
                    )
                }
                _uiState.update {
                    it.copy(
                        expression = result.formatted,
                        liveResult = "",
                        errorMessage = null
                    )
                }
            }
            is EvalResult.Error -> {
                val quick = com.omnicalc.platform.core.math.QuickCalculationEngine.evaluateQuick(current.expression)
                if (quick != null) {
                    viewModelScope.launch {
                        historyDao.insertHistory(
                            CalculationHistoryEntity(
                                toolId = quick.targetRoute ?: "quick_calc",
                                expression = current.expression,
                                result = quick.primaryResult
                            )
                        )
                    }
                    _uiState.update {
                        it.copy(
                            expression = quick.primaryResult,
                            liveResult = "",
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun onToggleAngleMode() {
        val newMode = if (_uiState.value.angleMode == AngleMode.DEGREES) AngleMode.RADIANS else AngleMode.DEGREES
        evaluator.angleMode = newMode
        _uiState.update { it.copy(angleMode = newMode) }
        viewModelScope.launch {
            preferencesRepository.setAngleMode(newMode)
        }
        val preview = calculatePreview(_uiState.value.expression)
        _uiState.update { it.copy(liveResult = preview.first, errorMessage = preview.second) }
    }

    fun onToggleScientific() {
        _uiState.update { it.copy(isScientificExpanded = !it.isScientificExpanded) }
    }

    fun setScientificExpanded(expanded: Boolean) {
        if (_uiState.value.isScientificExpanded == expanded) return
        _uiState.update { it.copy(isScientificExpanded = expanded) }
    }

    fun onMemoryAction(action: String) {
        val current = _uiState.value
        when (action) {
            "MC" -> _uiState.update { it.copy(memory = BigDecimal.ZERO) }
            "MR" -> {
                if (current.memory.compareTo(BigDecimal.ZERO) != 0) {
                    val memStr = current.memory.stripTrailingZeros().toPlainString()
                    onDigit(memStr)
                }
            }
            "M+" -> {
                val eval = evaluator.evaluate(current.expression)
                if (eval is EvalResult.Success) {
                    _uiState.update { it.copy(memory = it.memory.add(eval.value)) }
                }
            }
            "M-" -> {
                val eval = evaluator.evaluate(current.expression)
                if (eval is EvalResult.Success) {
                    _uiState.update { it.copy(memory = it.memory.subtract(eval.value)) }
                }
            }
        }
    }

    fun restoreExpression(expr: String) {
        val preview = calculatePreview(expr)
        _uiState.update {
            it.copy(
                expression = expr,
                liveResult = preview.first,
                errorMessage = preview.second
            )
        }
    }

    private fun calculatePreview(expr: String): Pair<String, String?> {
        if (expr.isBlank()) return Pair("", null)
        val eval = evaluator.evaluate(expr)
        if (eval is EvalResult.Success) {
            return Pair(eval.formatted, null)
        }
        val quick = com.omnicalc.platform.core.math.QuickCalculationEngine.evaluateQuick(expr)
        if (quick != null) {
            return Pair(quick.primaryResult, null)
        }
        return Pair("", null)
    }
}

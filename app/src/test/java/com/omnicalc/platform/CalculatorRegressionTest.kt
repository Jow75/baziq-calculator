package com.omnicalc.platform

import com.omnicalc.platform.core.database.daos.HistoryDao
import com.omnicalc.platform.core.database.entities.CalculationHistoryEntity
import com.omnicalc.platform.core.math.AngleMode
import com.omnicalc.platform.core.math.EvalResult
import com.omnicalc.platform.core.math.ExpressionEvaluator
import com.omnicalc.platform.core.preferences.AppTheme
import com.omnicalc.platform.core.preferences.PreferencesRepository
import com.omnicalc.platform.core.preferences.UserPreferences
import com.omnicalc.platform.feature.calculator.CalculatorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Regression tests for calculator defects confirmed during the Baziq Hue takeover review.
 * Each test reproduces a defect against the original implementation before the fix.
 */
class CalculatorRegressionTest {

    private val evaluator = ExpressionEvaluator(angleMode = AngleMode.DEGREES)

    private fun assertValue(expression: String, expected: String) {
        val result = evaluator.evaluate(expression)
        assertTrue("Expected success for '$expression' but got $result", result is EvalResult.Success)
        val value = (result as EvalResult.Success).value
        assertEquals("Wrong value for '$expression'", 0, BigDecimal(expected).compareTo(value))
    }

    private fun assertFormatted(expression: String, expected: String) {
        val result = evaluator.evaluate(expression)
        assertTrue("Expected success for '$expression' but got $result", result is EvalResult.Success)
        assertEquals("Wrong display for '$expression'", expected, (result as EvalResult.Success).formatted)
    }

    // Defect: a minus sign after a postfix operator (% or !) was parsed as unary negation,
    // so "50% - 10" and "5! - 3" failed with "Malformed expression".
    @Test
    fun subtractionAfterPostfixPercentIsBinary() {
        assertValue("50% - 10", "-9.5")
        assertValue("200 × 10% − 5", "15")
    }

    @Test
    fun subtractionAfterFactorialIsBinary() {
        assertValue("5! - 3", "117")
        assertValue("3! − 1", "5")
    }

    // Defect: a prefix negation after "^" popped the pending power operator,
    // so "2^-1" failed with "Missing operand for ^".
    @Test
    fun negativeExponentEvaluates() {
        assertValue("2^-1", "0.5")
        assertValue("2 ^ −2", "0.25")
        assertValue("-2^2", "-4")
    }

    // Defect: double-precision noise leaked into the display, e.g. sin(30) showed 0.4999999999999999.
    @Test
    fun trigonometricResultsDisplayWithoutBinaryNoise() {
        assertFormatted("sin(30)", "0.5")
        assertFormatted("cos(60)", "0.5")
        assertFormatted("tan(45)", "1")
        assertFormatted("asin(0.5)", "30")
        assertFormatted("log(1000)", "3")
    }

    // Defect: formatting a result with a huge exponent built a plain string with one character
    // per digit (about 1 GB for 10^999999999), exhausting the heap during live preview.
    @Test(timeout = 10_000)
    fun hugePowerFormatsWithoutBuildingPlainString() {
        val result = evaluator.evaluate("10^999999999")
        assertTrue(result is EvalResult.Success)
        val formatted = (result as EvalResult.Success).formatted
        assertTrue("Expected scientific notation but got '$formatted'", formatted.contains("E"))
        assertTrue(formatted.length < 32)
    }

    @Test(timeout = 10_000)
    fun tinyResultFormatsWithoutBuildingPlainString() {
        val result = evaluator.evaluate("10^-999999999")
        assertTrue(result is EvalResult.Success)
        val formatted = (result as EvalResult.Success).formatted
        assertTrue("Expected scientific notation but got '$formatted'", formatted.contains("E-"))
        assertTrue(formatted.length < 32)
    }

    // Behaviours that already worked and must not regress with the fixes above.
    @Test
    fun decimalArithmeticIsExact() {
        assertFormatted("0.1 + 0.2", "0.3")
        assertFormatted("1 ÷ 3 × 3", "1")
        assertFormatted("2 ÷ 3", "0.6666666666666667")
        assertFormatted("1,000 + 1", "1001")
    }

    @Test
    fun unaryMinusStillWorksInPrefixPositions() {
        assertValue("-5 + 10", "5")
        assertValue("10 × -2", "-20")
        assertValue("(2 − 5) × -2", "6")
        assertValue("sqrt(-(-16))", "4")
    }

    @Test
    fun fractionalPowersDisplayWithoutBinaryNoise() {
        assertFormatted("27^(1 ÷ 3)", "3")
        assertFormatted("16^0.25", "2")
    }

    // Defect: memory recall formatted the stored value with toPlainString(), so recalling a
    // huge value (e.g. after M+ on 10^999999999) exhausted the heap and crashed the app.
    @Test(timeout = 10_000)
    fun memoryRecallOfHugeValueDoesNotExhaustHeap() = runTest(UnconfinedTestDispatcher()) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val vm = CalculatorViewModel(NoOpHistoryDao(), FixedPreferences())
            vm.onDigit("10^999999999")
            vm.onMemoryAction("M+")
            vm.onClear()
            vm.onMemoryAction("MR")
            assertEquals("1E+999999999", vm.uiState.value.expression)
        } finally {
            Dispatchers.resetMain()
        }
    }


    private fun withViewModel(block: (CalculatorViewModel) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block(CalculatorViewModel(NoOpHistoryDao(), FixedPreferences()))
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun CalculatorViewModel.type(vararg keys: String) = keys.forEach { key ->
        when (key) {
            "+", "−", "×", "÷" -> onOperator(key)
            "=" -> onEquals()
            "⌫" -> onBackspace()
            "MR", "M+", "M-", "MC" -> onMemoryAction(key)
            else -> onDigit(key)
        }
    }

    private val CalculatorViewModel.expr get() = uiState.value.expression

    // --- Backspace (the key was enlarged in 1.1.1; its behaviour is unchanged) ---

    @Test
    fun backspaceDeletesExactlyOneCharacterOfAnExpression() = withViewModel { vm ->
        vm.type("7", "+", "7")
        assertEquals("7 + 7", vm.expr)
        assertEquals("14", vm.uiState.value.liveResult)
        vm.type("⌫")
        assertEquals("7 + ", vm.expr)
        vm.type("⌫")
        assertEquals("7", vm.expr)
        assertEquals("7", vm.uiState.value.liveResult)
        vm.type("⌫")
        assertEquals("", vm.expr)
        assertEquals("", vm.uiState.value.liveResult)
    }

    @Test
    fun backspaceThenRetypeGivesTheExpectedResult() = withViewModel { vm ->
        vm.type("7", "+", "7", "⌫", "3")
        assertEquals("10", vm.uiState.value.liveResult)
        vm.type("=")
        assertEquals("10", vm.expr)
    }

    // Defect (found on the phone): √ 1 4 4 = reported "Mismatched parentheses", because the
    // function keys open a bracket that only the ( ) key closed.
    @Test
    fun equalsClosesBracketsOpenedByFunctionKeys() = withViewModel { vm ->
        vm.onScientificFunction("sqrt(")
        vm.type("1", "4", "4")
        assertEquals("12", vm.uiState.value.liveResult)
        vm.type("=")
        assertEquals("12", vm.expr)
        assertEquals(null, vm.uiState.value.errorMessage)

        vm.onClear()
        vm.onScientificFunction("sqrt(")
        vm.onScientificFunction("sqrt(")
        vm.type("8", "1", "=")
        assertEquals("3", vm.expr)

        // Nothing to close yet: still reported, not guessed
        vm.onClear()
        vm.onScientificFunction("sqrt(")
        vm.type("=")
        assertEquals("sqrt(", vm.expr)
        assertTrue(vm.uiState.value.errorMessage != null)
    }

    // Defect (1.1.1 report): after a backspace onto an operator the next digit was shown
    // joined to it ("7 +3"), and a decimal point gave "7 +.".
    @Test
    fun backspaceOntoAnOperatorKeepsTheExpressionSpaced() = withViewModel { vm ->
        vm.type("7", "+", "7", "⌫", "3")
        assertEquals("7 + 3", vm.expr)
        vm.type("⌫", ".", "5")
        assertEquals("7 + 0.5", vm.expr)
        assertEquals("7.5", vm.uiState.value.liveResult)
        vm.type("⌫", "⌫", "⌫", "−", "2")
        assertEquals("7 − 2", vm.expr)
        vm.type("⌫", "⌫", "⌫")
        assertEquals("", vm.expr)
    }

    @Test
    fun backspaceOnDecimalInput() = withViewModel { vm ->
        vm.type("3", ".", "1", "4")
        vm.type("⌫")
        assertEquals("3.1", vm.expr)
        vm.type("⌫")
        assertEquals("3.", vm.expr)
        vm.type(".") // still a single decimal point
        assertEquals("3.", vm.expr)
        vm.type("⌫", "5")
        assertEquals("35", vm.expr)
    }

    @Test
    fun rapidBackspaceStopsAtEmptyAndNeverClearsMemory() = withViewModel { vm ->
        vm.type("9", "M+", "1", "2", "×", "3")
        repeat(25) { vm.onBackspace() }
        assertEquals("", vm.expr)
        assertEquals(0, BigDecimal("9").compareTo(vm.uiState.value.memory))
        vm.onToggleScientific()
        vm.type("⌫")
        assertTrue(vm.uiState.value.isScientificExpanded) // adjacent toggle unaffected
    }

    // --- Post-equals and memory recall: CURRENT behaviour, documented, intentionally unchanged ---

    @Test
    fun currentBehaviour_digitAfterEqualsAppendsToTheResult() = withViewModel { vm ->
        vm.type("4", "2", "=")
        assertEquals("42", vm.expr)
        vm.type("5")
        assertEquals("425", vm.expr)

        vm.onClear()
        vm.type("7", "+", "7", "=", "2")
        assertEquals("142", vm.expr)
    }

    @Test
    fun currentBehaviour_operatorAfterEqualsContinuesFromTheResult() = withViewModel { vm ->
        vm.type("7", "+", "7", "=", "×", "2")
        assertEquals("14 × 2", vm.expr)
        assertEquals("28", vm.uiState.value.liveResult)
    }

    @Test
    fun currentBehaviour_memoryRecallAppendsToTheCurrentNumber() = withViewModel { vm ->
        vm.type("3", "M+")
        vm.onClear()

        vm.type("7", "+", "MR")
        assertEquals("7 + 3", vm.expr) // after an operator: inserted as the next operand

        vm.onClear()
        vm.type("4", "2", "=", "MR")
        assertEquals("423", vm.expr) // after equals: digits joined onto the result

        vm.onClear()
        vm.type("1", "2", "MR")
        assertEquals("123", vm.expr) // mid-number: joined as well
    }

    private class NoOpHistoryDao : HistoryDao {
        override fun getRecentHistory(limit: Int): Flow<List<CalculationHistoryEntity>> = flowOf(emptyList())
        override fun getHistoryByTool(toolId: String, limit: Int): Flow<List<CalculationHistoryEntity>> = flowOf(emptyList())
        override suspend fun insertHistory(item: CalculationHistoryEntity): Long = 0
        override suspend fun deleteHistory(id: Long) = Unit
        override suspend fun clearAllHistory() = Unit
        override suspend fun setFavorite(id: Long, isFav: Boolean) = Unit
    }

    private class FixedPreferences : PreferencesRepository {
        override val userPreferencesFlow: Flow<UserPreferences> = flowOf(UserPreferences())
        override suspend fun setTheme(theme: AppTheme) = Unit
        override suspend fun setDecimalPrecision(precision: Int) = Unit
        override suspend fun setGrouping(enabled: Boolean) = Unit
        override suspend fun setHapticFeedback(enabled: Boolean) = Unit
        override suspend fun setAngleMode(mode: AngleMode) = Unit
        override suspend fun setDefaultCurrency(currencyCode: String) = Unit
    }
}

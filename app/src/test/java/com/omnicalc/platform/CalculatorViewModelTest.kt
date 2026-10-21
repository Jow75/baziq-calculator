package com.omnicalc.platform

import com.omnicalc.platform.core.database.daos.HistoryDao
import com.omnicalc.platform.core.database.entities.CalculationHistoryEntity
import com.omnicalc.platform.core.math.AngleMode
import com.omnicalc.platform.core.preferences.AppTheme
import com.omnicalc.platform.core.preferences.PreferencesRepository
import com.omnicalc.platform.core.preferences.UserPreferences
import com.omnicalc.platform.feature.calculator.CalculatorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {

    private class FakeHistoryDao : HistoryDao {
        val historyList = mutableListOf<CalculationHistoryEntity>()
        private val historyFlow = MutableStateFlow<List<CalculationHistoryEntity>>(emptyList())

        override fun getRecentHistory(limit: Int): Flow<List<CalculationHistoryEntity>> = historyFlow.asStateFlow()

        override fun getHistoryByTool(toolId: String, limit: Int): Flow<List<CalculationHistoryEntity>> = historyFlow.asStateFlow()

        override suspend fun insertHistory(item: CalculationHistoryEntity): Long {
            val id = (historyList.size + 1).toLong()
            val entity = item.copy(id = id)
            historyList.add(entity)
            historyFlow.value = historyList.toList()
            return id
        }

        override suspend fun deleteHistory(id: Long) {
            historyList.removeAll { it.id == id }
            historyFlow.value = historyList.toList()
        }

        override suspend fun clearAllHistory() {
            historyList.clear()
            historyFlow.value = emptyList()
        }

        override suspend fun setFavorite(id: Long, isFav: Boolean) {
            val index = historyList.indexOfFirst { it.id == id }
            if (index != -1) {
                historyList[index] = historyList[index].copy(isFavorite = isFav)
                historyFlow.value = historyList.toList()
            }
        }
    }

    private class FakePreferencesRepository : PreferencesRepository {
        val prefsFlow = MutableStateFlow(UserPreferences())

        override val userPreferencesFlow: Flow<UserPreferences> = prefsFlow.asStateFlow()

        override suspend fun setTheme(theme: AppTheme) {
            prefsFlow.value = prefsFlow.value.copy(theme = theme)
        }

        override suspend fun setDecimalPrecision(precision: Int) {
            prefsFlow.value = prefsFlow.value.copy(decimalPrecision = precision)
        }

        override suspend fun setGrouping(enabled: Boolean) {
            prefsFlow.value = prefsFlow.value.copy(useGroupingSeparators = enabled)
        }

        override suspend fun setHapticFeedback(enabled: Boolean) {
            prefsFlow.value = prefsFlow.value.copy(hapticFeedbackEnabled = enabled)
        }

        override suspend fun setAngleMode(mode: AngleMode) {
            prefsFlow.value = prefsFlow.value.copy(angleMode = mode)
        }

        override suspend fun setDefaultCurrency(currencyCode: String) {
            prefsFlow.value = prefsFlow.value.copy(defaultCurrency = currencyCode)
        }
    }

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testBasicDigitInputAndLivePreview() = runTest(testDispatcher) {
        val historyDao = FakeHistoryDao()
        val prefsRepo = FakePreferencesRepository()
        val vm = CalculatorViewModel(historyDao, prefsRepo)
        testScheduler.advanceUntilIdle()

        vm.onDigit("5")
        assertEquals("5", vm.uiState.value.expression)

        vm.onOperator("+")
        assertEquals("5 + ", vm.uiState.value.expression)

        vm.onDigit("3")
        assertEquals("5 + 3", vm.uiState.value.expression)
        assertEquals("8", vm.uiState.value.liveResult)

        vm.onEquals()
        testScheduler.advanceUntilIdle()
        assertEquals("8", vm.uiState.value.expression)
        assertEquals("", vm.uiState.value.liveResult)
    }

    @Test
    fun testOperatorReplacementOnBackToBackPress() = runTest(testDispatcher) {
        val historyDao = FakeHistoryDao()
        val prefsRepo = FakePreferencesRepository()
        val vm = CalculatorViewModel(historyDao, prefsRepo)
        testScheduler.advanceUntilIdle()

        vm.onDigit("10")
        vm.onOperator("+")
        assertEquals("10 + ", vm.uiState.value.expression)

        // User changes mind and presses multiply
        vm.onOperator("×")
        assertEquals("10 × ", vm.uiState.value.expression)

        // User changes mind again and presses divide
        vm.onOperator("÷")
        assertEquals("10 ÷ ", vm.uiState.value.expression)

        vm.onDigit("2")
        assertEquals("10 ÷ 2", vm.uiState.value.expression)
        assertEquals("5", vm.uiState.value.liveResult)

        vm.onEquals()
        testScheduler.advanceUntilIdle()
        assertEquals("5", vm.uiState.value.expression)
    }

    @Test
    fun testDecimalPointSanitization() = runTest(testDispatcher) {
        val historyDao = FakeHistoryDao()
        val prefsRepo = FakePreferencesRepository()
        val vm = CalculatorViewModel(historyDao, prefsRepo)
        testScheduler.advanceUntilIdle()

        // Decimal on empty inserts 0.
        vm.onDigit(".")
        assertEquals("0.", vm.uiState.value.expression)

        vm.onDigit("5")
        assertEquals("0.5", vm.uiState.value.expression)

        // Duplicate decimal in same token is rejected
        vm.onDigit(".")
        assertEquals("0.5", vm.uiState.value.expression)

        vm.onOperator("+")
        assertEquals("0.5 + ", vm.uiState.value.expression)

        // Decimal after operator inserts 0.
        vm.onDigit(".")
        assertEquals("0.5 + 0.", vm.uiState.value.expression)

        vm.onDigit("2")
        assertEquals("0.5 + 0.2", vm.uiState.value.expression)
        assertEquals("0.7", vm.uiState.value.liveResult)

        vm.onEquals()
        testScheduler.advanceUntilIdle()
        assertEquals("0.7", vm.uiState.value.expression)
    }

    @Test
    fun testSignChangeToggling() = runTest(testDispatcher) {
        val historyDao = FakeHistoryDao()
        val prefsRepo = FakePreferencesRepository()
        val vm = CalculatorViewModel(historyDao, prefsRepo)
        testScheduler.advanceUntilIdle()

        // Single number toggle
        vm.onDigit("7")
        assertEquals("7", vm.uiState.value.expression)

        vm.onSignChange()
        assertEquals("-7", vm.uiState.value.expression)

        vm.onSignChange()
        assertEquals("7", vm.uiState.value.expression)

        // Compound expression toggle
        vm.onOperator("+")
        vm.onDigit("3")
        assertEquals("7 + 3", vm.uiState.value.expression)

        vm.onSignChange()
        assertEquals("-(7 + 3)", vm.uiState.value.expression)
        assertEquals("-10", vm.uiState.value.liveResult)

        // Reversible without dangling parentheses
        vm.onSignChange()
        assertEquals("7 + 3", vm.uiState.value.expression)
        assertEquals("10", vm.uiState.value.liveResult)
    }

    @Test
    fun testParenthesesSmartHandling() = runTest(testDispatcher) {
        val historyDao = FakeHistoryDao()
        val prefsRepo = FakePreferencesRepository()
        val vm = CalculatorViewModel(historyDao, prefsRepo)
        testScheduler.advanceUntilIdle()

        // Open parenthesis
        vm.onParentheses()
        assertEquals("(", vm.uiState.value.expression)

        vm.onDigit("4")
        vm.onOperator("+")
        vm.onDigit("6")
        assertEquals("(4 + 6", vm.uiState.value.expression)

        // Close parenthesis
        vm.onParentheses()
        assertEquals("(4 + 6)", vm.uiState.value.expression)

        vm.onOperator("×")
        vm.onDigit("2")
        assertEquals("(4 + 6) × 2", vm.uiState.value.expression)
        assertEquals("20", vm.uiState.value.liveResult)

        vm.onEquals()
        testScheduler.advanceUntilIdle()
        assertEquals("20", vm.uiState.value.expression)
    }

    @Test
    fun testPercentageCalculation() = runTest(testDispatcher) {
        val historyDao = FakeHistoryDao()
        val prefsRepo = FakePreferencesRepository()
        val vm = CalculatorViewModel(historyDao, prefsRepo)
        testScheduler.advanceUntilIdle()

        // 100 + 15% = 115
        vm.onDigit("100")
        vm.onOperator("+")
        vm.onDigit("15")
        vm.onPercentage()
        assertEquals("100 + 15%", vm.uiState.value.expression)
        assertEquals("115", vm.uiState.value.liveResult)

        vm.onEquals()
        testScheduler.advanceUntilIdle()
        assertEquals("115", vm.uiState.value.expression)

        // 200 - 20% = 160
        vm.onClear()
        vm.onDigit("200")
        vm.onOperator("-")
        vm.onDigit("20")
        vm.onPercentage()
        assertEquals("200 - 20%", vm.uiState.value.expression)
        assertEquals("160", vm.uiState.value.liveResult)

        vm.onEquals()
        testScheduler.advanceUntilIdle()
        assertEquals("160", vm.uiState.value.expression)
    }

    @Test
    fun testDivisionByZeroHandledCleanly() = runTest(testDispatcher) {
        val historyDao = FakeHistoryDao()
        val prefsRepo = FakePreferencesRepository()
        val vm = CalculatorViewModel(historyDao, prefsRepo)
        testScheduler.advanceUntilIdle()

        vm.onDigit("42")
        vm.onOperator("÷")
        vm.onDigit("0")
        vm.onEquals()
        testScheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.errorMessage!!.contains("zero", ignoreCase = true))
    }

    @Test
    fun testClearAndBackspace() = runTest(testDispatcher) {
        val historyDao = FakeHistoryDao()
        val prefsRepo = FakePreferencesRepository()
        val vm = CalculatorViewModel(historyDao, prefsRepo)
        testScheduler.advanceUntilIdle()

        vm.onDigit("1")
        vm.onDigit("2")
        vm.onDigit("3")
        assertEquals("123", vm.uiState.value.expression)

        vm.onBackspace()
        assertEquals("12", vm.uiState.value.expression)

        vm.onClear()
        assertEquals("", vm.uiState.value.expression)
        assertEquals("", vm.uiState.value.liveResult)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun testMemoryOperations() = runTest(testDispatcher) {
        val historyDao = FakeHistoryDao()
        val prefsRepo = FakePreferencesRepository()
        val vm = CalculatorViewModel(historyDao, prefsRepo)
        testScheduler.advanceUntilIdle()

        assertEquals(0, BigDecimal.ZERO.compareTo(vm.uiState.value.memory))

        // Compute 50 and add to memory
        vm.onDigit("50")
        vm.onMemoryAction("M+")
        assertEquals(0, BigDecimal("50").compareTo(vm.uiState.value.memory))

        vm.onClear()
        assertEquals("", vm.uiState.value.expression)

        // Recall memory
        vm.onMemoryAction("MR")
        assertEquals("50", vm.uiState.value.expression)

        // Subtract 20 from memory: expression is 20
        vm.onClear()
        vm.onDigit("20")
        vm.onMemoryAction("M-")
        assertEquals(0, BigDecimal("30").compareTo(vm.uiState.value.memory))

        // Clear memory
        vm.onMemoryAction("MC")
        assertEquals(0, BigDecimal.ZERO.compareTo(vm.uiState.value.memory))
    }

    @Test
    fun testAngleModeToggle() = runTest(testDispatcher) {
        val historyDao = FakeHistoryDao()
        val prefsRepo = FakePreferencesRepository()
        val vm = CalculatorViewModel(historyDao, prefsRepo)
        testScheduler.advanceUntilIdle()

        assertEquals(AngleMode.DEGREES, vm.uiState.value.angleMode)

        vm.onToggleAngleMode()
        testScheduler.advanceUntilIdle()
        assertEquals(AngleMode.RADIANS, vm.uiState.value.angleMode)
        assertEquals(AngleMode.RADIANS, prefsRepo.prefsFlow.value.angleMode)

        vm.onToggleAngleMode()
        testScheduler.advanceUntilIdle()
        assertEquals(AngleMode.DEGREES, vm.uiState.value.angleMode)
        assertEquals(AngleMode.DEGREES, prefsRepo.prefsFlow.value.angleMode)
    }

    @Test
    fun testHistoryPersistenceOnEquals() = runTest(testDispatcher) {
        val historyDao = FakeHistoryDao()
        val prefsRepo = FakePreferencesRepository()
        val vm = CalculatorViewModel(historyDao, prefsRepo)
        testScheduler.advanceUntilIdle()

        vm.onDigit("12")
        vm.onOperator("×")
        vm.onDigit("5")
        vm.onEquals()
        testScheduler.advanceUntilIdle()

        assertEquals("60", vm.uiState.value.expression)
        assertEquals(1, historyDao.historyList.size)
        val entry = historyDao.historyList.first()
        assertEquals("basic_calc", entry.toolId)
        assertEquals("12 × 5", entry.expression)
        assertEquals("60", entry.result)
    }
}


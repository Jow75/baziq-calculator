package com.omnicalc.platform

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.espresso.IdlingPolicies
import com.omnicalc.platform.core.currency.CurrencyRepository
import com.omnicalc.platform.feature.currency.CurrencyScreen
import com.omnicalc.platform.feature.currency.CurrencyViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Drives the real converter screen (taps and IME-style text commits on the amount fields).
 *
 * Defect: with a zero amount, tapping a currency and typing 9 gave 0.9. Tapping a converted
 * row showing "0.00" reported the tap's cursor placement as an edit, which was rewritten to
 * "0." and converted; the row then became the source with "0." already in it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CurrencyAmountEntryUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var vm: CurrencyViewModel

    // The first test in a JVM also warms up Compose and Robolectric; under a full, parallel
    // build that took longer than Espresso's default 60 s idle limit and failed at setContent.
    @Before
    fun allowForColdStart() {
        IdlingPolicies.setMasterPolicyTimeout(5, TimeUnit.MINUTES)
        IdlingPolicies.setIdlingResourceTimeout(5, TimeUnit.MINUTES)
    }

    private fun launch() {
        val repo = CurrencyRepository(
            currencyDao = FakeCurrencyDao(),
            primaryProvider = FixedRateProvider(),
            secondaryProvider = FixedRateProvider(),
            externalScope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined
        )
        runBlocking {
            repo.ensureDefaultsSeeded()
            repo.refreshRates(force = true)
        }
        vm = CurrencyViewModel(repo)
        compose.setContent {
            MaterialTheme { CurrencyScreen(viewModel = vm, onNavigateBack = {}) }
        }
        compose.waitForIdle()
    }

    private fun field(code: String) = compose.onNodeWithTag("amount_$code")

    /** Brings every row to zero through the UI: the source row's clear button. */
    private fun clearToZero() {
        compose.onNodeWithContentDescription("Clear").performClick()
        compose.waitForIdle()
        field("KES").assertTextEquals("0.00")
    }

    private fun type(code: String, text: String) {
        text.forEach { field(code).performTextInput(it.toString()) }
        compose.waitForIdle()
    }

    private fun amountOf(code: String) = vm.uiState.value.items.first { it.currency.code == code }.rawAmount

    @Test
    fun tappingAZeroConvertedRowAndTyping9Gives9() {
        launch()
        clearToZero()
        field("KES").performClick()
        type("KES", "9")
        assertEquals("KES", vm.uiState.value.activeCurrencyCode)
        assertEquals("9", vm.uiState.value.activeAmount)
        field("KES").assertTextEquals("9")
    }

    @Test
    fun zeroAmountThenTyping96Gives96() {
        launch()
        clearToZero()
        field("KES").performClick()
        type("KES", "96")
        assertEquals("96", vm.uiState.value.activeAmount)
        field("KES").assertTextEquals("96")
    }

    @Test
    fun zeroAmountThenTyping123Gives123() {
        launch()
        clearToZero()
        field("TZS").performClick()
        type("TZS", "123")
        assertEquals("123", vm.uiState.value.activeAmount)
    }

    @Test
    fun zeroAmountThenTypingADecimalGives9Point5() {
        launch()
        clearToZero()
        field("KES").performClick()
        type("KES", "9.5")
        assertEquals("9.5", vm.uiState.value.activeAmount)
        field("KES").assertTextEquals("9.5")
    }

    @Test
    fun aDeliberateLeadingDecimalPointStillWorks() {
        launch()
        clearToZero()
        field("KES").performClick()
        type("KES", ".5")
        assertEquals("0.5", vm.uiState.value.activeAmount)
    }

    // The cursor can land before the "0" (e.g. a tap on its left edge); 9 must not give 90.
    @Test
    fun digitTypedBeforeTheZeroReplacesIt() {
        launch()
        clearToZero()
        field("USD").performClick()
        field("USD").performTextInputSelection(TextRange(0))
        type("USD", "9")
        assertEquals("9", vm.uiState.value.activeAmount)
    }

    @Test
    fun existingNonZeroAmountIsEditedNotReplaced() {
        launch()
        // USD starts as the source with 1
        field("USD").performClick()
        type("USD", "0")
        assertEquals("10", vm.uiState.value.activeAmount)
        field("USD").performTextInputSelection(TextRange(1))
        type("USD", "5")
        assertEquals("150", vm.uiState.value.activeAmount)
        field("USD").assertTextEquals("150")
    }

    @Test
    fun switchingSourceCurrencyStartsAFreshAmount() {
        launch()
        clearToZero()
        field("KES").performClick()
        type("KES", "96")
        field("EUR").performClick()
        compose.waitForIdle()
        type("EUR", "7")
        assertEquals("EUR", vm.uiState.value.activeCurrencyCode)
        assertEquals("7", vm.uiState.value.activeAmount)
        field("EUR").assertTextEquals("7")
        // 7 EUR = 7 / 0.9 * 130 KES
        assertEquals("1011.11", amountOf("KES"))
    }

    @Test
    fun rapidTypingKeepsEveryCharacterOnce() {
        launch()
        clearToZero()
        field("KES").performClick()
        type("KES", "987654321")
        assertEquals("987654321", vm.uiState.value.activeAmount)
        field("KES").assertTextEquals("987,654,321")
    }

    @Test
    fun multiCharacterCommitReplacesTheZero() {
        launch()
        clearToZero()
        field("KES").performClick()
        field("KES").performTextInput("250")
        compose.waitForIdle()
        assertEquals("250", vm.uiState.value.activeAmount)
    }

    @Test
    fun conversionsFollowTheNewAmount() {
        launch()
        clearToZero()
        field("KES").performClick()
        type("KES", "96")
        assertEquals("0.74", amountOf("USD"))   // 96 / 130
        assertEquals("0.66", amountOf("EUR"))   // 96 / 130 * 0.9
        assertEquals("1920.00", amountOf("TZS")) // 96 / 130 * 2600
        assertEquals("325", amountOf("KMF"))    // 96 / 130 * 440, no minor unit
    }
}

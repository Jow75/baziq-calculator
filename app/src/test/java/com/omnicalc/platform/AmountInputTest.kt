package com.omnicalc.platform

import com.omnicalc.platform.feature.currency.AmountFieldState
import com.omnicalc.platform.feature.currency.AmountInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editing rules of the converter's amount field (see also CurrencyAmountEntryUiTest). */
class AmountInputTest {

    private val sent = mutableListOf<String>()
    private val identity: (String) -> String = { sent += it; it }

    /** Simulates the keyboard: [typed] replaces the field's selection. */
    private fun AmountFieldState.type(typed: String): AmountFieldState {
        val newText = text.substring(0, selectionStart) + typed + text.substring(selectionEnd)
        val cursor = selectionStart + typed.length
        return AmountInput.onValueChange(this, newText, cursor, cursor, identity)
    }

    private fun AmountFieldState.typeAll(chars: String) = chars.fold(this) { s, c -> s.type(c.toString()) }

    private fun AmountFieldState.moveCursor(position: Int) =
        AmountInput.onValueChange(this, text, position, position, identity)

    @Test
    fun zeroIsSelectedAsAPlaceholder() {
        val s = AmountInput.initial("0")
        assertTrue(s.replaceOnInput)
        assertEquals(0, s.selectionStart)
        assertEquals(1, s.selectionEnd)
    }

    @Test
    fun firstDigitReplacesTheZero() {
        assertEquals("9", AmountInput.initial("0").type("9").text)
        assertEquals("96", AmountInput.initial("0").typeAll("96").text)
        assertEquals("123", AmountInput.initial("0").typeAll("123").text)
    }

    @Test
    fun aFormattedZeroIsReplacedToo() {
        assertEquals("9", AmountInput.initial("0.00").type("9").text)
        assertEquals("9", AmountInput.initial("").type("9").text)
    }

    // A tap can put the cursor before or after the zero instead of selecting it
    @Test
    fun cursorBesideTheZeroStillReplacesIt() {
        assertEquals("9", AmountInput.initial("0").moveCursor(0).type("9").text)
        assertEquals("9", AmountInput.initial("0").moveCursor(1).type("9").text)
        // ...and the placeholder stays selected after the tap
        val tapped = AmountInput.initial("0").moveCursor(1)
        assertEquals(0, tapped.selectionStart)
        assertEquals(1, tapped.selectionEnd)
    }

    // A placeholder with a cursor beside the zero rather than selecting it (e.g. after typing 0)
    @Test
    fun insertionBesideTheZeroIsTreatedAsReplacement() {
        val before = AmountFieldState("0", 0, replaceOnInput = true)
        val after = AmountFieldState("0", 1, replaceOnInput = true)
        assertEquals("9", AmountInput.onValueChange(before, "90", 1, 1, identity).text)
        assertEquals("9", AmountInput.onValueChange(after, "09", 2, 2, identity).text)
    }

    @Test
    fun selectedZeroReplacedByTextEndingInZero() {
        assertEquals("250", AmountInput.initial("0").type("250").text)
        assertEquals("100", AmountInput.initial("0").typeAll("100").text)
    }

    // The tap that focuses the field reports a cursor position (often before the
    // right-aligned number); typing must still continue the amount
    @Test
    fun focusingTapKeepsTheCursorAtTheEnd() {
        val focused = AmountInput.onFocus(AmountInput.initial("125").moveCursor(1))
        assertEquals(3, focused.selectionStart)
        val tapped = focused.moveCursor(0)
        assertEquals(3, tapped.selectionStart)
        assertEquals("1259", tapped.type("9").text)
        // later taps move the cursor
        assertEquals("9125", tapped.moveCursor(0).type("9").text)
    }

    @Test
    fun decimalEntryWorksNormally() {
        assertEquals("9.5", AmountInput.initial("0").typeAll("9.5").text)
        assertEquals("0.5", AmountInput.initial("0").typeAll(".5").text)
        assertEquals("0.05", AmountInput.initial("0").typeAll("0.05").text)
    }

    @Test
    fun typedZeroKeepsThePlaceholderButAPointEndsIt() {
        val afterZero = AmountInput.initial("0").type("0")
        assertEquals("0", afterZero.text)
        assertTrue(afterZero.replaceOnInput)
        assertEquals("7", afterZero.type("7").text)

        val afterPoint = AmountInput.initial("0").type(".")
        assertEquals("0.", afterPoint.text)
        assertFalse(afterPoint.replaceOnInput)
    }

    @Test
    fun nonZeroAmountIsEditedAtTheCursor() {
        val s = AmountInput.initial("125")
        assertFalse(s.replaceOnInput)
        assertEquals(3, s.selectionStart)
        assertEquals("1205", s.moveCursor(2).type("0").text)
        assertEquals("1259", s.type("9").text)
    }

    @Test
    fun cursorMovesInANonZeroAmountAreKeptAndNotSent() {
        val moved = AmountInput.initial("125").moveCursor(1)
        assertEquals(1, moved.selectionStart)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun deletingBackToZeroMakesItAPlaceholderAgain() {
        val s = AmountInput.initial("0").type("5")
        val deleted = AmountInput.onValueChange(s, "", 0, 0, identity)
        assertEquals("", deleted.text)
        assertTrue(deleted.replaceOnInput)
        assertEquals("8", deleted.type("8").text)
    }

    @Test
    fun nonNumericCharactersAreIgnored() {
        val zero = AmountInput.initial("0")
        assertEquals(zero, zero.type("-"))
        assertEquals("12", AmountInput.initial("12").type(",").text)
    }

    @Test
    fun redundantLeadingZerosAreRemoved() {
        val s = AmountInput.initial("0.")
        // "0." is a zero, so it is a placeholder when set by the view model
        assertTrue(s.replaceOnInput)
        val typed = AmountInput.onValueChange(AmountFieldState("5", 0), "05", 1, 1, identity)
        assertEquals("5", typed.text)
        assertEquals("0.5", AmountInput.onValueChange(AmountFieldState("0.5", 0), "00.5", 1, 1, identity).text)
    }

    @Test
    fun normalizedAmountFromTheViewModelIsShown() {
        val s = AmountInput.onValueChange(AmountFieldState("1.2", 3), "1.2.", 4, 4) { "1.2" }
        assertEquals("1.2", s.text)
        assertEquals(3, s.selectionStart)
    }

    @Test
    fun everyKeystrokeIsSentOnceInOrder() {
        AmountInput.initial("0").typeAll("987654321")
        assertEquals(listOf("9", "98", "987", "9876", "98765", "987654", "9876543", "98765432", "987654321"), sent)
    }

    @Test
    fun insertedTextFollowsTheSelection() {
        assertEquals("9", AmountInput.insertedText("0", 0, 1, "9"))
        assertEquals("250", AmountInput.insertedText("0", 0, 1, "250"))
        assertEquals("9", AmountInput.insertedText("0", 0, 0, "90"))
        assertEquals("9", AmountInput.insertedText("0", 1, 1, "09"))
        assertEquals("", AmountInput.insertedText("0", 0, 1, ""))
        // keyboard ignored the selection: the differing part is used
        assertEquals("9", AmountInput.insertedText("0", 1, 1, "90"))
    }
}

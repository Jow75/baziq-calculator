package com.omnicalc.platform.feature.currency

/**
 * Text and selection of the source amount field.
 *
 * @property replaceOnInput the field shows a zero amount ("0", or empty) that stands in for "no
 *           amount yet": the next typed text replaces it instead of being inserted beside it,
 *           wherever the cursor is. Typing 9 into it gives 9, not 0.9 or 90.
 * @property holdCursorAtEnd the field was just focused with an amount; the cursor position
 *           reported by the tap that focused it is ignored, so typing continues the amount.
 *           A tap left of the right-aligned number would otherwise put the cursor before it.
 */
data class AmountFieldState(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart,
    val replaceOnInput: Boolean = false,
    val holdCursorAtEnd: Boolean = false
)

/** Editing rules for the source amount field, kept free of Compose so they can be unit tested. */
object AmountInput {

    /** A zero amount, as set by the view model ("0", "0.00", "") or left after deleting. */
    fun isZero(text: String): Boolean = text.all { it == '0' || it == '.' }

    /**
     * State for an amount set by something other than typing (the field becoming the source,
     * a cleared amount, a detected local currency). A zero amount is selected, so it is shown
     * as about to be replaced; any other amount gets the cursor at its end.
     */
    fun initial(text: String): AmountFieldState =
        if (isZero(text)) AmountFieldState(text, 0, text.length, replaceOnInput = true)
        else AmountFieldState(text, text.length)

    /**
     * The field gained focus: same rule as [initial], applied to what the field shows now; an
     * amount also keeps its cursor at the end through the focusing tap.
     */
    fun onFocus(current: AmountFieldState): AmountFieldState =
        initial(current.text).let { if (it.replaceOnInput) it else it.copy(holdCursorAtEnd = true) }

    /**
     * Applies a change reported by the text field.
     *
     * @param normalize sends the candidate amount to the view model and returns the amount it
     *        actually converts; called only when the text changed, never for cursor moves.
     */
    fun onValueChange(
        current: AmountFieldState,
        newText: String,
        newSelectionStart: Int,
        newSelectionEnd: Int,
        normalize: (String) -> String
    ): AmountFieldState {
        if (newText == current.text) {
            // Cursor or selection only. A placeholder stays fully selected (a tap on it must
            // not leave the cursor beside the zero); otherwise the user's cursor is kept.
            return when {
                current.replaceOnInput -> current.copy(selectionStart = 0, selectionEnd = current.text.length)
                current.holdCursorAtEnd -> current.copy(holdCursorAtEnd = false)
                else -> current.copy(selectionStart = newSelectionStart, selectionEnd = newSelectionEnd)
            }
        }

        if (current.replaceOnInput) {
            val inserted = insertedText(current.text, current.selectionStart, current.selectionEnd, newText)
            if (inserted.isEmpty()) {
                // Deleting the placeholder leaves an empty field, still a placeholder
                val cleared = normalize("")
                return AmountFieldState(cleared, cleared.length, replaceOnInput = isZero(cleared))
            }
            val typed = inserted.filter { it.isDigit() || it == '.' }
            if (typed.isEmpty()) return current // e.g. "-" or "," from the keyboard: ignored
            return accept(if (typed.startsWith('.')) "0$typed" else typed, normalize)
        }

        val digits = newText.filter { it.isDigit() || it == '.' }
        // Redundant leading zeros go ("05" -> "5", "00.5" -> "0.5"); "0.5" stays
        val trimmed = digits.trimStart('0')
        val candidate = when {
            digits.length <= 1 || digits[0] != '0' || digits[1] == '.' -> digits
            trimmed.isEmpty() -> "0"
            trimmed.startsWith('.') -> "0$trimmed"
            else -> trimmed
        }
        val cursor = (newText.take(newSelectionStart).count { it.isDigit() || it == '.' } - (digits.length - candidate.length))
            .coerceIn(0, candidate.length)

        val normalized = normalize(candidate)
        return if (normalized == candidate) {
            AmountFieldState(candidate, cursor, replaceOnInput = candidate.isEmpty() || candidate == "0")
        } else {
            // The view model changed the text (e.g. "1.2.3" -> "1.23"): cursor to the end
            AmountFieldState(normalized, normalized.length, replaceOnInput = normalized.isEmpty() || normalized == "0")
        }
    }

    private fun accept(candidate: String, normalize: (String) -> String): AmountFieldState {
        val normalized = normalize(candidate)
        // A typed "0" keeps the placeholder (0 then 9 gives 9); "0." does not (0. then 5 gives 0.5)
        return AmountFieldState(normalized, normalized.length, replaceOnInput = normalized.isEmpty() || normalized == "0")
    }

    /**
     * The text the keyboard added. A keyboard replaces the selection [selectionStart] to
     * [selectionEnd] of [old] with what was typed: the selected zero ("0" -> "250" is 250), or
     * the empty selection at a cursor beside it ("0" -> "90" or "09" is 9). If [new] does not
     * have that shape, the part that differs from [old] at both ends is used.
     */
    fun insertedText(old: String, selectionStart: Int, selectionEnd: Int, new: String): String {
        val start = selectionStart.coerceIn(0, old.length)
        val end = selectionEnd.coerceIn(start, old.length)
        val before = old.substring(0, start)
        val after = old.substring(end)
        if (new.length >= before.length + after.length && new.startsWith(before) && new.endsWith(after)) {
            return new.substring(before.length, new.length - after.length)
        }
        var prefix = 0
        while (prefix < old.length && prefix < new.length && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < old.length - prefix && suffix < new.length - prefix &&
            old[old.length - 1 - suffix] == new[new.length - 1 - suffix]
        ) suffix++
        return new.substring(prefix, new.length - suffix)
    }
}

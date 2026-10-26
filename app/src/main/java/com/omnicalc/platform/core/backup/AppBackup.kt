package com.omnicalc.platform.core.backup

import com.omnicalc.platform.core.currency.CurrencyCatalog
import com.omnicalc.platform.core.database.entities.CalculationHistoryEntity
import com.omnicalc.platform.core.database.entities.CustomRateOverrideEntity
import com.omnicalc.platform.core.database.entities.FavoriteToolEntity
import com.omnicalc.platform.core.math.AngleMode
import com.omnicalc.platform.core.preferences.AppTheme
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * A backup of the user's own data: calculation history, favourite tools, the currency list
 * and its order, custom rates and settings. Downloaded exchange rates are left out (they are
 * fetched again). The file is versioned so later releases can still read it.
 */
@JsonClass(generateAdapter = true)
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAtMs: Long,
    val appVersion: String,
    val history: List<BackupHistoryItem> = emptyList(),
    val favorites: List<BackupFavorite> = emptyList(),
    /** Currency codes in the order the converter shows them. */
    val currencies: List<String> = emptyList(),
    val customRates: List<BackupCustomRate> = emptyList(),
    val preferences: BackupPreferences = BackupPreferences()
) {
    companion object {
        const val FORMAT = "baziq-calculator-backup"
        const val VERSION = 1
    }
}

@JsonClass(generateAdapter = true)
data class BackupHistoryItem(
    val toolId: String,
    val expression: String,
    val result: String,
    val timestampMs: Long,
    val inputsJson: String = "",
    val isFavorite: Boolean = false
)

@JsonClass(generateAdapter = true)
data class BackupFavorite(val toolId: String, val orderIndex: Int, val addedAtMs: Long)

@JsonClass(generateAdapter = true)
data class BackupCustomRate(val currencyCode: String, val customRateToBase: Double, val baseCurrency: String, val updatedAtMs: Long)

/** Settings that were set on the phone; a missing (null) one is left unchanged on import. */
@JsonClass(generateAdapter = true)
data class BackupPreferences(
    val theme: String? = null,
    val decimalPrecision: Int? = null,
    val useGroupingSeparators: Boolean? = null,
    val hapticFeedbackEnabled: Boolean? = null,
    val angleMode: String? = null,
    val autoLocalCurrency: Boolean? = null,
    val locationPromptAnswered: Boolean? = null,
    val lastAppliedCountry: String? = null,
    val baseCurrency: String? = null
)

/** What an import changed, for the confirmation message. */
data class ImportSummary(
    val historyAdded: Int,
    val historyAlreadyPresent: Int,
    val currencies: Int,
    val customRates: Int,
    val favorites: Int
)

class InvalidBackupException(message: String) : Exception(message)

object BackupCodec {
    // Generous limits that only reject files which cannot be a genuine backup
    const val MAX_HISTORY = 50_000
    const val MAX_FILE_BYTES = 50 * 1024 * 1024
    private const val MAX_TEXT = 10_000
    private const val MAX_INPUTS_JSON = 100_000

    private val adapter by lazy { Moshi.Builder().build().adapter(BackupFile::class.java).indent("  ") }

    fun encode(file: BackupFile): String = adapter.toJson(file)

    /**
     * Reads the text of a file chosen for import. Reading stops as soon as the file exceeds
     * [maxBytes], so picking a large file by mistake (a video, say) cannot exhaust memory.
     */
    fun readText(input: InputStream, maxBytes: Int = MAX_FILE_BYTES): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > maxBytes) throw InvalidBackupException("The file is too large to be a backup.")
            out.write(buffer, 0, read)
        }
        return out.toByteArray().decodeToString()
    }

    /** Parses and validates a backup; every problem is reported as [InvalidBackupException]. */
    fun decode(json: String): BackupFile {
        val file = try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            throw InvalidBackupException("This is not a Baziq Calculator backup file.")
        } ?: throw InvalidBackupException("The backup file is empty.")
        validate(file)
        return file
    }

    fun validate(file: BackupFile) {
        fun check(ok: Boolean, problem: String) { if (!ok) throw InvalidBackupException(problem) }

        check(file.format == BackupFile.FORMAT, "This is not a Baziq Calculator backup file.")
        check(file.version in 1..BackupFile.VERSION, "This backup was made by a newer version of the app (format ${file.version}). Update the app first.")
        check(file.history.size <= MAX_HISTORY, "The backup holds too many history entries.")
        file.history.forEach {
            check(it.toolId.isNotBlank() && it.toolId.length <= 100, "A history entry has an invalid tool.")
            check(it.expression.length <= MAX_TEXT && it.result.length <= MAX_TEXT, "A history entry is too long.")
            check(it.inputsJson.length <= MAX_INPUTS_JSON, "A history entry is too long.")
            check(it.timestampMs >= 0, "A history entry has an invalid date.")
        }
        check(file.favorites.size <= 1_000, "The backup holds too many favourites.")
        file.favorites.forEach { check(it.toolId.isNotBlank() && it.toolId.length <= 100, "A favourite has an invalid tool.") }
        check(file.currencies.size <= 1_000, "The backup holds too many currencies.")
        check(file.currencies.map { it.uppercase() }.distinct().size == file.currencies.size, "The currency list repeats a currency.")
        file.currencies.forEach { check(CurrencyCatalog.isSupported(it), "Unknown currency in the backup: $it") }
        file.customRates.forEach {
            check(CurrencyCatalog.isSupported(it.currencyCode) && CurrencyCatalog.isSupported(it.baseCurrency), "Unknown currency in a custom rate.")
            check(it.customRateToBase.isFinite() && it.customRateToBase > 0, "A custom rate is not a positive number.")
        }
        with(file.preferences) {
            theme?.let { t -> check(AppTheme.values().any { it.name == t }, "Unknown theme in the backup.") }
            angleMode?.let { a -> check(AngleMode.values().any { it.name == a }, "Unknown angle mode in the backup.") }
            decimalPrecision?.let { check(it in 0..20, "Invalid decimal precision in the backup.") }
            lastAppliedCountry?.let { check(it.length == 2 && it.all(Char::isLetter), "Invalid country code in the backup.") }
            baseCurrency?.let { check(CurrencyCatalog.isSupported(it), "Unknown base currency in the backup.") }
        }
    }
}

/** How a backup is combined with what the phone already holds. Nothing is deleted. */
object BackupMerge {

    /** Entries to add: those not already present (same tool, expression, result and time). */
    fun historyToAdd(existing: List<CalculationHistoryEntity>, incoming: List<BackupHistoryItem>): List<CalculationHistoryEntity> {
        val present = existing.map { HistoryKey(it.toolId, it.expression, it.result, it.timestampMs) }.toHashSet()
        return incoming
            .filter { present.add(HistoryKey(it.toolId, it.expression, it.result, it.timestampMs)) }
            .map {
                CalculationHistoryEntity(
                    toolId = it.toolId,
                    expression = it.expression,
                    result = it.result,
                    timestampMs = it.timestampMs,
                    inputsJson = it.inputsJson,
                    isFavorite = it.isFavorite
                )
            }
    }

    /** The backup's currencies in its order, followed by any others the phone already lists. */
    fun currencyOrder(current: List<String>, incoming: List<String>): List<String> {
        val fromBackup = incoming.map { it.uppercase() }
        return fromBackup + current.map { it.uppercase() }.filter { it !in fromBackup }
    }

    fun favorites(incoming: List<BackupFavorite>) = incoming.map { FavoriteToolEntity(it.toolId, it.orderIndex, it.addedAtMs) }

    fun customRates(incoming: List<BackupCustomRate>) = incoming.map {
        CustomRateOverrideEntity(it.currencyCode.uppercase(), it.customRateToBase, it.baseCurrency.uppercase(), it.updatedAtMs)
    }

    private data class HistoryKey(val toolId: String, val expression: String, val result: String, val timestampMs: Long)
}

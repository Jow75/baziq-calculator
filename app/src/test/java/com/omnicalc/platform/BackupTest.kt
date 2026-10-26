package com.omnicalc.platform

import com.omnicalc.platform.core.backup.BackupCodec
import com.omnicalc.platform.core.backup.BackupCustomRate
import com.omnicalc.platform.core.backup.BackupFavorite
import com.omnicalc.platform.core.backup.BackupFile
import com.omnicalc.platform.core.backup.BackupHistoryItem
import com.omnicalc.platform.core.backup.BackupMerge
import com.omnicalc.platform.core.backup.BackupPreferences
import com.omnicalc.platform.core.backup.InvalidBackupException
import com.omnicalc.platform.core.database.entities.CalculationHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The backup file used to carry the user's data across a reinstall or a signing change. */
class BackupTest {

    private val sample = BackupFile(
        exportedAtMs = 1_792_900_000_000,
        appVersion = "1.1.2",
        history = listOf(
            BackupHistoryItem("calculator", "7+7", "14", 1_792_000_000_000),
            BackupHistoryItem("currency", "100 USD", "12,984 KES", 1_792_000_100_000, inputsJson = "{\"from\":\"USD\"}", isFavorite = true)
        ),
        favorites = listOf(BackupFavorite("loan", 0, 1_792_000_000_000)),
        currencies = listOf("USD", "EUR", "KES", "KMF", "TZS", "AED", "OMR"),
        customRates = listOf(BackupCustomRate("KES", 129.5, "USD", 1_792_000_000_000)),
        preferences = BackupPreferences(theme = "DARK", decimalPrecision = 6, autoLocalCurrency = true, lastAppliedCountry = "KM", baseCurrency = "KMF")
    )

    private fun assertRejected(file: BackupFile) {
        try {
            BackupCodec.validate(file)
            fail("Expected the backup to be rejected")
        } catch (_: InvalidBackupException) {
        }
    }

    @Test
    fun roundTripKeepsEverything() {
        val json = BackupCodec.encode(sample)
        assertTrue(json.contains("\"format\": \"baziq-calculator-backup\""))
        assertTrue(json.contains("\"version\": 1"))
        assertEquals(sample, BackupCodec.decode(json))
    }

    @Test
    fun unknownFieldsFromALaterVersionAreIgnored() {
        val json = BackupCodec.encode(sample).replaceFirst("{", "{\n  \"addedLater\": [1, 2],")
        assertEquals(sample, BackupCodec.decode(json))
    }

    @Test
    fun otherFilesAreRejected() {
        for (text in listOf("", "hello", "{}", "[1,2]", "{\"format\":\"something-else\",\"version\":1,\"exportedAtMs\":0,\"appVersion\":\"x\"}")) {
            try {
                BackupCodec.decode(text)
                fail("Accepted: $text")
            } catch (_: InvalidBackupException) {
            }
        }
    }

    @Test
    fun invalidContentIsRejected() {
        assertRejected(sample.copy(version = 2))
        assertRejected(sample.copy(currencies = listOf("USD", "XXQ")))
        assertRejected(sample.copy(currencies = listOf("USD", "usd")))
        assertRejected(sample.copy(customRates = listOf(BackupCustomRate("KES", -1.0, "USD", 0))))
        assertRejected(sample.copy(customRates = listOf(BackupCustomRate("KES", Double.NaN, "USD", 0))))
        assertRejected(sample.copy(preferences = BackupPreferences(theme = "NEON")))
        assertRejected(sample.copy(preferences = BackupPreferences(decimalPrecision = 99)))
        assertRejected(sample.copy(preferences = BackupPreferences(lastAppliedCountry = "Comoros")))
        assertRejected(sample.copy(history = listOf(BackupHistoryItem("", "1", "1", 0))))
        assertRejected(sample.copy(history = listOf(BackupHistoryItem("calculator", "1", "1", -5))))
    }

    @Test
    fun importingTwiceDoesNotDuplicateHistory() {
        val existing = listOf(CalculationHistoryEntity(id = 3, toolId = "calculator", expression = "7+7", result = "14", timestampMs = 1_792_000_000_000))
        val added = BackupMerge.historyToAdd(existing, sample.history)
        assertEquals(1, added.size)
        assertEquals("100 USD", added.single().expression)
        assertEquals(0L, added.single().id) // new row id, never the backup's
        assertTrue(added.single().isFavorite)
        // identical entries inside one backup are added once
        assertEquals(1, BackupMerge.historyToAdd(emptyList(), listOf(sample.history[0], sample.history[0])).size)
    }

    @Test
    fun currencyOrderFollowsTheBackupAndKeepsOtherCurrencies() {
        assertEquals(
            listOf("KES", "USD", "EUR", "GBP"),
            BackupMerge.currencyOrder(current = listOf("USD", "GBP", "EUR"), incoming = listOf("KES", "USD", "EUR"))
        )
    }

    // Defect: import read the whole chosen file into memory before checking its size, so a
    // large file picked by mistake could crash the app.
    @Test
    fun readingAFileStopsOnceItExceedsTheLimit() {
        val json = BackupCodec.encode(sample)
        assertEquals(sample, BackupCodec.decode(BackupCodec.readText(json.byteInputStream(), maxBytes = json.toByteArray().size)))

        var bytesServed = 0L
        val endless = object : java.io.InputStream() {
            override fun read(): Int = if (bytesServed++ < Long.MAX_VALUE) 'x'.code else -1
        }
        try {
            BackupCodec.readText(endless, maxBytes = 1_000_000)
            fail("Expected the file to be rejected")
        } catch (_: InvalidBackupException) {
        }
        assertTrue("read $bytesServed bytes", bytesServed <= 1_000_000 + 64 * 1024)
    }
}

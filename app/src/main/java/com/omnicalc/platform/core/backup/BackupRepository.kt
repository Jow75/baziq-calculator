package com.omnicalc.platform.core.backup

import com.omnicalc.platform.core.database.daos.BackupDao
import com.omnicalc.platform.core.database.entities.WorkspaceCurrencyEntity
import com.omnicalc.platform.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the app's data into a [BackupFile], and merges a validated backup back in. */
class BackupRepository(
    private val backupDao: BackupDao,
    private val preferences: UserPreferencesRepository,
    private val appVersion: String
) {
    suspend fun export(): BackupFile = withContext(Dispatchers.IO) {
        BackupFile(
            exportedAtMs = System.currentTimeMillis(),
            appVersion = appVersion,
            history = backupDao.allHistory().map {
                BackupHistoryItem(it.toolId, it.expression, it.result, it.timestampMs, it.inputsJson, it.isFavorite)
            },
            favorites = backupDao.allFavorites().map { BackupFavorite(it.toolId, it.orderIndex, it.addedAtMs) },
            currencies = backupDao.workspace().map { it.currencyCode },
            customRates = backupDao.customRates().map {
                BackupCustomRate(it.currencyCode, it.customRateToBase, it.baseCurrency, it.updatedAtMs)
            },
            preferences = preferences.backupPreferences()
        )
    }

    suspend fun import(file: BackupFile): ImportSummary = withContext(Dispatchers.IO) {
        BackupCodec.validate(file)
        val history = BackupMerge.historyToAdd(backupDao.allHistory(), file.history)
        val order = if (file.currencies.isEmpty()) null
        else BackupMerge.currencyOrder(backupDao.workspace().map { it.currencyCode }, file.currencies)
        backupDao.restore(
            history = history,
            favorites = BackupMerge.favorites(file.favorites),
            workspace = order?.mapIndexed { index, code -> WorkspaceCurrencyEntity(code, index, isPinned = true) },
            customRates = BackupMerge.customRates(file.customRates)
        )
        preferences.restoreBackupPreferences(file.preferences)
        ImportSummary(
            historyAdded = history.size,
            historyAlreadyPresent = file.history.size - history.size,
            currencies = order?.size ?: 0,
            customRates = file.customRates.size,
            favorites = file.favorites.size
        )
    }
}

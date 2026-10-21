package com.omnicalc.platform.core.database.daos

import androidx.room.*
import com.omnicalc.platform.core.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM calculation_history ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 100): Flow<List<CalculationHistoryEntity>>

    @Query("SELECT * FROM calculation_history WHERE toolId = :toolId ORDER BY timestampMs DESC LIMIT :limit")
    fun getHistoryByTool(toolId: String, limit: Int = 50): Flow<List<CalculationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: CalculationHistoryEntity): Long

    @Query("DELETE FROM calculation_history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM calculation_history")
    suspend fun clearAllHistory()

    @Query("UPDATE calculation_history SET isFavorite = :isFav WHERE id = :id")
    suspend fun setFavorite(id: Long, isFav: Boolean)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_tools ORDER BY orderIndex ASC")
    fun getAllFavorites(): Flow<List<FavoriteToolEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(fav: FavoriteToolEntity)

    @Query("DELETE FROM favorite_tools WHERE toolId = :toolId")
    suspend fun removeFavorite(toolId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tools WHERE toolId = :toolId)")
    fun isFavorite(toolId: String): Flow<Boolean>

    @Query("DELETE FROM favorite_tools")
    suspend fun clearAllFavorites()
}

@Dao
abstract class CurrencyDao {
    @Query("SELECT * FROM cached_currency_rates")
    abstract fun getAllCachedRatesFlow(): Flow<List<CachedRateEntity>>

    @Query("SELECT * FROM cached_currency_rates")
    abstract suspend fun getAllCachedRates(): List<CachedRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRates(rates: List<CachedRateEntity>)

    @Query("SELECT * FROM currency_workspace_items ORDER BY orderIndex ASC")
    abstract fun getWorkspaceCurrencies(): Flow<List<WorkspaceCurrencyEntity>>

    @Query("SELECT * FROM currency_workspace_items ORDER BY orderIndex ASC")
    abstract suspend fun getWorkspaceCurrenciesList(): List<WorkspaceCurrencyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWorkspaceItem(item: WorkspaceCurrencyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWorkspaceItems(items: List<WorkspaceCurrencyEntity>)

    @Query("DELETE FROM currency_workspace_items")
    abstract suspend fun clearWorkspaceItems()

    /**
     * Atomically replaces the entire workspace: deletes all existing items,
     * then inserts the new list. This prevents phantom currencies from
     * surviving a reorder or remove operation.
     */
    @Transaction
    open suspend fun replaceWorkspaceItems(items: List<WorkspaceCurrencyEntity>) {
        clearWorkspaceItems()
        insertWorkspaceItems(items)
    }

    @Query("DELETE FROM currency_workspace_items WHERE currencyCode = :code")
    abstract suspend fun removeWorkspaceItem(code: String)

    @Query("SELECT * FROM custom_rate_overrides")
    abstract fun getCustomOverrides(): Flow<List<CustomRateOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun setCustomOverride(override: CustomRateOverrideEntity)

    @Query("DELETE FROM custom_rate_overrides WHERE currencyCode = :code")
    abstract suspend fun removeCustomOverride(code: String)

    @Query("DELETE FROM cached_currency_rates")
    abstract suspend fun clearCachedRates()
}

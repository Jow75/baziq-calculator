package com.omnicalc.platform.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.omnicalc.platform.core.database.daos.BackupDao
import com.omnicalc.platform.core.database.daos.CurrencyDao
import com.omnicalc.platform.core.database.daos.FavoriteDao
import com.omnicalc.platform.core.database.daos.HistoryDao
import com.omnicalc.platform.core.database.entities.*

@Database(
    entities = [
        CalculationHistoryEntity::class,
        FavoriteToolEntity::class,
        CachedRateEntity::class,
        WorkspaceCurrencyEntity::class,
        CustomRateOverrideEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OmniCalcDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun backupDao(): BackupDao

    companion object {
        @Volatile
        private var INSTANCE: OmniCalcDatabase? = null

        fun getInstance(context: Context): OmniCalcDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniCalcDatabase::class.java,
                    "omnicalc_local.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

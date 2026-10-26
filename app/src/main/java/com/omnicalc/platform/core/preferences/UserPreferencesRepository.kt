package com.omnicalc.platform.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.omnicalc.platform.core.backup.BackupPreferences
import com.omnicalc.platform.core.math.AngleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "omnicalc_user_preferences")

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK
}

data class UserPreferences(
    val theme: AppTheme = AppTheme.SYSTEM,
    val decimalPrecision: Int = 4,
    val useGroupingSeparators: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val angleMode: AngleMode = AngleMode.DEGREES,
    val defaultCurrency: String = "USD",
    val soundEnabled: Boolean = false
)

interface PreferencesRepository {
    val userPreferencesFlow: Flow<UserPreferences>
    suspend fun setTheme(theme: AppTheme)
    suspend fun setDecimalPrecision(precision: Int)
    suspend fun setGrouping(enabled: Boolean)
    suspend fun setHapticFeedback(enabled: Boolean)
    suspend fun setAngleMode(mode: AngleMode)
    suspend fun setDefaultCurrency(currencyCode: String)
}

/**
 * Settings for choosing the base currency from the current country. Only a country code is
 * stored (the last one whose currency was applied), never a position.
 */
data class LocalCurrencySettings(
    /** "Automatically use my current country's currency". Off until the user opts in. */
    val autoLocalCurrency: Boolean = false,
    /** The one-time explanation card was answered (either way), so it is not shown again. */
    val locationPromptAnswered: Boolean = false,
    val lastAppliedCountry: String? = null,
    /** The converter's base (source) currency, restored when the app starts again. */
    val baseCurrency: String? = null
)

interface LocalCurrencyPreferences {
    val localCurrencySettingsFlow: Flow<LocalCurrencySettings>
    suspend fun setAutoLocalCurrency(enabled: Boolean)
    suspend fun setLocationPromptAnswered()
    suspend fun setLastAppliedCountry(countryCode: String)
    suspend fun setBaseCurrency(currencyCode: String)
}

class UserPreferencesRepository(private val context: Context) : PreferencesRepository, LocalCurrencyPreferences {
    private object Keys {
        val THEME = stringPreferencesKey("app_theme")
        val DECIMAL_PRECISION = intPreferencesKey("decimal_precision")
        val USE_GROUPING = booleanPreferencesKey("use_grouping")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val ANGLE_MODE = stringPreferencesKey("angle_mode")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val AUTO_LOCAL_CURRENCY = booleanPreferencesKey("auto_local_currency")
        val LOCATION_PROMPT_ANSWERED = booleanPreferencesKey("location_prompt_answered")
        val LAST_APPLIED_COUNTRY = stringPreferencesKey("last_applied_country")
        val BASE_CURRENCY = stringPreferencesKey("base_currency")
    }

    override val localCurrencySettingsFlow: Flow<LocalCurrencySettings> = context.dataStore.data.map { prefs ->
        LocalCurrencySettings(
            autoLocalCurrency = prefs[Keys.AUTO_LOCAL_CURRENCY] ?: false,
            locationPromptAnswered = prefs[Keys.LOCATION_PROMPT_ANSWERED] ?: false,
            lastAppliedCountry = prefs[Keys.LAST_APPLIED_COUNTRY],
            baseCurrency = prefs[Keys.BASE_CURRENCY]
        )
    }

    override suspend fun setAutoLocalCurrency(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_LOCAL_CURRENCY] = enabled }
    }

    override suspend fun setLocationPromptAnswered() {
        context.dataStore.edit { it[Keys.LOCATION_PROMPT_ANSWERED] = true }
    }

    override suspend fun setLastAppliedCountry(countryCode: String) {
        context.dataStore.edit { it[Keys.LAST_APPLIED_COUNTRY] = countryCode }
    }

    override suspend fun setBaseCurrency(currencyCode: String) {
        context.dataStore.edit { it[Keys.BASE_CURRENCY] = currencyCode }
    }

    override val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME] ?: AppTheme.SYSTEM.name
        val theme = try { AppTheme.valueOf(themeStr) } catch (_: Exception) { AppTheme.SYSTEM }

        val angleModeStr = prefs[Keys.ANGLE_MODE] ?: AngleMode.DEGREES.name
        val angleMode = try { AngleMode.valueOf(angleModeStr) } catch (_: Exception) { AngleMode.DEGREES }

        UserPreferences(
            theme = theme,
            decimalPrecision = prefs[Keys.DECIMAL_PRECISION] ?: 4,
            useGroupingSeparators = prefs[Keys.USE_GROUPING] ?: true,
            hapticFeedbackEnabled = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            angleMode = angleMode,
            defaultCurrency = prefs[Keys.DEFAULT_CURRENCY] ?: "USD",
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: false
        )
    }

    override suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    override suspend fun setDecimalPrecision(precision: Int) {
        context.dataStore.edit { it[Keys.DECIMAL_PRECISION] = precision }
    }

    override suspend fun setGrouping(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_GROUPING] = enabled }
    }

    override suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    override suspend fun setAngleMode(mode: AngleMode) {
        context.dataStore.edit { it[Keys.ANGLE_MODE] = mode.name }
    }

    override suspend fun setDefaultCurrency(currencyCode: String) {
        context.dataStore.edit { it[Keys.DEFAULT_CURRENCY] = currencyCode }
    }

    /** The settings the user has set, for the backup file. */
    suspend fun backupPreferences(): BackupPreferences {
        val prefs = context.dataStore.data.first()
        return BackupPreferences(
            theme = prefs[Keys.THEME],
            decimalPrecision = prefs[Keys.DECIMAL_PRECISION],
            useGroupingSeparators = prefs[Keys.USE_GROUPING],
            hapticFeedbackEnabled = prefs[Keys.HAPTIC_FEEDBACK],
            angleMode = prefs[Keys.ANGLE_MODE],
            autoLocalCurrency = prefs[Keys.AUTO_LOCAL_CURRENCY],
            locationPromptAnswered = prefs[Keys.LOCATION_PROMPT_ANSWERED],
            lastAppliedCountry = prefs[Keys.LAST_APPLIED_COUNTRY],
            baseCurrency = prefs[Keys.BASE_CURRENCY]
        )
    }

    /** Applies the settings found in a validated backup; missing ones are left as they are. */
    suspend fun restoreBackupPreferences(backup: BackupPreferences) {
        context.dataStore.edit { prefs ->
            backup.theme?.let { prefs[Keys.THEME] = it }
            backup.decimalPrecision?.let { prefs[Keys.DECIMAL_PRECISION] = it }
            backup.useGroupingSeparators?.let { prefs[Keys.USE_GROUPING] = it }
            backup.hapticFeedbackEnabled?.let { prefs[Keys.HAPTIC_FEEDBACK] = it }
            backup.angleMode?.let { prefs[Keys.ANGLE_MODE] = it }
            backup.autoLocalCurrency?.let { prefs[Keys.AUTO_LOCAL_CURRENCY] = it }
            backup.locationPromptAnswered?.let { prefs[Keys.LOCATION_PROMPT_ANSWERED] = it }
            backup.lastAppliedCountry?.let { prefs[Keys.LAST_APPLIED_COUNTRY] = it.uppercase() }
            backup.baseCurrency?.let { prefs[Keys.BASE_CURRENCY] = it.uppercase() }
        }
    }
}

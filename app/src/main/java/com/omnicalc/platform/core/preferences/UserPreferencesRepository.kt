package com.omnicalc.platform.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.omnicalc.platform.core.math.AngleMode
import kotlinx.coroutines.flow.Flow
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

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("app_theme")
        val DECIMAL_PRECISION = intPreferencesKey("decimal_precision")
        val USE_GROUPING = booleanPreferencesKey("use_grouping")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val ANGLE_MODE = stringPreferencesKey("angle_mode")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
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

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setDecimalPrecision(precision: Int) {
        context.dataStore.edit { it[Keys.DECIMAL_PRECISION] = precision }
    }

    suspend fun setGrouping(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_GROUPING] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setAngleMode(mode: AngleMode) {
        context.dataStore.edit { it[Keys.ANGLE_MODE] = mode.name }
    }

    suspend fun setDefaultCurrency(currencyCode: String) {
        context.dataStore.edit { it[Keys.DEFAULT_CURRENCY] = currencyCode }
    }
}

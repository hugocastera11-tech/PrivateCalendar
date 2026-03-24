package com.example.privatecalendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        private val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        private val NOTIFICATION_LEAD_TIME = intPreferencesKey("notification_lead_time")
        private val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
        private val SHOW_HOLIDAYS = booleanPreferencesKey("show_holidays")
        private val HOLIDAY_COUNTRY_CODE = stringPreferencesKey("holiday_country_code")
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_DARK_MODE] ?: false
    }

    val notificationLeadTime: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATION_LEAD_TIME] ?: 30
    }

    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_BIOMETRIC_ENABLED] ?: false
    }

    val showHolidays: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_HOLIDAYS] ?: true
    }

    val holidayCountryCode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[HOLIDAY_COUNTRY_CODE] ?: Locale.getDefault().country
    }

    suspend fun setDarkMode(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_DARK_MODE] = isDark
        }
    }

    suspend fun setNotificationLeadTime(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_LEAD_TIME] = minutes
        }
    }

    suspend fun setBiometricEnabled(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_BIOMETRIC_ENABLED] = isEnabled
        }
    }

    suspend fun setShowHolidays(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_HOLIDAYS] = show
        }
    }

    suspend fun setHolidayCountryCode(code: String) {
        context.dataStore.edit { preferences ->
            preferences[HOLIDAY_COUNTRY_CODE] = code
        }
    }
}

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
        private val LANGUAGE_CODE = stringPreferencesKey("language_code")
        private val THEME_NAME = stringPreferencesKey("theme_name")
        private val ALL_DAY_NOTIFICATION_HOUR = intPreferencesKey("all_day_notification_hour")
        private val ALL_DAY_NOTIFICATION_DAY_BEFORE = booleanPreferencesKey("all_day_notification_day_before")
        private val DEFAULT_VIEW_MODE = stringPreferencesKey("default_view_mode")
    }

    val defaultViewMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_VIEW_MODE] ?: "MONTH"
    }

    suspend fun setDefaultViewMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_VIEW_MODE] = mode
        }
    }

    val allDayNotificationHour: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[ALL_DAY_NOTIFICATION_HOUR] ?: 9
    }

    val allDayNotificationDayBefore: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ALL_DAY_NOTIFICATION_DAY_BEFORE] ?: false
    }

    suspend fun setAllDayNotificationHour(hour: Int) {
        context.dataStore.edit { preferences ->
            preferences[ALL_DAY_NOTIFICATION_HOUR] = hour
        }
    }

    suspend fun setAllDayNotificationDayBefore(dayBefore: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ALL_DAY_NOTIFICATION_DAY_BEFORE] = dayBefore
        }
    }

    val themeName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_NAME] ?: "DEFAULT"
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_DARK_MODE] ?: false
    }

    val notificationLeadTime: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATION_LEAD_TIME] ?: 15
    }

    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_BIOMETRIC_ENABLED] ?: false
    }

    val showHolidays: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_HOLIDAYS] ?: true
    }

    val holidayCountryCode: Flow<String> = context.dataStore.data.map { preferences ->
        val saved = preferences[HOLIDAY_COUNTRY_CODE]
        if (saved != null && saved.isNotBlank()) return@map saved
        
        val deviceCountry = Locale.getDefault().country.uppercase()
        if (deviceCountry.isNotBlank() && HolidayProvider.SUPPORTED_COUNTRIES.containsKey(deviceCountry)) {
            deviceCountry
        } else {
            "ES"
        }
    }

    val languageCode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE_CODE] ?: "es"
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

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_CODE] = code
        }
    }

    suspend fun setThemeName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_NAME] = name
        }
    }
}

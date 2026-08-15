package com.russellwhite.moodsampler.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.russellwhite.moodsampler.scheduling.SamplingConfig
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val windowsPerDay: Int = 6,
    val dayStartMinutes: Int = 9 * 60,
    val dayEndMinutes: Int = 22 * 60,
    val expiryMinutes: Int = 20,
    val onboardingDone: Boolean = false,
) {
    fun toSamplingConfig() = SamplingConfig(
        dayStart = LocalTime.ofSecondOfDay(dayStartMinutes * 60L),
        dayEnd = LocalTime.ofSecondOfDay(dayEndMinutes * 60L),
        windowsPerDay = windowsPerDay,
        expiryMinutes = expiryMinutes,
    )
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val WINDOWS_PER_DAY = intPreferencesKey("windows_per_day")
        val DAY_START_MINUTES = intPreferencesKey("day_start_minutes")
        val DAY_END_MINUTES = intPreferencesKey("day_end_minutes")
        val EXPIRY_MINUTES = intPreferencesKey("expiry_minutes")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            windowsPerDay = prefs[Keys.WINDOWS_PER_DAY] ?: defaults.windowsPerDay,
            dayStartMinutes = prefs[Keys.DAY_START_MINUTES] ?: defaults.dayStartMinutes,
            dayEndMinutes = prefs[Keys.DAY_END_MINUTES] ?: defaults.dayEndMinutes,
            expiryMinutes = prefs[Keys.EXPIRY_MINUTES] ?: defaults.expiryMinutes,
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: defaults.onboardingDone,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setWindowsPerDay(value: Int) =
        context.dataStore.edit { it[Keys.WINDOWS_PER_DAY] = value.coerceIn(2, 12) }

    suspend fun setDayStartMinutes(value: Int) =
        context.dataStore.edit { it[Keys.DAY_START_MINUTES] = value.coerceIn(0, 24 * 60 - 1) }

    suspend fun setDayEndMinutes(value: Int) =
        context.dataStore.edit { it[Keys.DAY_END_MINUTES] = value.coerceIn(0, 24 * 60 - 1) }

    suspend fun setExpiryMinutes(value: Int) =
        context.dataStore.edit { it[Keys.EXPIRY_MINUTES] = value.coerceIn(5, 120) }

    suspend fun setOnboardingDone() =
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
}

package com.russellwhite.moodsampler.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.russellwhite.moodsampler.data.backup.SettingsDto
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
    /** Take one location fix while answering a prompt (needs the location permission). */
    val autoLocation: Boolean = true,
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
        val SCHEDULE_ZONE_ID = stringPreferencesKey("schedule_zone_id")
        val AUTO_LOCATION = booleanPreferencesKey("auto_location")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            windowsPerDay = prefs[Keys.WINDOWS_PER_DAY] ?: defaults.windowsPerDay,
            dayStartMinutes = prefs[Keys.DAY_START_MINUTES] ?: defaults.dayStartMinutes,
            dayEndMinutes = prefs[Keys.DAY_END_MINUTES] ?: defaults.dayEndMinutes,
            expiryMinutes = prefs[Keys.EXPIRY_MINUTES] ?: defaults.expiryMinutes,
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: defaults.onboardingDone,
            autoLocation = prefs[Keys.AUTO_LOCATION] ?: defaults.autoLocation,
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

    suspend fun setAutoLocation(enabled: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_LOCATION] = enabled }

    suspend fun setOnboardingDone() =
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }

    /** Restore sampling preferences from a backup (clamped like the individual setters). */
    suspend fun replaceAll(dto: SettingsDto) =
        context.dataStore.edit {
            it[Keys.WINDOWS_PER_DAY] = dto.windowsPerDay.coerceIn(2, 12)
            it[Keys.DAY_START_MINUTES] = dto.dayStartMinutes.coerceIn(0, 24 * 60 - 1)
            it[Keys.DAY_END_MINUTES] = dto.dayEndMinutes.coerceIn(0, 24 * 60 - 1)
            it[Keys.EXPIRY_MINUTES] = dto.expiryMinutes.coerceIn(5, 120)
            it[Keys.AUTO_LOCATION] = dto.autoLocation
        }

    /**
     * Zone ID the upcoming schedule was generated in, or null before the first
     * generation. Internal bookkeeping for [com.russellwhite.moodsampler.scheduling.PromptScheduler],
     * which regenerates when the phone's zone no longer matches.
     */
    suspend fun scheduleZoneId(): String? = context.dataStore.data.first()[Keys.SCHEDULE_ZONE_ID]

    suspend fun setScheduleZoneId(zoneId: String) =
        context.dataStore.edit { it[Keys.SCHEDULE_ZONE_ID] = zoneId }
}

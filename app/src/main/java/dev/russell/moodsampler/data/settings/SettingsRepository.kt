package dev.russell.moodsampler.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.russell.moodsampler.data.backup.SettingsDto
import dev.russell.moodsampler.data.claude.ClaudeModel
import dev.russell.moodsampler.scheduling.SamplingConfig
import java.time.LocalTime
import java.util.UUID
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
    /** Scan for nearby Bluetooth devices while answering to suggest company (needs the nearby-devices permission). */
    val autoCompany: Boolean = true,
    /** [ClaudeModel.id] used to fill a capture from the narration. */
    val claudeModel: String = ClaudeModel.DEFAULT.id,
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
        val AUTO_COMPANY = booleanPreferencesKey("auto_company")
        val DEVICE_SALT = stringPreferencesKey("device_salt")
        val CLAUDE_MODEL = stringPreferencesKey("claude_model")
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
            autoCompany = prefs[Keys.AUTO_COMPANY] ?: defaults.autoCompany,
            claudeModel = prefs[Keys.CLAUDE_MODEL] ?: defaults.claudeModel,
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

    suspend fun setAutoCompany(enabled: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_COMPANY] = enabled }

    suspend fun setClaudeModel(model: ClaudeModel) =
        context.dataStore.edit { it[Keys.CLAUDE_MODEL] = model.id }

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
            it[Keys.AUTO_COMPANY] = dto.autoCompany
            // Restored device hashes only match future scans under the salt they were made with.
            dto.deviceSalt?.let { salt -> it[Keys.DEVICE_SALT] = salt }
            dto.claudeModel?.let { id -> it[Keys.CLAUDE_MODEL] = ClaudeModel.parse(id).id }
        }

    /** Salt for hashing nearby devices, or null if no scan has ever needed one. */
    suspend fun deviceSaltOrNull(): String? = context.dataStore.data.first()[Keys.DEVICE_SALT]

    /** Salt for hashing nearby devices; created on first use and kept for the life of the install. */
    suspend fun deviceSalt(): String {
        deviceSaltOrNull()?.let { return it }
        var salt = ""
        context.dataStore.edit { prefs ->
            salt = prefs[Keys.DEVICE_SALT] ?: UUID.randomUUID().toString().also { prefs[Keys.DEVICE_SALT] = it }
        }
        return salt
    }

    /**
     * Zone ID the upcoming schedule was generated in, or null before the first
     * generation. Internal bookkeeping for [dev.russell.moodsampler.scheduling.PromptScheduler],
     * which regenerates when the phone's zone no longer matches.
     */
    suspend fun scheduleZoneId(): String? = context.dataStore.data.first()[Keys.SCHEDULE_ZONE_ID]

    suspend fun setScheduleZoneId(zoneId: String) =
        context.dataStore.edit { it[Keys.SCHEDULE_ZONE_ID] = zoneId }
}

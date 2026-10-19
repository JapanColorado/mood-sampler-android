package dev.russell.moodsampler.data.settings

import dev.russell.moodsampler.data.db.AppDatabase

/**
 * Decides whether first-run onboarding still has to happen. Installs that
 * already collected data (upgrades from before onboarding existed) are
 * grandfathered in and the flag is persisted so the check stays cheap.
 */
class OnboardingGate(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    suspend fun isDone(): Boolean {
        if (settings.current().onboardingDone) return true
        val hasHistory = db.sampleDao().count() > 0 || db.scheduledPromptDao().count() > 0
        if (hasHistory) settings.setOnboardingDone()
        return hasHistory
    }
}

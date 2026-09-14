package com.russellwhite.moodsampler

import android.content.Context
import com.russellwhite.moodsampler.data.CategoryRepository
import com.russellwhite.moodsampler.data.CsvExporter
import com.russellwhite.moodsampler.data.SampleRepository
import com.russellwhite.moodsampler.data.backup.BackupManager
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.location.LocationProvider
import com.russellwhite.moodsampler.data.settings.OnboardingGate
import com.russellwhite.moodsampler.data.settings.SettingsRepository
import com.russellwhite.moodsampler.scheduling.DeviceClock
import com.russellwhite.moodsampler.scheduling.PromptNotifier
import com.russellwhite.moodsampler.scheduling.PromptScheduler
import com.russellwhite.moodsampler.scheduling.ScheduleGenerator

/** Manual DI container; created once in [MoodSamplerApp]. */
class AppGraph(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val settingsRepository = SettingsRepository(context)
    val sampleRepository = SampleRepository(database)
    val categoryRepository = CategoryRepository(database)
    val csvExporter = CsvExporter(context, database)
    val locationProvider = LocationProvider(context)
    val promptNotifier = PromptNotifier(context)
    val onboardingGate = OnboardingGate(database, settingsRepository)
    val promptScheduler = PromptScheduler(
        context = context,
        db = database,
        settings = settingsRepository,
        generator = ScheduleGenerator(),
        notifier = promptNotifier,
        clock = DeviceClock,
        onboardingGate = onboardingGate,
    )
    val backupManager = BackupManager(
        context = context,
        db = database,
        settings = settingsRepository,
        scheduler = promptScheduler,
        appVersionName = BuildConfig.VERSION_NAME,
    )
}

val Context.appGraph: AppGraph
    get() = (applicationContext as MoodSamplerApp).graph

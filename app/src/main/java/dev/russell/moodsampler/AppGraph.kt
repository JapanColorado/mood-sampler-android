package dev.russell.moodsampler

import android.content.Context
import dev.russell.moodsampler.data.CategoryRepository
import dev.russell.moodsampler.data.CsvExporter
import dev.russell.moodsampler.data.PhotoStore
import dev.russell.moodsampler.data.SampleRepository
import dev.russell.moodsampler.data.backup.BackupManager
import dev.russell.moodsampler.data.bluetooth.BluetoothScanner
import dev.russell.moodsampler.data.claude.CaptureAssistant
import dev.russell.moodsampler.data.claude.ClaudeKeyStore
import dev.russell.moodsampler.data.db.AppDatabase
import dev.russell.moodsampler.data.location.LocationProvider
import dev.russell.moodsampler.data.settings.OnboardingGate
import dev.russell.moodsampler.data.settings.SettingsRepository
import dev.russell.moodsampler.scheduling.DeviceClock
import dev.russell.moodsampler.scheduling.PromptNotifier
import dev.russell.moodsampler.scheduling.PromptScheduler
import dev.russell.moodsampler.scheduling.ScheduleGenerator

/** Manual DI container; created once in [MoodSamplerApp]. */
class AppGraph(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val settingsRepository = SettingsRepository(context)
    val photoStore = PhotoStore(context)
    val sampleRepository = SampleRepository(database, photoStore)
    val categoryRepository = CategoryRepository(database)
    val claudeKeyStore = ClaudeKeyStore(context)
    val captureAssistant = CaptureAssistant(
        db = database,
        categories = categoryRepository,
        keyStore = claudeKeyStore,
        settings = settingsRepository,
        photos = photoStore,
    )
    val csvExporter = CsvExporter(context, database)
    val locationProvider = LocationProvider(context)
    val bluetoothScanner = BluetoothScanner(context)
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
        photos = photoStore,
        appVersionName = BuildConfig.VERSION_NAME,
    )
}

val Context.appGraph: AppGraph
    get() = (applicationContext as MoodSamplerApp).graph

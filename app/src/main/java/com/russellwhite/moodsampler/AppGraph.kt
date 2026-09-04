package com.russellwhite.moodsampler

import android.content.Context
import com.russellwhite.moodsampler.data.CsvExporter
import com.russellwhite.moodsampler.data.SampleRepository
import com.russellwhite.moodsampler.data.db.AppDatabase
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
    val csvExporter = CsvExporter(context, database)
    val promptNotifier = PromptNotifier(context)
    val promptScheduler = PromptScheduler(
        context = context,
        db = database,
        settings = settingsRepository,
        generator = ScheduleGenerator(),
        notifier = promptNotifier,
        clock = DeviceClock,
    )
}

val Context.appGraph: AppGraph
    get() = (applicationContext as MoodSamplerApp).graph

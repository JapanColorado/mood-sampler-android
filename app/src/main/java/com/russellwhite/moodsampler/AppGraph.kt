package com.russellwhite.moodsampler

import android.content.Context
import com.russellwhite.moodsampler.data.SampleRepository
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.settings.SettingsRepository
import com.russellwhite.moodsampler.scheduling.PromptNotifier
import com.russellwhite.moodsampler.scheduling.PromptScheduler
import com.russellwhite.moodsampler.scheduling.ScheduleGenerator
import java.time.Clock

/** Manual DI container; created once in [MoodSamplerApp]. */
class AppGraph(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val settingsRepository = SettingsRepository(context)
    val sampleRepository = SampleRepository(database)
    val promptNotifier = PromptNotifier(context)
    val promptScheduler = PromptScheduler(
        context = context,
        db = database,
        settings = settingsRepository,
        generator = ScheduleGenerator(),
        notifier = promptNotifier,
        clock = Clock.systemDefaultZone(),
    )
}

val Context.appGraph: AppGraph
    get() = (applicationContext as MoodSamplerApp).graph

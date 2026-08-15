package com.russellwhite.moodsampler

import android.content.Context
import com.russellwhite.moodsampler.data.SampleRepository
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.settings.SettingsRepository

/** Manual DI container; created once in [MoodSamplerApp]. */
class AppGraph(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val settingsRepository = SettingsRepository(context)
    val sampleRepository = SampleRepository(database)
}

val Context.appGraph: AppGraph
    get() = (applicationContext as MoodSamplerApp).graph

package com.russellwhite.moodsampler

import android.app.Application

class MoodSamplerApp : Application() {
    // AppGraph (manual DI container) is added in the data-layer stage.
    override fun onCreate() {
        super.onCreate()
    }
}

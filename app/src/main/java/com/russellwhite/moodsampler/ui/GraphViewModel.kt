package com.russellwhite.moodsampler.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.russellwhite.moodsampler.AppGraph
import com.russellwhite.moodsampler.MoodSamplerApp

/** Factory helper wiring ViewModels to the manual DI graph. */
inline fun <reified VM : ViewModel> graphViewModelFactory(
    crossinline create: (AppGraph) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) as MoodSamplerApp
        create(app.graph)
    }
}

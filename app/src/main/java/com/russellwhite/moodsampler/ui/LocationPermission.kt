package com.russellwhite.moodsampler.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.russellwhite.moodsampler.appGraph
import com.russellwhite.moodsampler.data.location.LocationProvider

/** Whether the location permission is granted, plus a way to ask for it. */
class LocationPermissionState(val granted: Boolean, val request: () -> Unit)

/**
 * Tracks the location permission, re-checking on resume (the user may change it
 * in system settings) and after the request dialog closes.
 */
@Composable
fun rememberLocationPermission(): LocationPermissionState {
    val provider = LocalContext.current.appGraph.locationProvider
    var granted by remember { mutableStateOf(provider.hasPermission()) }
    LifecycleResumeEffect(Unit) {
        granted = provider.hasPermission()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = provider.hasPermission()
    }
    return LocationPermissionState(granted) { launcher.launch(LocationProvider.PERMISSIONS) }
}

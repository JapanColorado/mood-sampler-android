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
import com.russellwhite.moodsampler.data.bluetooth.BluetoothScanner
import com.russellwhite.moodsampler.data.location.LocationProvider

/** Whether a runtime permission is granted, plus a way to ask for it. */
class PermissionState(val granted: Boolean, val request: () -> Unit)

@Composable
fun rememberLocationPermission(): PermissionState {
    val provider = LocalContext.current.appGraph.locationProvider
    return rememberPermission(LocationProvider.PERMISSIONS, provider::hasPermission)
}

/** "Nearby devices" (BLUETOOTH_SCAN), for company suggestions. */
@Composable
fun rememberBluetoothPermission(): PermissionState {
    val scanner = LocalContext.current.appGraph.bluetoothScanner
    return rememberPermission(BluetoothScanner.PERMISSIONS, scanner::hasPermission)
}

/**
 * Tracks a permission, re-checking on resume (the user may change it in system
 * settings) and after the request dialog closes.
 */
@Composable
private fun rememberPermission(permissions: Array<String>, isGranted: () -> Boolean): PermissionState {
    var granted by remember { mutableStateOf(isGranted()) }
    LifecycleResumeEffect(Unit) {
        granted = isGranted()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = isGranted()
    }
    return PermissionState(granted) { launcher.launch(permissions) }
}

package dev.russell.moodsampler.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.russell.moodsampler.appGraph
import dev.russell.moodsampler.data.bluetooth.BluetoothScanner
import dev.russell.moodsampler.data.location.LocationProvider

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

/** Microphone (RECORD_AUDIO), for dictating a capture's narration. */
@Composable
fun rememberMicrophonePermission(): PermissionState {
    val context = LocalContext.current
    return rememberPermission(arrayOf(Manifest.permission.RECORD_AUDIO)) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
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

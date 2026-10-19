package dev.russell.moodsampler.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One location fix, as stored on a sample. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float?,
    val atEpochMs: Long,
)

/**
 * Single-shot foreground location via the platform [LocationManager] (no Play
 * Services). Only ever called while the capture screen is open, so the
 * foreground permission is all that's needed.
 */
class LocationProvider(private val context: Context) {

    private val manager: LocationManager? get() = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean = PERMISSIONS.any(::granted)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Null when the permission is missing, no provider answers in time, or location is off.
     * [timeoutMs] bounds the whole call: enabled providers are tried in order until one answers.
     */
    suspend fun currentFix(timeoutMs: Long = DEFAULT_TIMEOUT_MS): LocationFix? {
        if (!hasPermission()) return null
        val lm = manager ?: return null
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        return withTimeoutOrNull(timeoutMs) {
            providers.firstNotNullOfOrNull { request(lm, it) }?.toFix()
        }
    }

    private suspend fun request(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
            } catch (e: IllegalArgumentException) {
                // Unknown provider on this device.
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun Location.toFix() = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyM = if (hasAccuracy()) accuracy else null,
        atEpochMs = time,
    )

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L

        /** Either one is enough; both are requested so the user can choose precision. */
        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

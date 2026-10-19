package dev.russell.moodsampler.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Short foreground BLE scan of what's advertising nearby, for company suggestions.
 * Only ever run while the capture screen is open. The permission is declared
 * `neverForLocation`, so no location permission is involved (and Android hides
 * some beacon-type adverts from such scans, which is fine here).
 */
class BluetoothScanner(private val context: Context) {

    private val adapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun hasPermission(): Boolean =
        PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /**
     * Devices heard during [durationMs], strongest signal per address. Null when
     * the permission is missing, Bluetooth is off, or the scan fails to start.
     */
    @SuppressLint("MissingPermission")  // Guarded by hasPermission(); SecurityException caught below.
    suspend fun scan(durationMs: Long = DEFAULT_SCAN_MS): List<NearbyDevice>? {
        if (!hasPermission()) return null
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return null
        val seen = ConcurrentHashMap<String, NearbyDevice>()
        val failed = CompletableDeferred<Int>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = record(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::record)
            override fun onScanFailed(errorCode: Int) {
                failed.complete(errorCode)
            }

            private fun record(result: ScanResult) {
                val device = result.toNearbyDevice()
                seen.merge(device.address, device) { old, new -> if (new.rssi > old.rssi) new.copy(name = new.name ?: old.name) else old }
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, callback)
        } catch (e: SecurityException) {
            return null
        }
        val error = try {
            withTimeoutOrNull(durationMs) { failed.await() }
        } finally {
            runCatching { scanner.stopScan(callback) }
        }
        return if (error != null) null else seen.values.toList()
    }

    private fun ScanResult.toNearbyDevice(): NearbyDevice {
        val address = device.address
        return NearbyDevice(
            address = address,
            // From the advert itself; BluetoothDevice.getName() would need BLUETOOTH_CONNECT.
            name = scanRecord?.deviceName?.trim()?.takeIf { it.isNotEmpty() },
            rssi = rssi,
            rotatingAddress = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
                device.addressType == BluetoothDevice.ADDRESS_TYPE_RANDOM &&
                !DeviceFingerprint.isStaticRandom(address),
        )
    }

    companion object {
        const val DEFAULT_SCAN_MS = 4_000L

        val PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN)
    }
}

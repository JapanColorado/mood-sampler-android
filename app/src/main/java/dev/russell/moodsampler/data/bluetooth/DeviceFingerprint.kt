package dev.russell.moodsampler.data.bluetooth

import java.security.MessageDigest

/** One advertiser heard during a scan. */
data class NearbyDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    /**
     * A private address the device rotates every ~15 min (phones do this), so it
     * can never match a past answer. Only detectable on Android 15+; false before.
     */
    val rotatingAddress: Boolean = false,
)

/**
 * Turns a scan into the salted hashes stored on a sample. Each device contributes
 * up to two tokens — its address (unless it rotates) and its advertised name —
 * so a watch that rotates its address but keeps its name still matches. Only
 * reasonably close devices count: "with" means in the room, not down the street.
 * Pure Kotlin so it's unit-testable.
 */
object DeviceFingerprint {
    /** About a room's distance for typical BLE transmit power. */
    const val MIN_RSSI = -85

    /** Keeps busy places (a café full of phones) from flooding the table. */
    const val MAX_DEVICES = 40

    fun hashes(devices: List<NearbyDevice>, salt: String): Set<String> =
        devices
            .filter { it.rssi >= MIN_RSSI }
            .sortedByDescending { it.rssi }
            .take(MAX_DEVICES)
            .flatMap { listOfNotNull(if (it.rotatingAddress) null else "a:${it.address.uppercase()}", it.name?.let { n -> "n:$n" }) }
            .map { hash(salt, it) }
            .toSet()

    /** First 64 bits of SHA-256(salt, token), as hex. */
    fun hash(salt: String, token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$salt|$token".toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /** A random-type address whose top two bits are 11 is static (stable until the device reboots). */
    fun isStaticRandom(address: String): Boolean =
        address.take(2).toIntOrNull(16)?.let { it and 0xC0 == 0xC0 } ?: false
}

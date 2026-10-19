package com.russellwhite.moodsampler.data

import com.google.common.truth.Truth.assertThat
import com.russellwhite.moodsampler.data.bluetooth.DeviceFingerprint
import com.russellwhite.moodsampler.data.bluetooth.NearbyDevice
import org.junit.Test

class DeviceFingerprintTest {

    @Test
    fun hashesAreStablePerSaltAndHideTheToken() {
        val a = DeviceFingerprint.hash("salt", "a:AA:BB:CC:DD:EE:FF")
        assertThat(DeviceFingerprint.hash("salt", "a:AA:BB:CC:DD:EE:FF")).isEqualTo(a)
        assertThat(DeviceFingerprint.hash("other", "a:AA:BB:CC:DD:EE:FF")).isNotEqualTo(a)
        assertThat(a).matches("[0-9a-f]{16}")
    }

    @Test
    fun addressAndNameEachGiveAHash() {
        val hashes = DeviceFingerprint.hashes(listOf(NearbyDevice("aa:bb:cc:dd:ee:ff", "Watch", -60)), "s")
        assertThat(hashes).containsExactly(
            DeviceFingerprint.hash("s", "a:AA:BB:CC:DD:EE:FF"),
            DeviceFingerprint.hash("s", "n:Watch"),
        )
    }

    @Test
    fun rotatingAddressKeepsOnlyTheName() {
        val hashes = DeviceFingerprint.hashes(listOf(NearbyDevice("4A:00:00:00:00:01", "Watch", -60, rotatingAddress = true)), "s")
        assertThat(hashes).containsExactly(DeviceFingerprint.hash("s", "n:Watch"))
    }

    @Test
    fun farDevicesAreDroppedAndBusyScansCapped() {
        assertThat(DeviceFingerprint.hashes(listOf(NearbyDevice("00:00:00:00:00:01", null, -95)), "s")).isEmpty()
        val crowd = List(100) { NearbyDevice("00:00:00:00:00:%02X".format(it), null, -50 - it % 30) }
        assertThat(DeviceFingerprint.hashes(crowd, "s")).hasSize(DeviceFingerprint.MAX_DEVICES)
    }

    @Test
    fun staticRandomAddressesHaveTopBitsSet() {
        assertThat(DeviceFingerprint.isStaticRandom("C0:11:22:33:44:55")).isTrue()
        assertThat(DeviceFingerprint.isStaticRandom("4A:11:22:33:44:55")).isFalse()
    }
}

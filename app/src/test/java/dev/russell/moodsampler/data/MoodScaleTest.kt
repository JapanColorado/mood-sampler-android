package dev.russell.moodsampler.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoodScaleTest {
    @Test
    fun legacyEndsAndMiddleMapOntoTheScale() {
        assertThat(MoodScale.fromLegacy(-1f)).isEqualTo(0)
        assertThat(MoodScale.fromLegacy(0f)).isEqualTo(5)
        assertThat(MoodScale.fromLegacy(1f)).isEqualTo(10)
    }

    @Test
    fun legacyValuesSnapToTheNearestStep() {
        assertThat(MoodScale.fromLegacy(-0.95f)).isEqualTo(0)
        assertThat(MoodScale.fromLegacy(0.3f)).isEqualTo(7)
        assertThat(MoodScale.fromLegacy(-0.5f)).isEqualTo(3)
    }

    @Test
    fun legacyOutOfRangeIsClamped() {
        assertThat(MoodScale.fromLegacy(3f)).isEqualTo(10)
        assertThat(MoodScale.fromLegacy(-3f)).isEqualTo(0)
    }

    @Test
    fun v5StepsStretchOntoZeroToTen() {
        assertThat((1..10).map(MoodScale::fromV5)).containsExactly(0, 1, 2, 3, 4, 6, 7, 8, 9, 10).inOrder()
    }
}

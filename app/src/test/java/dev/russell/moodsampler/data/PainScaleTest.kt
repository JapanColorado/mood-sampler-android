package dev.russell.moodsampler.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PainScaleTest {
    @Test
    fun descriptorsCoverTheScale() {
        assertThat(PainScale.descriptor(0)).isEqualTo("None")
        assertThat(PainScale.descriptor(1)).isEqualTo("Mild")
        assertThat(PainScale.descriptor(3)).isEqualTo("Mild")
        assertThat(PainScale.descriptor(4)).isEqualTo("Moderate")
        assertThat(PainScale.descriptor(6)).isEqualTo("Moderate")
        assertThat(PainScale.descriptor(7)).isEqualTo("Severe")
        assertThat(PainScale.descriptor(10)).isEqualTo("Severe")
    }

    @Test
    fun outOfRangeIsClamped() {
        assertThat(PainScale.descriptor(-5)).isEqualTo("None")
        assertThat(PainScale.descriptor(42)).isEqualTo("Severe")
    }
}

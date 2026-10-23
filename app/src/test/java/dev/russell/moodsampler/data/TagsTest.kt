package dev.russell.moodsampler.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TagsTest {

    @Test
    fun normalizeTrimsLowercasesAndDeduplicates() {
        assertThat(Tags.normalize(listOf(" Coffee ", "#coffee", "Screen   Time", "", "   ")))
            .containsExactly("coffee", "screen time").inOrder()
    }

    @Test
    fun normalizeCapsLengthAndCount() {
        val long = "a".repeat(Tags.MAX_LENGTH + 10)
        assertThat(Tags.normalize(listOf(long)).single()).hasLength(Tags.MAX_LENGTH)
        assertThat(Tags.normalize((1..20).map { "tag$it" })).hasSize(Tags.MAX_PER_SAMPLE)
    }
}

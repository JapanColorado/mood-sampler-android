package dev.russell.moodsampler.data

import com.google.common.truth.Truth.assertThat
import dev.russell.moodsampler.data.bluetooth.CompanyMatcher
import dev.russell.moodsampler.data.db.DeviceTag
import org.junit.Test

class CompanyMatcherTest {

    private val alone = 1L
    private val partner = 2L
    private val friends = 3L

    /** One past answer: its company and the devices seen with it. */
    private fun answer(sampleId: Long, company: Long, vararg devices: String) =
        devices.map { DeviceTag(sampleId, company, it) }

    @Test
    fun emptyHistoryGivesNoSuggestion() {
        assertThat(CompanyMatcher.suggest(setOf("watch"), emptyList())).isNull()
    }

    @Test
    fun deviceSeenRepeatedlyWithOneCompanyMatches() {
        val tags = answer(1, partner, "watch", "buds") + answer(2, partner, "watch", "buds") +
            answer(3, alone, "buds") + answer(4, alone, "buds")
        assertThat(CompanyMatcher.suggest(setOf("watch", "buds"), tags)).isEqualTo(partner)
    }

    @Test
    fun deviceSeenOnceIsNotEnough() {
        val tags = answer(1, partner, "watch") + answer(2, alone, "buds") + answer(3, alone, "buds")
        assertThat(CompanyMatcher.suggest(setOf("watch"), tags)).isNull()
    }

    @Test
    fun ownDevicePresentEverywhereSaysNothing() {
        // "buds" follows the base rates exactly, so it carries no information.
        val tags = answer(1, alone, "buds") + answer(2, alone, "buds") + answer(3, alone, "buds") +
            answer(4, partner, "buds")
        assertThat(CompanyMatcher.suggest(setOf("buds"), tags)).isNull()
    }

    @Test
    fun ambiguousDeviceIsIgnored() {
        val tags = answer(1, partner, "car") + answer(2, friends, "car") + answer(3, alone, "x") + answer(4, alone, "x")
        assertThat(CompanyMatcher.suggest(setOf("car"), tags)).isNull()
    }

    @Test
    fun strongerEvidenceWins() {
        val tags = answer(1, partner, "watch") + answer(2, partner, "watch") + answer(3, partner, "watch") +
            answer(4, friends, "speaker") + answer(5, friends, "speaker") +
            answer(6, alone, "x") + answer(7, alone, "x") + answer(8, alone, "x")
        assertThat(CompanyMatcher.suggest(setOf("watch", "speaker"), tags)).isEqualTo(partner)
    }

    @Test
    fun nothingNearbyGivesNoSuggestion() {
        val tags = answer(1, partner, "watch") + answer(2, partner, "watch") + answer(3, alone, "x")
        assertThat(CompanyMatcher.suggest(emptySet(), tags)).isNull()
    }
}

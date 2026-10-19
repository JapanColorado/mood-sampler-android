package dev.russell.moodsampler.data

import com.google.common.truth.Truth.assertThat
import dev.russell.moodsampler.data.db.GeoTag
import dev.russell.moodsampler.data.location.LocationFix
import dev.russell.moodsampler.data.location.PlaceMatcher
import org.junit.Test

class PlaceMatcherTest {

    // ~1e-5 degrees of latitude ≈ 1.1 m
    private val home = 40.0 to -105.0
    private fun fix(lat: Double, lon: Double, accuracy: Float? = 10f) = LocationFix(lat, lon, accuracy, 0)
    private fun tag(id: Long, lat: Double, lon: Double) = GeoTag(id, lat, lon)

    @Test
    fun emptyHistoryGivesNoSuggestion() {
        assertThat(PlaceMatcher.suggest(fix(home.first, home.second), emptyList())).isNull()
    }

    @Test
    fun nearbyTagWithinRadiusMatches() {
        val tags = listOf(tag(1, home.first + 0.0005, home.second))  // ~55 m north
        assertThat(PlaceMatcher.suggest(fix(home.first, home.second), tags)).isEqualTo(1)
    }

    @Test
    fun farTagIsIgnored() {
        val tags = listOf(tag(1, home.first + 0.01, home.second))  // ~1.1 km
        assertThat(PlaceMatcher.suggest(fix(home.first, home.second), tags)).isNull()
    }

    @Test
    fun poorAccuracyWidensRadius() {
        val tags = listOf(tag(1, home.first + 0.003, home.second))  // ~330 m
        assertThat(PlaceMatcher.suggest(fix(home.first, home.second, accuracy = 10f), tags)).isNull()
        assertThat(PlaceMatcher.suggest(fix(home.first, home.second, accuracy = 400f), tags)).isEqualTo(1)
    }

    @Test
    fun majorityBeatsNearest() {
        val tags = listOf(
            tag(2, home.first + 0.0001, home.second),  // nearest, one vote
            tag(1, home.first + 0.0005, home.second),
            tag(1, home.first + 0.0006, home.second),
        )
        assertThat(PlaceMatcher.suggest(fix(home.first, home.second), tags)).isEqualTo(1)
    }

    @Test
    fun tieGoesToNearest() {
        val tags = listOf(
            tag(2, home.first + 0.0001, home.second),
            tag(1, home.first + 0.0005, home.second),
        )
        assertThat(PlaceMatcher.suggest(fix(home.first, home.second), tags)).isEqualTo(2)
    }

    @Test
    fun distanceIsRoughlyRight() {
        // One degree of latitude ≈ 111.2 km.
        val d = PlaceMatcher.distanceM(0.0, 0.0, 1.0, 0.0)
        assertThat(d).isWithin(500.0).of(111_195.0)
    }
}

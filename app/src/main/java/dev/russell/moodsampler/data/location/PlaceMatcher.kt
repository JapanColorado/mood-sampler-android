package dev.russell.moodsampler.data.location

import dev.russell.moodsampler.data.db.GeoTag
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Suggests the place for a fresh fix from past answers: every earlier answer that
 * carried coordinates and a place is a vote for that place if it lies within the
 * match radius (widened when the new fix is imprecise). Most votes wins; ties go
 * to the closest. Pure Kotlin so it's unit-testable.
 */
object PlaceMatcher {
    const val DEFAULT_RADIUS_M = 150.0

    fun suggest(fix: LocationFix, tags: List<GeoTag>, radiusM: Double = DEFAULT_RADIUS_M): Long? {
        val radius = maxOf(radiusM, fix.accuracyM?.toDouble() ?: 0.0)
        val hits = tags.mapNotNull { tag ->
            val d = distanceM(fix.latitude, fix.longitude, tag.latitude, tag.longitude)
            if (d <= radius) tag.locationId to d else null
        }
        return hits
            .groupBy({ (id, _) -> id }, { (_, d) -> d })
            .entries
            .maxWithOrNull(compareBy<Map.Entry<Long, List<Double>>> { it.value.size }.thenByDescending { it.value.min() })
            ?.key
    }

    /** Great-circle distance in metres (haversine). */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }
}

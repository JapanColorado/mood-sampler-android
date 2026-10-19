package com.russellwhite.moodsampler.debug

import com.russellwhite.moodsampler.AppGraph
import com.russellwhite.moodsampler.data.MoodScale
import com.russellwhite.moodsampler.data.SkipReason
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.db.SampleActivityCrossRef
import com.russellwhite.moodsampler.data.db.SampleDeviceEntity
import com.russellwhite.moodsampler.data.db.SampleEntity
import com.russellwhite.moodsampler.data.db.ScheduledPromptEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Debug-only: insert ~30 days of plausible answered/missed prompts so the
 * dashboard can be developed and demoed without waiting a month.
 */
suspend fun seedFakeHistory(graph: AppGraph, days: Int = 30, seed: Long = 7) {
    val random = Random(seed)
    val zone = ZoneId.systemDefault()
    val promptDao = graph.database.scheduledPromptDao()
    val sampleDao = graph.database.sampleDao()
    val categories = graph.database.categoryDao().getAll().filter { !it.isArchived }
    val activities = categories.filter { it.type == CategoryType.ACTIVITY }
    val locations = categories.filter { it.type == CategoryType.LOCATION }
    val socials = categories.filter { it.type == CategoryType.SOCIAL }

    val skipReasons = SkipReason.CAPTURE_CHOICES.map { it.first }
    // Fake device hashes: two per company option, your own earbuds everywhere, plus passers-by.
    // They never match a real scan; they just give sample_device realistic shape.
    val companyDevices = socials.associate { s -> s.id to List(2) { "seed%04x%02d".format(s.id, it) } }
    val ownEarbuds = "seedself000"

    val today = LocalDate.now()
    for (dayOffset in days downTo 1) {
        val date = today.minusDays(dayOffset.toLong())
        // Pain comes in episodes: some days are pain-free, others hover around a baseline.
        val painFreeDay = random.nextFloat() < 0.35f
        val painBaseline = random.nextInt(1, 7)
        repeat(6) { window ->
            val minute = 9 * 60 + window * 130 + random.nextInt(130)
            val fireAt = date.atTime(LocalTime.ofSecondOfDay(minute * 60L))
                .atZone(zone).toInstant().toEpochMilli()
            val answered = random.nextFloat() < 0.85f
            val promptId = promptDao.insert(
                ScheduledPromptEntity(
                    scheduledAtEpochMs = fireAt,
                    localDate = date.toString(),
                    windowIndex = window,
                    status = if (answered) PromptStatus.ANSWERED else PromptStatus.MISSED,
                    notifiedAtEpochMs = fireAt,
                    answeredAtEpochMs = if (answered) fireAt + random.nextLong(60_000, 600_000) else null,
                    skipReason = if (!answered && random.nextFloat() < 0.25f) skipReasons.random(random) else null,
                )
            )
            if (answered) {
                // Mood with a gentle daily rhythm plus noise, so charts have shape; drawn on
                // a -1..+1 curve and snapped onto the 0–10 scale.
                val hourPhase = sin((minute / 60.0 - 9) / 13.0 * PI)
                val valence = (0.15 + 0.35 * hourPhase + random.nextDouble(-0.45, 0.45))
                val arousal = (0.3 * hourPhase + random.nextDouble(-0.5, 0.5))
                // Pain drags on valence a little, so the mood-vs-pain chart has a slope.
                val pain = if (painFreeDay) 0 else
                    (painBaseline - 3 * valence + random.nextDouble(-1.0, 1.0)).roundToInt().coerceIn(0, 10)
                val socialId = socials.randomOrNull(random)?.id
                val sampleId = sampleDao.insert(
                    SampleEntity(
                        promptId = promptId,
                        valence = MoodScale.fromLegacy(valence.toFloat()),
                        arousal = MoodScale.fromLegacy(arousal.toFloat()),
                        pain = pain,
                        locationId = locations.randomOrNull(random)?.id,
                        socialId = socialId,
                        note = null,
                        createdAtEpochMs = fireAt + 120_000,
                    )
                )
                val devices = (companyDevices[socialId].orEmpty().filter { random.nextFloat() < 0.7f } +
                    ownEarbuds + List(random.nextInt(0, 5)) { "seednoise%06x".format(random.nextInt(0x1000000)) }).distinct()
                sampleDao.insertDevices(devices.map { SampleDeviceEntity(sampleId, it) })
                // Real moments overlap: 1–3 activities per sample.
                val tagged = activities.shuffled(random).take(random.nextInt(1, 4))
                if (tagged.isNotEmpty()) {
                    sampleDao.insertActivities(tagged.map { SampleActivityCrossRef(sampleId, it.id) })
                }
            }
        }
    }
}

private fun <T> List<T>.randomOrNull(random: Random): T? =
    if (isEmpty()) null else this[random.nextInt(size)]

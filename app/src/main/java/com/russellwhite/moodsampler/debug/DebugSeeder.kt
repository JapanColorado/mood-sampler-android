package com.russellwhite.moodsampler.debug

import com.russellwhite.moodsampler.AppGraph
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.db.SampleEntity
import com.russellwhite.moodsampler.data.db.ScheduledPromptEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
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

    val today = LocalDate.now()
    for (dayOffset in days downTo 1) {
        val date = today.minusDays(dayOffset.toLong())
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
                )
            )
            if (answered) {
                // Mood with a gentle daily rhythm plus noise, so charts have shape.
                val hourPhase = sin((minute / 60.0 - 9) / 13.0 * PI)
                val valence = (0.15 + 0.35 * hourPhase + random.nextDouble(-0.45, 0.45))
                val arousal = (0.3 * hourPhase + random.nextDouble(-0.5, 0.5))
                sampleDao.insert(
                    SampleEntity(
                        promptId = promptId,
                        valence = valence.coerceIn(-1.0, 1.0).toFloat(),
                        arousal = arousal.coerceIn(-1.0, 1.0).toFloat(),
                        activityId = activities.randomOrNull(random)?.id,
                        locationId = locations.randomOrNull(random)?.id,
                        socialId = socials.randomOrNull(random)?.id,
                        note = null,
                        createdAtEpochMs = fireAt + 120_000,
                    )
                )
            }
        }
    }
}

private fun <T> List<T>.randomOrNull(random: Random): T? =
    if (isEmpty()) null else this[random.nextInt(size)]

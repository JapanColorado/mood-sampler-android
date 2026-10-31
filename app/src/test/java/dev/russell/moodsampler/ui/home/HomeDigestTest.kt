package dev.russell.moodsampler.ui.home

import dev.russell.moodsampler.data.db.PromptStatus
import dev.russell.moodsampler.data.db.ScheduledPromptEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDigestTest {

    private fun prompt(id: Long, status: PromptStatus) = ScheduledPromptEntity(
        id = id,
        scheduledAtEpochMs = id,
        localDate = "2026-10-30",
        windowIndex = 0,
        status = status,
    )

    @Test
    fun dotsFollowThePromptsAndLeaveOutCancelled() {
        val dots = dayDots(
            listOf(
                prompt(1, PromptStatus.ANSWERED),
                prompt(2, PromptStatus.MISSED),
                prompt(3, PromptStatus.CANCELLED),
                prompt(4, PromptStatus.NOTIFIED),
                prompt(5, PromptStatus.PENDING),
            ),
            valenceByPrompt = mapOf(1L to 7),
        )
        assertEquals(
            listOf(
                DayDot(DayDot.Kind.ANSWERED, 7),
                DayDot(DayDot.Kind.MISSED),
                DayDot(DayDot.Kind.OPEN),
                DayDot(DayDot.Kind.PENDING),
            ),
            dots,
        )
    }

    @Test
    fun meansCoverSevenDaysOldestFirstWithGapsNull() {
        val last = LocalDate.of(2026, 10, 30)
        val means = dayMeans(
            listOf("2026-10-30" to 4, "2026-10-30" to 8, "2026-10-24" to 2, "2026-10-23" to 10),
            last,
        )
        assertEquals((6L downTo 0L).map { last.minusDays(it) }, means.map { it.date })
        assertEquals(2.0, means.first().valence!!, 0.0)
        assertEquals(6.0, means.last().valence!!, 0.0)
        assertNull(means[3].valence)
    }
}

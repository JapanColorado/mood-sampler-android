package com.russellwhite.moodsampler.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.db.ScheduledPromptEntity
import com.russellwhite.moodsampler.data.settings.SettingsRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AlarmManager orchestration around the pure [ScheduleGenerator].
 *
 * Only one prompt alarm is armed at a time (the next PENDING prompt), plus one
 * expiry alarm per live notification. Every entry point (app launch, alarm
 * fire, boot, time change, settings change) funnels through the idempotent
 * [ensureScheduled], which keeps the schedule ~2 days deep with no background
 * jobs.
 *
 * Days here are *sampling* days, not calendar days: with an AM end time the
 * span that started yesterday morning is still the current day at 00:30, so
 * generation keys off [SamplingConfig.samplingDayFor].
 */
class PromptScheduler(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val generator: ScheduleGenerator,
    private val notifier: PromptNotifier,
    private val clock: Clock,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val dao get() = db.scheduledPromptDao()
    private val mutex = Mutex()

    suspend fun ensureScheduled(): Unit = mutex.withLock {
        sweepOverdueLocked()
        val config = settings.current().toSamplingConfig()
        val now = clock.instant()
        val today = currentSamplingDay(config)
        if (!dao.hasDate(today.toString())) generateInto(today, config, onlyAfter = now)
        val tomorrow = today.plusDays(1)
        if (!dao.hasDate(tomorrow.toString())) generateInto(tomorrow, config, onlyAfter = null)
        armNextLocked(now)
    }

    /** Alarm fired for [promptId]: notify if it is still PENDING, then roll the chain. */
    suspend fun onPromptAlarmFired(promptId: Long) {
        val config = settings.current().toSamplingConfig()
        val now = clock.instant()
        // Guard against stale alarms for cancelled/answered prompts.
        val updated = mutex.withLock { dao.markNotified(promptId, now.toEpochMilli()) }
        if (updated == 1) {
            notifier.showPrompt(promptId, config.expiryMinutes)
            armExpiryAlarm(promptId, now.plusSeconds(config.expiryMinutes * 60L))
        }
        ensureScheduled()
    }

    /** Expiry alarm fired: if still unanswered, record the miss honestly. */
    suspend fun onExpiryAlarmFired(promptId: Long) {
        val missed = mutex.withLock { dao.markMissedIfNotified(promptId) }
        if (missed == 1) notifier.cancel(promptId)
    }

    /** Capture saved: clear the notification and its expiry alarm. */
    fun onPromptAnswered(promptId: Long) {
        alarmManager.cancel(expiryPendingIntent(promptId))
        notifier.cancel(promptId)
    }

    /** Sampling settings changed: drop the stale future schedule and regenerate. */
    suspend fun onConfigChanged(): Unit = mutex.withLock {
        sweepOverdueLocked()
        val config = settings.current().toSamplingConfig()
        val now = clock.instant()
        dao.cancelFuturePending(now.toEpochMilli())
        val today = currentSamplingDay(config)
        generateInto(today, config, onlyAfter = now)
        generateInto(today.plusDays(1), config, onlyAfter = null)
        armNextLocked(now)
    }

    /** Debug-only: schedule a real prompt shortly from now to exercise the full pipeline. */
    suspend fun debugFireIn(seconds: Long) {
        val config = settings.current().toSamplingConfig()
        val now = clock.instant()
        val id = mutex.withLock {
            dao.insert(
                ScheduledPromptEntity(
                    scheduledAtEpochMs = now.plusSeconds(seconds).toEpochMilli(),
                    localDate = currentSamplingDay(config).toString(),
                    windowIndex = DEBUG_WINDOW_INDEX,
                    status = PromptStatus.PENDING,
                )
            )
        }
        ensureScheduled()
        // ensureScheduled arms the earliest pending prompt, which is this one.
        check(id > 0)
    }

    /** The sampling day now falls in — the previous date while inside an AM tail. */
    private fun currentSamplingDay(config: SamplingConfig): LocalDate =
        config.samplingDayFor(LocalDateTime.now(clock))

    /** Mark overdue rows MISSED so stats stay honest even if alarms never fired. */
    private suspend fun sweepOverdueLocked() {
        val nowMs = clock.instant().toEpochMilli()
        val config = settings.current().toSamplingConfig()

        // PENDING prompts whose fire time passed without the alarm running
        // (device off, alarm revoked). Grace period avoids racing an in-flight alarm.
        val stalePending = dao.overduePending(nowMs - FIRE_GRACE_MS)
        if (stalePending.isNotEmpty()) dao.markMissed(stalePending.map { it.id })

        // NOTIFIED prompts whose expiry alarm was delayed/lost.
        val expiryMs = config.expiryMinutes * 60_000L
        val staleNotified = dao.overdueNotified(nowMs - expiryMs)
        if (staleNotified.isNotEmpty()) {
            dao.markMissed(staleNotified.map { it.id })
            staleNotified.forEach { notifier.cancel(it.id) }
        }
    }

    private suspend fun generateInto(date: LocalDate, config: SamplingConfig, onlyAfter: Instant?) {
        val prompts = generator.generateDay(date, clock.zone, config)
            .filter { onlyAfter == null || it.fireAt.isAfter(onlyAfter) }
            .map {
                ScheduledPromptEntity(
                    scheduledAtEpochMs = it.fireAt.toEpochMilli(),
                    localDate = it.localDate.toString(),
                    windowIndex = it.windowIndex,
                    status = PromptStatus.PENDING,
                )
            }
        if (prompts.isNotEmpty()) dao.insertAll(prompts)
    }

    private suspend fun armNextLocked(now: Instant) {
        val next = dao.nextPending(now.toEpochMilli()) ?: run {
            alarmManager.cancel(promptPendingIntent(0))
            return
        }
        val pi = promptPendingIntent(next.id)
        val at = next.scheduledAtEpochMs
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Exact-alarm access revoked (possible on API 31–32): degrade to a
            // ±10 min window — still random, still valid ESM.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, at, INEXACT_WINDOW_MS, pi)
        }
    }

    private fun armExpiryAlarm(promptId: Long, at: Instant) {
        // Expiry needn't be exact; Doze delays are corrected by sweepOverdue.
        alarmManager.set(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), expiryPendingIntent(promptId))
    }

    /** Single slot: arming the next prompt replaces the previous alarm. */
    private fun promptPendingIntent(promptId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            RC_PROMPT,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_PROMPT)
                .putExtra(AlarmReceiver.EXTRA_PROMPT_ID, promptId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Per-prompt request code: overlapping live notifications keep distinct expiry alarms. */
    private fun expiryPendingIntent(promptId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            promptId.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_EXPIRE)
                .putExtra(AlarmReceiver.EXTRA_PROMPT_ID, promptId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        private const val RC_PROMPT = 1
        private const val FIRE_GRACE_MS = 90_000L
        private const val INEXACT_WINDOW_MS = 10 * 60_000L
        const val DEBUG_WINDOW_INDEX = 99
    }
}

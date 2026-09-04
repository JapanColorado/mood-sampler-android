package com.russellwhite.moodsampler.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledPromptDao {

    @Insert
    suspend fun insertAll(prompts: List<ScheduledPromptEntity>): List<Long>

    @Insert
    suspend fun insert(prompt: ScheduledPromptEntity): Long

    @Query("SELECT * FROM scheduled_prompt WHERE id = :id")
    suspend fun getById(id: Long): ScheduledPromptEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM scheduled_prompt WHERE localDate = :localDate)")
    suspend fun hasDate(localDate: String): Boolean

    @Query(
        """SELECT * FROM scheduled_prompt
           WHERE status = 'PENDING' AND scheduledAtEpochMs > :nowMs
           ORDER BY scheduledAtEpochMs LIMIT 1"""
    )
    suspend fun nextPending(nowMs: Long): ScheduledPromptEntity?

    /** PENDING prompts whose fire time already passed (alarm never fired / device off). */
    @Query("SELECT * FROM scheduled_prompt WHERE status = 'PENDING' AND scheduledAtEpochMs <= :nowMs")
    suspend fun overduePending(nowMs: Long): List<ScheduledPromptEntity>

    /** NOTIFIED prompts whose expiry window has elapsed. */
    @Query("SELECT * FROM scheduled_prompt WHERE status = 'NOTIFIED' AND notifiedAtEpochMs <= :cutoffMs")
    suspend fun overdueNotified(cutoffMs: Long): List<ScheduledPromptEntity>

    @Query("UPDATE scheduled_prompt SET status = 'NOTIFIED', notifiedAtEpochMs = :atMs WHERE id = :id AND status = 'PENDING'")
    suspend fun markNotified(id: Long, atMs: Long): Int

    @Query("UPDATE scheduled_prompt SET status = 'ANSWERED', answeredAtEpochMs = :atMs WHERE id = :id AND status IN ('PENDING', 'NOTIFIED')")
    suspend fun markAnswered(id: Long, atMs: Long): Int

    @Query("UPDATE scheduled_prompt SET status = 'MISSED' WHERE id = :id AND status = 'NOTIFIED'")
    suspend fun markMissedIfNotified(id: Long): Int

    @Query("UPDATE scheduled_prompt SET status = 'MISSED' WHERE id IN (:ids)")
    suspend fun markMissed(ids: List<Long>)

    /** User declined the prompt on purpose: still MISSED for honest response rates, but with a reason. */
    @Query(
        """UPDATE scheduled_prompt SET status = 'MISSED', skipReason = :reason
           WHERE id = :id AND status IN ('PENDING', 'NOTIFIED')"""
    )
    suspend fun markSkipped(id: Long, reason: String): Int

    /** The user deleted this prompt's answer: drop it from stats rather than count it as missed. */
    @Query(
        """UPDATE scheduled_prompt SET status = 'CANCELLED', answeredAtEpochMs = NULL
           WHERE id = :id AND status = 'ANSWERED'"""
    )
    suspend fun cancelAnswered(id: Long): Int

    @Query("UPDATE scheduled_prompt SET status = 'CANCELLED' WHERE status = 'PENDING' AND scheduledAtEpochMs > :nowMs")
    suspend fun cancelFuturePending(nowMs: Long): Int

    @Query("SELECT COUNT(*) FROM scheduled_prompt WHERE status = :status AND scheduledAtEpochMs >= :sinceMs")
    suspend fun countByStatusSince(status: PromptStatus, sinceMs: Long): Int

    @Query("SELECT COUNT(*) FROM scheduled_prompt")
    suspend fun count(): Int

    @Query("SELECT * FROM scheduled_prompt WHERE status = 'NOTIFIED'")
    suspend fun allNotified(): List<ScheduledPromptEntity>

    @Query("SELECT * FROM scheduled_prompt WHERE localDate = :localDate ORDER BY scheduledAtEpochMs")
    fun promptsForDateFlow(localDate: String): Flow<List<ScheduledPromptEntity>>

    @Query("SELECT COUNT(*) FROM scheduled_prompt WHERE status = :status AND scheduledAtEpochMs >= :sinceMs")
    fun countByStatusSinceFlow(status: PromptStatus, sinceMs: Long): Flow<Int>

    @Transaction
    @Query(
        """SELECT * FROM scheduled_prompt
           WHERE status IN ('ANSWERED', 'MISSED') AND scheduledAtEpochMs >= :sinceMs
           ORDER BY scheduledAtEpochMs"""
    )
    fun concludedPromptsSinceFlow(sinceMs: Long): Flow<List<PromptWithSample>>

    @Transaction
    @Query(
        """SELECT * FROM scheduled_prompt
           WHERE status IN ('ANSWERED', 'MISSED')
           ORDER BY scheduledAtEpochMs"""
    )
    suspend fun allConcludedPrompts(): List<PromptWithSample>

    /** Newest first, for the History screen. */
    @Transaction
    @Query(
        """SELECT * FROM scheduled_prompt
           WHERE status IN ('ANSWERED', 'MISSED')
           ORDER BY scheduledAtEpochMs DESC"""
    )
    fun historyFlow(): Flow<List<PromptWithSample>>

    @Query("SELECT * FROM scheduled_prompt ORDER BY scheduledAtEpochMs")
    suspend fun getAll(): List<ScheduledPromptEntity>

    @Query("DELETE FROM scheduled_prompt")
    suspend fun deleteAll()
}

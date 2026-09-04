package com.russellwhite.moodsampler.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** How many samples reference a category (activities via the join table, place/company directly). */
data class CategoryUsage(val categoryId: Long, val n: Int)

@Dao
interface SampleDao {

    @Insert
    suspend fun insert(sample: SampleEntity): Long

    @Insert
    suspend fun insertAll(samples: List<SampleEntity>)

    @Update
    suspend fun update(sample: SampleEntity)

    @Query("DELETE FROM sample WHERE promptId = :promptId")
    suspend fun deleteByPromptId(promptId: Long): Int

    @Transaction
    @Query("SELECT * FROM sample WHERE promptId = :promptId")
    suspend fun byPromptId(promptId: Long): SampleWithActivities?

    @Query("SELECT COUNT(*) FROM sample")
    suspend fun count(): Int

    @Query("SELECT * FROM sample ORDER BY id")
    suspend fun getAll(): List<SampleEntity>

    @Query("SELECT * FROM sample_activity ORDER BY sampleId, categoryId")
    suspend fun getAllActivities(): List<SampleActivityCrossRef>

    @Insert
    suspend fun insertActivities(refs: List<SampleActivityCrossRef>)

    @Query("DELETE FROM sample_activity WHERE sampleId = :sampleId")
    suspend fun deleteActivities(sampleId: Long)

    @Query(
        """SELECT categoryId, COUNT(*) AS n FROM sample_activity GROUP BY categoryId
           UNION ALL
           SELECT locationId AS categoryId, COUNT(*) AS n FROM sample WHERE locationId IS NOT NULL GROUP BY locationId
           UNION ALL
           SELECT socialId AS categoryId, COUNT(*) AS n FROM sample WHERE socialId IS NOT NULL GROUP BY socialId"""
    )
    fun categoryUsageFlow(): Flow<List<CategoryUsage>>

    @Query("DELETE FROM sample_activity")
    suspend fun deleteAllActivities()

    @Query("DELETE FROM sample")
    suspend fun deleteAll()
}

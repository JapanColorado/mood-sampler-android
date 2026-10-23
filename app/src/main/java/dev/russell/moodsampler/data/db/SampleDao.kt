package dev.russell.moodsampler.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A past answer's coordinates and the place the user picked — the training set for place suggestions. */
data class GeoTag(val locationId: Long, val latitude: Double, val longitude: Double)

/** One device seen with a past answer that named its company — the training set for company suggestions. */
data class DeviceTag(val sampleId: Long, val socialId: Long, val deviceHash: String)

/** How many samples reference a category (activities and pain types via join tables, place/company directly). */
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

    @Query("SELECT photoPath FROM sample WHERE photoPath IS NOT NULL")
    suspend fun allPhotoPaths(): List<String>

    @Query("SELECT photoPath FROM sample WHERE promptId = :promptId")
    suspend fun photoPathByPromptId(promptId: Long): String?

    @Query("SELECT * FROM sample_activity ORDER BY sampleId, categoryId")
    suspend fun getAllActivities(): List<SampleActivityCrossRef>

    @Insert
    suspend fun insertActivities(refs: List<SampleActivityCrossRef>)

    @Query("DELETE FROM sample_activity WHERE sampleId = :sampleId")
    suspend fun deleteActivities(sampleId: Long)

    @Query("SELECT * FROM sample_pain ORDER BY sampleId, categoryId")
    suspend fun getAllPainTypes(): List<SamplePainCrossRef>

    @Insert
    suspend fun insertPainTypes(refs: List<SamplePainCrossRef>)

    @Query("DELETE FROM sample_pain WHERE sampleId = :sampleId")
    suspend fun deletePainTypes(sampleId: Long)

    @Query("SELECT * FROM sample_tag ORDER BY sampleId, tag")
    suspend fun getAllTags(): List<SampleTagEntity>

    @Insert
    suspend fun insertTags(rows: List<SampleTagEntity>)

    @Query("DELETE FROM sample_tag WHERE sampleId = :sampleId")
    suspend fun deleteTags(sampleId: Long)

    @Query(
        """SELECT categoryId, COUNT(*) AS n FROM sample_activity GROUP BY categoryId
           UNION ALL
           SELECT categoryId, COUNT(*) AS n FROM sample_pain GROUP BY categoryId
           UNION ALL
           SELECT locationId AS categoryId, COUNT(*) AS n FROM sample WHERE locationId IS NOT NULL GROUP BY locationId
           UNION ALL
           SELECT socialId AS categoryId, COUNT(*) AS n FROM sample WHERE socialId IS NOT NULL GROUP BY socialId"""
    )
    fun categoryUsageFlow(): Flow<List<CategoryUsage>>

    /** Most recent geotagged answers that also carry a place, newest first. */
    @Query(
        """SELECT locationId, latitude, longitude FROM sample
           WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND locationId IS NOT NULL
           ORDER BY id DESC LIMIT :limit"""
    )
    suspend fun recentGeoTags(limit: Int = 500): List<GeoTag>

    /** Devices of the most recent [limit] answers that carry both a company and at least one device. */
    @Query(
        """SELECT d.sampleId, s.socialId, d.deviceHash FROM sample_device d JOIN sample s ON s.id = d.sampleId
           WHERE d.sampleId IN (
               SELECT d2.sampleId FROM sample_device d2 JOIN sample s2 ON s2.id = d2.sampleId
               WHERE s2.socialId IS NOT NULL GROUP BY d2.sampleId ORDER BY d2.sampleId DESC LIMIT :limit
           )"""
    )
    suspend fun recentDeviceTags(limit: Int = 500): List<DeviceTag>

    @Query("SELECT * FROM sample_device ORDER BY sampleId, deviceHash")
    suspend fun getAllDevices(): List<SampleDeviceEntity>

    @Insert
    suspend fun insertDevices(rows: List<SampleDeviceEntity>)

    @Query("DELETE FROM sample_device")
    suspend fun deleteAllDevices()

    @Query("DELETE FROM sample_activity")
    suspend fun deleteAllActivities()

    @Query("DELETE FROM sample_pain")
    suspend fun deleteAllPainTypes()

    @Query("DELETE FROM sample_tag")
    suspend fun deleteAllTags()

    @Query("DELETE FROM sample")
    suspend fun deleteAll()
}

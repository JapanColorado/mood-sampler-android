package dev.russell.moodsampler.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category WHERE type = :type AND isArchived = 0 ORDER BY sortOrder, id")
    fun activeByTypeFlow(type: CategoryType): Flow<List<CategoryEntity>>

    /** Every category of [type], archived included, in display order. */
    @Query("SELECT * FROM category WHERE type = :type ORDER BY sortOrder, id")
    suspend fun byType(type: CategoryType): List<CategoryEntity>

    @Query("SELECT * FROM category")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM category")
    fun allFlow(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    /** Categories of [type], archived included, that no sample references, in display order. */
    @Query(
        """SELECT * FROM category WHERE type = :type
           AND id NOT IN (SELECT categoryId FROM sample_activity)
           AND id NOT IN (SELECT categoryId FROM sample_pain)
           AND id NOT IN (SELECT locationId FROM sample WHERE locationId IS NOT NULL)
           AND id NOT IN (SELECT socialId FROM sample WHERE socialId IS NOT NULL)
           ORDER BY sortOrder, id"""
    )
    suspend fun unusedByType(type: CategoryType): List<CategoryEntity>

    /** Delete those of [ids] that no sample references (re-checked here, so a stale list is safe). */
    @Query(
        """DELETE FROM category WHERE id IN (:ids)
           AND id NOT IN (SELECT categoryId FROM sample_activity)
           AND id NOT IN (SELECT categoryId FROM sample_pain)
           AND id NOT IN (SELECT locationId FROM sample WHERE locationId IS NOT NULL)
           AND id NOT IN (SELECT socialId FROM sample WHERE socialId IS NOT NULL)"""
    )
    suspend fun deleteUnused(ids: List<Long>): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM category WHERE type = :type")
    suspend fun maxSortOrder(type: CategoryType): Int

    @Query("DELETE FROM category")
    suspend fun deleteAll()
}

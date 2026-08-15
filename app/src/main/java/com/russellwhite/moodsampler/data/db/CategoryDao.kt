package com.russellwhite.moodsampler.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category WHERE type = :type AND isArchived = 0 ORDER BY sortOrder, id")
    fun activeByTypeFlow(type: CategoryType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM category")
    fun allFlow(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM category WHERE type = :type")
    suspend fun maxSortOrder(type: CategoryType): Int
}

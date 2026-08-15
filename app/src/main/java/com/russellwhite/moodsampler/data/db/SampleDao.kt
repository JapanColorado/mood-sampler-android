package com.russellwhite.moodsampler.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SampleDao {

    @Insert
    suspend fun insert(sample: SampleEntity): Long

    @Query("SELECT * FROM sample WHERE promptId = :promptId")
    suspend fun byPromptId(promptId: Long): SampleEntity?

    @Query("SELECT COUNT(*) FROM sample")
    suspend fun count(): Int
}

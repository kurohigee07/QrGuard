package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompt_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<PromptRecord>>

    @Query("SELECT * FROM prompt_records WHERE isBookmarked = 1 ORDER BY timestamp DESC")
    fun getBookmarkedRecords(): Flow<List<PromptRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: PromptRecord)

    @Update
    suspend fun updateRecord(record: PromptRecord)

    @Query("UPDATE prompt_records SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: Long, isBookmarked: Boolean)

    @Query("DELETE FROM prompt_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM prompt_records")
    suspend fun clearAll()
}

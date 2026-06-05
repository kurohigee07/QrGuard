package com.example.db

import kotlinx.coroutines.flow.Flow

class PromptRepository(private val promptDao: PromptDao) {
    val allRecords: Flow<List<PromptRecord>> = promptDao.getAllRecords()
    val bookmarkedRecords: Flow<List<PromptRecord>> = promptDao.getBookmarkedRecords()

    suspend fun insertRecord(record: PromptRecord) {
        promptDao.insertRecord(record)
    }

    suspend fun updateRecord(record: PromptRecord) {
        promptDao.updateRecord(record)
    }

    suspend fun updateBookmarkStatus(id: Long, isBookmarked: Boolean) {
        promptDao.updateBookmarkStatus(id, isBookmarked)
    }

    suspend fun deleteRecordById(id: Long) {
        promptDao.deleteRecordById(id)
    }

    suspend fun clearAll() {
        promptDao.clearAll()
    }
}

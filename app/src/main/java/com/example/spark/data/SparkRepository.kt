package com.example.spark.data

import kotlinx.coroutines.flow.Flow

class SparkRepository(private val dao: SparkDao) {
    val allMemories: Flow<List<CoreMemoryEntity>> = dao.getAllMemories()
    val recentActionLogs: Flow<List<ActionLogEntity>> = dao.getRecentActionLogs()

    suspend fun saveMemory(key: String, value: String, category: String = "general"): Long {
        val entity = CoreMemoryEntity(
            key = key.trim(),
            value = value.trim(),
            category = category.trim(),
            timestamp = System.currentTimeMillis()
        )
        return dao.insertMemory(entity)
    }

    suspend fun getMemory(key: String): CoreMemoryEntity? {
        return dao.getMemoryByKey(key.trim())
    }

    suspend fun searchMemories(query: String): List<CoreMemoryEntity> {
        return dao.searchMemories(query.trim())
    }

    suspend fun deleteMemoryById(id: Long) {
        dao.deleteMemoryById(id)
    }

    suspend fun clearMemories() {
        dao.clearAllMemories()
    }

    suspend fun logAction(
        toolName: String,
        argumentsJson: String,
        resultText: String,
        isSuccess: Boolean
    ): Long {
        val log = ActionLogEntity(
            toolName = toolName,
            argumentsJson = argumentsJson,
            resultText = resultText,
            isSuccess = isSuccess,
            timestamp = System.currentTimeMillis()
        )
        return dao.insertActionLog(log)
    }

    suspend fun clearActionLogs() {
        dao.clearAllActionLogs()
    }
}

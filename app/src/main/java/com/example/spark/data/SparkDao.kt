package com.example.spark.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SparkDao {
    // Core Memory Operations
    @Query("SELECT * FROM core_memory ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<CoreMemoryEntity>>

    @Query("SELECT * FROM core_memory WHERE memory_key LIKE '%' || :query || '%' OR memory_value LIKE '%' || :query || '%'")
    suspend fun searchMemories(query: String): List<CoreMemoryEntity>

    @Query("SELECT * FROM core_memory WHERE LOWER(memory_key) = LOWER(:key) LIMIT 1")
    suspend fun getMemoryByKey(key: String): CoreMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: CoreMemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: CoreMemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: CoreMemoryEntity)

    @Query("DELETE FROM core_memory WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM core_memory")
    suspend fun clearAllMemories()

    // Action Logs Operations
    @Query("SELECT * FROM action_log ORDER BY timestamp DESC LIMIT 100")
    fun getRecentActionLogs(): Flow<List<ActionLogEntity>>

    @Insert
    suspend fun insertActionLog(log: ActionLogEntity): Long

    @Query("DELETE FROM action_log")
    suspend fun clearAllActionLogs()
}

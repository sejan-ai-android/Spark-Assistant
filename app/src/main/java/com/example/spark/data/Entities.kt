package com.example.spark.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "core_memory")
data class CoreMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "memory_key")
    val key: String,
    @ColumnInfo(name = "memory_value")
    val value: String,
    @ColumnInfo(name = "category")
    val category: String = "general",
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "action_log")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "tool_name")
    val toolName: String,
    @ColumnInfo(name = "arguments_json")
    val argumentsJson: String,
    @ColumnInfo(name = "result_text")
    val resultText: String,
    @ColumnInfo(name = "is_success")
    val isSuccess: Boolean,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

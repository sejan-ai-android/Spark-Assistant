package com.example.spark.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CoreMemoryEntity::class, ActionLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SparkDatabase : RoomDatabase() {
    abstract fun sparkDao(): SparkDao

    companion object {
        @Volatile
        private var INSTANCE: SparkDatabase? = null

        fun getDatabase(context: Context): SparkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SparkDatabase::class.java,
                    "spark_assistant_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

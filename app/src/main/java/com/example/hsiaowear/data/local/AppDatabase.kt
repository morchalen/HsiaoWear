package com.example.hsiaowear.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ClothingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clothingDao(): ClothingDao
}
package com.example.hsiaowear.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothes")
data class ClothingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val category: String = "",
    val color: String = "",
    val imageUrl: String = "",
    val aiData: String = "{}",
    val createdAt: Long = System.currentTimeMillis()
)
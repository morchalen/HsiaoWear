package com.example.hsiaowear.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClothingDao {
    @Query("SELECT * FROM clothes ORDER BY createdAt DESC")
    fun getAllClothes(): Flow<List<ClothingEntity>>

    @Query("SELECT * FROM clothes WHERE id = :id")
    suspend fun getClothingById(id: Long): ClothingEntity?

    @Query("SELECT * FROM clothes WHERE name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR color LIKE '%' || :query || '%'")
    fun searchClothes(query: String): Flow<List<ClothingEntity>>

    @Query("SELECT * FROM clothes WHERE category = :category")
    fun getClothesByCategory(category: String): Flow<List<ClothingEntity>>

    @Insert
    suspend fun insertClothing(clothing: ClothingEntity): Long

    @Update
    suspend fun updateClothing(clothing: ClothingEntity)

    @Delete
    suspend fun deleteClothing(clothing: ClothingEntity)

    @Query("DELETE FROM clothes WHERE id = :id")
    suspend fun deleteClothingById(id: Long)
}


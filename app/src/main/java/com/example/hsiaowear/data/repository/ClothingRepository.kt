package com.example.hsiaowear.data.repository

import com.example.hsiaowear.data.local.ClothingDao
import com.example.hsiaowear.data.local.ClothingEntity
import com.example.hsiaowear.network.ApiResponse
import com.example.hsiaowear.network.WardrobeApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ClothingRepository @Inject constructor(
    private val clothingDao: ClothingDao,
    private val api: WardrobeApi
) {
    fun getAllClothes(): Flow<List<ClothingEntity>> {
        return clothingDao.getAllClothes()
    }

    fun searchClothes(query: String): Flow<List<ClothingEntity>> {
        return clothingDao.searchClothes(query)
    }

    fun getClothesByCategory(category: String): Flow<List<ClothingEntity>> {
        return clothingDao.getClothesByCategory(category)
    }

    suspend fun getClothingById(id: Long): ClothingEntity? {
        return clothingDao.getClothingById(id)
    }

    suspend fun insertClothing(clothing: ClothingEntity): Long {
        return clothingDao.insertClothing(clothing)
    }

    suspend fun updateClothing(clothing: ClothingEntity) {
        clothingDao.updateClothing(clothing)
    }

    suspend fun deleteClothing(clothing: ClothingEntity) {
        clothingDao.deleteClothing(clothing)
    }

    suspend fun deleteClothingById(id: Long) {
        clothingDao.deleteClothingById(id)
    }

    suspend fun fetchClothesFromApi(): ApiResponse<*>? {
        return try {
            api.getClothes()
        } catch (e: Exception) {
            null
        }
    }
}
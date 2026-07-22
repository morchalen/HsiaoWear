package com.example.hsiaowear.data.repository

import com.example.hsiaowear.data.Result
import com.example.hsiaowear.network.OutfitScoreRequest
import com.example.hsiaowear.network.OutfitScoreResponse
import com.example.hsiaowear.network.RecommendationResponse
import com.example.hsiaowear.network.WardrobeApi
import javax.inject.Inject

class RecommendationRepository @Inject constructor(
    private val api: WardrobeApi
) {
    suspend fun getTodayRecommendation(): Result<RecommendationResponse> {
        return try {
            val response = api.getTodayRecommendation()
            response.data?.let { Result.Success(it) } ?: Result.Error(Exception("Empty response"))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun scoreOutfit(outfitIds: List<Long>): Result<OutfitScoreResponse> {
        return try {
            val response = api.scoreOutfit(OutfitScoreRequest(outfitIds))
            response.data?.let { Result.Success(it) } ?: Result.Error(Exception("Empty response"))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
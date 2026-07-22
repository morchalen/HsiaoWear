package com.example.hsiaowear.data.repository

import com.example.hsiaowear.data.Result
import com.example.hsiaowear.network.AIClassifyRequest
import com.example.hsiaowear.network.AIClassifyResponse
import com.example.hsiaowear.network.AISegmentRequest
import com.example.hsiaowear.network.AITaskResponse
import com.example.hsiaowear.network.WardrobeApi
import javax.inject.Inject

class AIServiceRepository @Inject constructor(
    private val api: WardrobeApi
) {
    suspend fun aiSegment(imageBase64: String): Result<AITaskResponse> {
        return try {
            val response = api.aiSegment(AISegmentRequest(imageBase64))
            response.data?.let { Result.Success(it) } ?: Result.Error(Exception("Empty response"))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun aiClassify(imageBase64: String): Result<AIClassifyResponse> {
        return try {
            val response = api.aiClassify(AIClassifyRequest(imageBase64))
            response.data?.let { Result.Success(it) } ?: Result.Error(Exception("Empty response"))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
package com.example.hsiaowear.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface WardrobeApi {
    @GET("clothes")
    suspend fun getClothes(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("category") category: String? = null,
        @Query("search") search: String? = null
    ): ApiResponse<PaginatedResponse<ClothingResponse>>

    @GET("clothes/{id}")
    suspend fun getClothing(@Path("id") id: Long): ApiResponse<ClothingResponse>

    @POST("clothes/upload")
    suspend fun uploadClothing(@Body request: UploadClothingRequest): ApiResponse<ClothingResponse>

    @PUT("clothes/{id}")
    suspend fun updateClothing(
        @Path("id") id: Long,
        @Body request: UpdateClothingRequest
    ): ApiResponse<ClothingResponse>

    @DELETE("clothes/{id}")
    suspend fun deleteClothing(@Path("id") id: Long): ApiResponse<Void>

    @POST("ai/segment")
    suspend fun aiSegment(@Body request: AISegmentRequest): ApiResponse<AITaskResponse>

    @POST("ai/classify")
    suspend fun aiClassify(@Body request: AIClassifyRequest): ApiResponse<AIClassifyResponse>

    @GET("recommendations/today")
    suspend fun getTodayRecommendation(): ApiResponse<RecommendationResponse>

    @POST("outfit/score")
    suspend fun scoreOutfit(@Body request: OutfitScoreRequest): ApiResponse<OutfitScoreResponse>

    // ==================== AI 试衣相关 ====================

    @POST("upload")
    suspend fun uploadImage(@Body request: UploadImageRequest): ApiResponse<ImageUploadResponse>
}

data class ApiResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null
)

data class PaginatedResponse<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val perPage: Int = 20
)

data class ClothingResponse(
    val id: Long = 0,
    val name: String = "",
    val category: String = "",
    val color: String = "",
    val imageUrl: String = "",
    val aiData: String = "{}",
    val createdAt: Long = 0
)

data class UploadClothingRequest(
    val name: String = "",
    val category: String = "",
    val color: String = "",
    val imageBase64: String = "",
    val aiData: String = "{}"
)

data class UpdateClothingRequest(
    val name: String? = null,
    val category: String? = null,
    val color: String? = null
)

data class AISegmentRequest(
    val imageBase64: String = ""
)

data class AIClassifyRequest(
    val imageBase64: String = ""
)

data class AITaskResponse(
    val taskId: String = "",
    val status: String = "",
    val result: String = ""
)

data class AIClassifyResponse(
    val category: String = "",
    val color: String = "",
    val name: String = "",
    val confidence: Double = 0.0
)

data class RecommendationResponse(
    val outfits: List<OutfitResponse> = emptyList(),
    val weather: WeatherResponse? = null
)

data class OutfitResponse(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val items: List<ClothingResponse> = emptyList()
)

data class WeatherResponse(
    val temperature: Int = 0,
    val condition: String = "",
    val recommendation: String = ""
)

data class OutfitScoreRequest(
    val outfitIds: List<Long> = emptyList()
)

data class OutfitScoreResponse(
    val score: Int = 0,
    val feedback: String = ""
)

// ==================== AI 试衣相关数据类 ====================

data class UploadImageRequest(
    val imageBase64: String = ""
)

data class ImageUploadResponse(
    val url: String = ""
)
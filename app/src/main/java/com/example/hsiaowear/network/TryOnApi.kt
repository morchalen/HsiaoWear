package com.example.hsiaowear.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// ==================== 创建任务请求体 ====================

data class TryOnRequest(
    val model: String = "aitryon",
    val input: TryOnInput,
    val parameters: TryOnParameters = TryOnParameters()
)

data class TryOnInput(
    val person_image_url: String,
    val top_garment_url: String? = null,
    val bottom_garment_url: String? = null
)

data class TryOnParameters(
    val resolution: Int = -1,
    val restore_face: Boolean = true
)

// ==================== 创建任务响应体 ====================

data class TryOnTaskResponse(
    val output: TryOnTaskOutput? = null,
    val request_id: String = ""
)

data class TryOnTaskOutput(
    val task_id: String = "",
    val task_status: String = ""
)

// ==================== 查询任务响应体 ====================

data class TryOnQueryResponse(
    val output: TryOnQueryOutput? = null,
    val request_id: String = "",
    val usage: TryOnUsage? = null
)

data class TryOnQueryOutput(
    val task_id: String = "",
    val task_status: String = "",
    val image_url: String? = null,
    val submit_time: String? = null,
    val scheduled_time: String? = null,
    val end_time: String? = null,
    val code: String? = null,
    val message: String? = null
)

data class TryOnUsage(
    val image_count: Int = 0
)

// ==================== Retrofit 接口 ====================

interface TryOnApi {

    /**
     * 步骤1：创建 AI 试衣异步任务
     */
    @POST("services/aigc/image2image/image-synthesis")
    suspend fun createTryOnTask(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Header("X-DashScope-Async") async: String = "enable",
        @Body request: TryOnRequest
    ): retrofit2.Response<TryOnTaskResponse>

    /**
     * 步骤2：根据 task_id 查询任务状态和结果
     */
    @GET("tasks/{task_id}")
    suspend fun queryTryOnTask(
        @retrofit2.http.Header("Authorization") authorization: String,
        @Path("task_id") taskId: String
    ): retrofit2.Response<TryOnQueryResponse>
}

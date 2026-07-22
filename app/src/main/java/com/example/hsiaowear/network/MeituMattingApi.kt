package com.example.hsiaowear.network

import retrofit2.http.Body
import retrofit2.http.POST

// ==================== 请求体 ====================

data class MeituMattingRequest(
    val task: String = "/v1/photo_scissors/sod",
    val task_type: String = "mtlab",
    val params: String = """{"parameter":{"nMask":false,"model_type":0}}""",
    val init_images: List<MeituInitImage>,
    val sync_timeout: Int = 30,
    val rsp_media_type: String = "url"
)

data class MeituInitImage(
    val url: String,
    val profile: MeituImageProfile
)

data class MeituImageProfile(
    val media_profiles: MeituMediaProfiles,
    val version: String = "v1"
)

data class MeituMediaProfiles(
    val media_data_type: String  // "url" 表示 URL，"jpg" 表示 base64
)

// ==================== 响应体 ====================

data class MeituMattingResponse(
    val request_id: String = "",
    val code: Int = -1,
    val message: String = "",
    val data: MeituMattingData? = null
)

data class MeituMattingData(
    val status: Int = 0,
    val result: MeituMattingResult? = null,
    val progress: Int = 0
)

data class MeituMattingResult(
    val media_info_list: List<MeituMediaInfo>? = null
)

data class MeituMediaInfo(
    val media_data: String = ""   // 抠图结果图片的 URL 或 base64
)

// ==================== Retrofit 接口 ====================

interface MeituApi {

    @POST("api/v1/sdk/sync/push")
    suspend fun matting(@Body request: MeituMattingRequest): retrofit2.Response<MeituMattingResponse>
}

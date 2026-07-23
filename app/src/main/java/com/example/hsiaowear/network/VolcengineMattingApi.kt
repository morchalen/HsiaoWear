package com.example.hsiaowear.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// ==================== 请求体 ====================

data class VolcengineMattingRequest(
    val req_key: String = "saliency_seg",
    val binary_data_base64: List<String>,
    val only_mask: Int = 3,
    val rgb: List<Int> = listOf(-1, -1, -1),
    val refine_mask: Int = 2
)

// ==================== 响应体 ====================

data class VolcengineMattingResponse(
    val code: Int = -1,
    val message: String = "",
    val data: VolcengineMattingData? = null
)

data class VolcengineMattingData(
    val binary_data_base64: List<String>? = null,
    val image_urls: List<String>? = null,
    val bbox: List<List<Int>>? = null,
    val seg_score: List<Double>? = null,
    val ori_height: List<Int>? = null,
    val ori_width: List<Int>? = null,
    val algorithm_base_resp: VolcengineAlgorithmResp? = null
)

data class VolcengineAlgorithmResp(
    val status_code: Int = 0,
    val status_message: String = ""
)

// ==================== Retrofit 接口 ====================

interface VolcengineApi {

    /**
     * 调用火山引擎通用图像分割（主体分割）API
     *
     * @param action  接口名，固定 CVProcess
     * @param version 版本号，固定 2022-08-31
     * @param request 请求体
     */
    @POST("/")
    suspend fun cvProcess(
        @Query("Action") action: String = "CVProcess",
        @Query("Version") version: String = "2022-08-31",
        @Body request: VolcengineMattingRequest
    ): Response<VolcengineMattingResponse>
}

package com.example.hsiaowear.data.repository

import android.content.Context
import android.util.Log
import com.example.hsiaowear.data.Result
import com.example.hsiaowear.network.MeituApi
import com.example.hsiaowear.network.MeituInitImage
import com.example.hsiaowear.network.MeituImageProfile
import com.example.hsiaowear.network.MeituMattingRequest
import com.example.hsiaowear.network.MeituMattingResponse
import com.example.hsiaowear.network.MeituMediaProfiles
import com.example.hsiaowear.util.ImageUtils
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HsiaoWear-Meitu"

/**
 * 调用美图开放平台抠图 API，完成"选择图片 → 抠图 → 下载结果"全流程。
 */
@Singleton
class MattingRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val meituApi: MeituApi
) {

    /**
     * 对本地图片文件执行抠图，返回抠图结果图片的本地路径。
     *
     * @param localFilePath 原始图片的本地路径
     * @return 抠图成功返回本地路径，失败返回 [Result.error]
     */
    suspend fun matting(localFilePath: String): Result<String> {
        // 1. 图片转 Base64
        Log.d(TAG, "=== 开始抠图流程 ===")
        Log.d(TAG, "原始文件路径: $localFilePath")

        val base64 = ImageUtils.fileToBase64(localFilePath)
        if (base64 == null) {
            Log.e(TAG, "图片转 Base64 失败")
            return Result.error("图片读取失败，无法转换为 Base64")
        }
        Log.d(TAG, "Base64 长度: ${base64.length} 字符")
        Log.d(TAG, "Base64 前 50 字符: ${base64.take(50)}...")

        // 2. 构造请求 - base64 带 data:image 前缀，media_data_type = "jpg"
        val request = MeituMattingRequest(
            init_images = listOf(
                MeituInitImage(
                    url = "data:image/jpeg;base64,$base64",
                    profile = MeituImageProfile(
                        media_profiles = MeituMediaProfiles(media_data_type = "jpg")
                    )
                )
            )
        )
        Log.d(TAG, "请求体: task=${request.task}, task_type=${request.task_type}")
        Log.d(TAG, "请求体: params=${request.params}")
        Log.d(TAG, "请求体: sync_timeout=${request.sync_timeout}, rsp_media_type=${request.rsp_media_type}")
        Log.d(TAG, "请求体: init_images[0].url 前 50 字符: ${request.init_images.first().url.take(50)}...")
        Log.d(TAG, "请求体: init_images[0].profile.media_data_type=${request.init_images.first().profile.media_profiles.media_data_type}")

        // 3. 调用抠图 API
        return try {
            Log.d(TAG, "正在调用 API...")
            val startTime = System.currentTimeMillis()
            val rawResponse = meituApi.matting(request)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "API 返回耗时: ${elapsed}ms")
            Log.d(TAG, "HTTP 状态码: ${rawResponse.code()}")

            // 即使 HTTP 4xx/5xx，也尝试解析响应体获取具体错误信息
            val response = if (rawResponse.isSuccessful) {
                rawResponse.body()
            } else {
                val errorBody = rawResponse.errorBody()?.string()
                Log.e(TAG, "HTTP 错误: ${rawResponse.code()}, errorBody=$errorBody")

                if (errorBody != null) {
                    // 尝试解析为标准 MeituMattingResponse 格式
                    var parsed: MeituMattingResponse? = null
                    try {
                        parsed = com.google.gson.Gson().fromJson(errorBody, MeituMattingResponse::class.java)
                    } catch (_: Exception) { /* ignore */ }

                    // 标准解析后 code=-1（默认值），说明字段名不匹配 → 可能是网关错误格式 {ErrorCode, ErrorMsg}
                    if (parsed != null && parsed.code == -1) {
                        try {
                            val jsonObj = com.google.gson.JsonParser.parseString(errorBody).asJsonObject
                            if (jsonObj.has("ErrorCode")) {
                                val errCode = jsonObj.get("ErrorCode").asInt
                                val errMsg = jsonObj.get("ErrorMsg")?.asString ?: "未知错误"
                                Log.e(TAG, "网关错误: ErrorCode=$errCode, ErrorMsg=$errMsg")
                                return Result.error("抠图 API 认证错误：$errMsg ($errCode)")
                            }
                        } catch (_: Exception) { /* ignore */ }
                    }

                    if (parsed == null) {
                        return Result.error("抠图 API 请求失败：HTTP ${rawResponse.code()}")
                    }
                    parsed
                } else {
                    return Result.error("抠图 API 请求失败：HTTP ${rawResponse.code()}")
                }
            }

            if (response == null) {
                Log.e(TAG, "API 响应体为 null")
                return Result.error("抠图 API 无响应")
            }

            Log.d(TAG, "API 响应: code=${response.code}, message='${response.message}', request_id='${response.request_id}'")

            if (response.code != 0) {
                Log.e(TAG, "API 返回错误: code=${response.code}, message=${response.message}")
                return Result.error("抠图 API 返回错误：${response.message} (code=${response.code})")
            }

            val data = response.data
            if (data == null) {
                Log.e(TAG, "API 返回 data 为 null")
                return Result.error("抠图 API 返回数据为空")
            }

            Log.d(TAG, "任务状态: status=${data.status}, progress=${data.progress}")

            when (data.status) {
                10 -> {
                    // 任务成功
                    val mediaInfoList = data.result?.media_info_list
                    Log.d(TAG, "任务成功, media_info_list=${mediaInfoList?.size ?: 0} 项")

                    if (mediaInfoList.isNullOrEmpty()) {
                        Log.e(TAG, "media_info_list 为空")
                        return Result.error("抠图结果中无图片数据")
                    }

                    val resultUrl = mediaInfoList.first().media_data
                    Log.d(TAG, "抠图结果 URL: ${resultUrl.take(100)}...")

                    if (resultUrl.isBlank()) {
                        Log.e(TAG, "抠图结果 URL 为空")
                        return Result.error("抠图结果图片 URL 为空")
                    }

                    // 4. 下载抠图结果到本地
                    Log.d(TAG, "开始下载抠图结果...")
                    val downloadStart = System.currentTimeMillis()
                    val localPath = ImageUtils.downloadImage(appContext, resultUrl)
                    val downloadElapsed = System.currentTimeMillis() - downloadStart
                    Log.d(TAG, "下载完成, 耗时: ${downloadElapsed}ms, 本地路径: $localPath")

                    if (localPath == null) {
                        Log.e(TAG, "下载抠图结果失败")
                        return Result.error("下载抠图结果图片失败")
                    }

                    Log.d(TAG, "=== 抠图流程成功完成 ===")
                    Result.Success(localPath)
                }

                9 -> {
                    Log.w(TAG, "抠图任务超时")
                    Result.error("抠图任务超时，请重试")
                }

                2 -> {
                    val errorMsg = data.result?.media_info_list?.firstOrNull()?.media_data
                        ?: data.result?.let { "算法内部错误" }
                        ?: "抠图任务失败"
                    Log.e(TAG, "抠图任务失败: $errorMsg")
                    Result.error(errorMsg)
                }

                else -> {
                    Log.w(TAG, "未知任务状态: status=${data.status}")
                    Result.error("抠图任务状态异常：status=${data.status}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "抠图 API 调用异常", e)
            Result.error("抠图 API 调用异常：${e.message ?: e.javaClass.simpleName}")
        }
    }
}

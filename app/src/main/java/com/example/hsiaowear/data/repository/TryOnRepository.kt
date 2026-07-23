package com.example.hsiaowear.data.repository

import android.content.Context
import android.util.Log
import com.example.hsiaowear.data.Result
import com.example.hsiaowear.data.SettingsDataStore
import com.example.hsiaowear.network.TryOnApi
import com.example.hsiaowear.network.TryOnInput
import com.example.hsiaowear.network.TryOnParameters
import com.example.hsiaowear.network.TryOnRequest
import com.example.hsiaowear.util.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HsiaoWear-TryOn"

/**
 * 待试穿的衣物信息。
 */
data class TryOnGarment(
    val name: String,
    val category: String,   // 上衣 / 下装 / 鞋
    val imagePath: String   // 本地路径或网络 URL
)

/**
 * AI 试衣仓库：负责将本地图片上传获取公网 URL → 调用阿里云百炼 aitryon API → 下载结果。
 *
 * 由于用户可自定义 DashScope Host，Repository 内部动态创建 Retrofit 实例（类似 AIRepository 模式）。
 */
@Singleton
class TryOnRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsDataStore: SettingsDataStore
) {
    private var tryOnApi: TryOnApi? = null

    /**
     * 根据用户配置的 host 动态创建 TryOnApi 实例。
     */
    private fun getOrCreateTryOnApi(host: String): TryOnApi {
        // 自动补充 https:// 协议前缀
        val schemeHost = if (host.startsWith("http://") || host.startsWith("https://")) host else "https://$host"
        val cleanHost = if (schemeHost.endsWith("/")) schemeHost else "$schemeHost/"
        val baseUrl = "${cleanHost}api/v1/"

        if (tryOnApi != null) return tryOnApi!!

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(TryOnApi::class.java)
    }

    /**
     * 执行 AI 试衣全流程。
     *
     * @param personImagePath 模特人物图片的本地路径
     * @param garments 待试穿的衣物列表（至少1件）
     * @return 试衣结果图片的本地路径
     */
    suspend fun tryOn(
        personImagePath: String,
        garments: List<TryOnGarment>,
        onStep: ((String) -> Unit)? = null
    ): Result<String> {
        Log.d(TAG, "=== 开始 AI 试衣流程 ===")
        Log.d(TAG, "模特图片: $personImagePath")
        Log.d(TAG, "待试衣物: ${garments.map { "${it.name}(${it.category})" }}")

        // 1. 获取 API Key 和 Host
        onStep?.invoke("正在读取 API 配置...")
        val settings = settingsDataStore.getTryOnSettings()
        val apiKey = settings.apiKey
        val apiHost = settings.host

        if (apiKey.isBlank() || apiHost.isBlank()) {
            Log.e(TAG, "API Key 或 Host 未配置")
            return Result.error("请先在设置中配置 AI 试衣 API Key 和 Host")
        }
        Log.d(TAG, "API 配置读取完成: host=$apiHost")

        // 创建动态 TryOnApi
        tryOnApi = getOrCreateTryOnApi(apiHost)
        val api = tryOnApi ?: return Result.error("创建 API 客户端失败")

        // 2. 上传模特图片获取公网 URL
        onStep?.invoke("正在上传模特图片...")
        Log.d(TAG, "步骤1: 上传模特图片...")
        val personImageUrl = when (val result = uploadImage(personImagePath, "person")) {
            is Result.Success -> {
                Log.d(TAG, "模特图片上传成功: ${result.data}")
                result.data
            }
            is Result.Error -> {
                Log.e(TAG, "模特图片上传失败: ${result.message}")
                return Result.error("模特图片上传失败：${result.message}")
            }
            Result.Loading -> return Result.error("上传失败")
        }

        // 3. 上传衣物图片获取公网 URL
        onStep?.invoke("正在上传衣物图片...")
        var topUrl: String? = null
        var bottomUrl: String? = null

        for (garment in garments) {
            val isUrl = garment.imagePath.startsWith("http://") || garment.imagePath.startsWith("https://")
            val url = if (isUrl) {
                garment.imagePath
            } else {
                when (val result = uploadImage(garment.imagePath, "garment_${garment.category}")) {
                    is Result.Success -> result.data
                    is Result.Error -> {
                        Log.e(TAG, "衣物图片上传失败: ${garment.name} - ${result.message}")
                        return Result.error("衣物图片上传失败：${garment.name}")
                    }
                    Result.Loading -> return Result.error("上传失败")
                }
            }

            when (garment.category) {
                "上装" -> topUrl = url
                "下装" -> bottomUrl = url
                "鞋" -> { /* 鞋子不上传给 aitryon API */ }
                else -> {
                    if (topUrl == null) topUrl = url else bottomUrl = url
                }
            }
        }

        if (topUrl == null && bottomUrl == null) {
            Log.e(TAG, "没有可试穿的衣物（至少需要上装或下装）")
            return Result.error("至少需要一件上装或下装才能试穿")
        }

        Log.d(TAG, "上装URL: $topUrl")
        Log.d(TAG, "下装URL: $bottomUrl")

        // 4. 调用 aitryon API 创建异步任务
        onStep?.invoke("正在创建 AI 试衣任务...")
        Log.d(TAG, "步骤2: 创建 AI 试衣任务...")
        val auth = "Bearer $apiKey"
        val tryOnRequest = TryOnRequest(
            model = "aitryon",
            input = TryOnInput(
                person_image_url = personImageUrl,
                top_garment_url = topUrl,
                bottom_garment_url = bottomUrl
            ),
            parameters = TryOnParameters(
                resolution = -1,
                restore_face = true
            )
        )

        val taskResponse = try {
            val raw = api.createTryOnTask(authorization = auth, request = tryOnRequest)
            if (raw.isSuccessful) raw.body() else {
                val errorBody = raw.errorBody()?.string()
                Log.e(TAG, "创建任务失败: HTTP ${raw.code()}, body=$errorBody")
                return Result.error("创建试衣任务失败：HTTP ${raw.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建任务异常", e)
            return Result.error("创建试衣任务异常：${e.message ?: e.javaClass.simpleName}")
        }

        if (taskResponse == null || taskResponse.output == null) {
            Log.e(TAG, "创建任务响应为空")
            return Result.error("创建试衣任务响应为空")
        }

        val taskId = taskResponse.output.task_id
        val taskStatus = taskResponse.output.task_status
        Log.d(TAG, "任务已创建: task_id=$taskId, status=$taskStatus")

        if (taskId.isBlank()) {
            Log.e(TAG, "task_id 为空")
            return Result.error("创建试衣任务失败：未获取到任务ID")
        }

        // 5. 轮询任务结果
        onStep?.invoke("AI 试衣任务已提交，正在等待处理结果...（约15-30秒）")
        Log.d(TAG, "步骤3: 轮询任务结果...")
        val maxAttempts = 60
        val pollInterval = 5000L

        for (attempt in 1..maxAttempts) {
            Log.d(TAG, "轮询第 $attempt 次...")
            kotlinx.coroutines.delay(pollInterval)

            val queryResponse = try {
                val raw = api.queryTryOnTask(authorization = auth, taskId = taskId)
                if (raw.isSuccessful) raw.body() else {
                    Log.e(TAG, "查询任务失败: HTTP ${raw.code()}")
                    return Result.error("查询试衣任务失败：HTTP ${raw.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "查询任务异常", e)
                return Result.error("查询试衣任务异常：${e.message ?: e.javaClass.simpleName}")
            }

            if (queryResponse == null || queryResponse.output == null) {
                Log.e(TAG, "查询任务响应为空")
                return Result.error("查询试衣任务响应为空")
            }

            val currentStatus = queryResponse.output.task_status
            Log.d(TAG, "当前状态: $currentStatus")

            when (currentStatus) {
                "SUCCEEDED" -> {
                    val imageUrl = queryResponse.output.image_url
                    if (imageUrl.isNullOrBlank()) {
                        Log.e(TAG, "任务成功但 image_url 为空")
                        return Result.error("试衣成功但未获取到图片URL")
                    }
                    Log.d(TAG, "试衣成功! image_url: ${imageUrl.take(100)}...")

                    // 转 HTTPS 避免 Android 明文流量限制
                    val httpsUrl = if (imageUrl.startsWith("http://")) {
                        "https://" + imageUrl.removePrefix("http://")
                    } else {
                        imageUrl
                    }

                    // 6. 下载结果图片到本地
                    onStep?.invoke("正在下载试衣结果...")
                    Log.d(TAG, "步骤4: 下载试衣结果...")
                    val localPath = withContext(Dispatchers.IO) {
                        ImageUtils.downloadImage(appContext, httpsUrl, "tryon_result")
                    }
                    if (localPath == null) {
                        Log.e(TAG, "下载试衣结果失败")
                        return Result.error("下载试衣结果图片失败")
                    }
                    Log.d(TAG, "试衣结果已保存到: $localPath")
                    Log.d(TAG, "=== AI 试衣流程完成 ===")
                    return Result.Success(localPath)
                }

                "FAILED" -> {
                    val errorMsg = queryResponse.output.message ?: "未知错误"
                    val errorCode = queryResponse.output.code ?: ""
                    Log.e(TAG, "试衣任务失败: code=$errorCode, message=$errorMsg")
                    return Result.error("试衣任务失败：$errorMsg")
                }

                "CANCELED" -> {
                    Log.e(TAG, "试衣任务已取消")
                    return Result.error("试衣任务已被取消")
                }

                "UNKNOWN" -> {
                    Log.e(TAG, "试衣任务状态未知")
                    return Result.error("试衣任务状态未知")
                }

                else -> {
                    Log.d(TAG, "任务处理中, 当前状态: $currentStatus, 继续等待...")
                }
            }
        }

        Log.e(TAG, "试衣任务超时")
        return Result.error("试衣任务超时，请稍后重试")
    }

    /**
     * 上传图片到后端获取公网 URL。
     */
    private suspend fun uploadImage(localPath: String, prefix: String): Result<String> {
        if (localPath.startsWith("http://") || localPath.startsWith("https://")) {
            return Result.Success(localPath)
        }

        val file = File(localPath)
        if (!file.exists()) {
            return Result.error("图片文件不存在：$localPath")
        }

        // 压缩图片以加速上传（1024px, 80% 质量，约 100-200KB）
        Log.d(TAG, "compressImage: 压缩原图...")
        val compressedPath = ImageUtils.compressImage(localPath, 1024, 80)
        val uploadFile = if (compressedPath != null) File(compressedPath) else file
        Log.d(TAG, "compressImage: 原大小=${file.length()/1024}KB, 压缩后=${uploadFile.length()/1024}KB")

        val settings = settingsDataStore.getTryOnSettings()
        val apiKey = settings.apiKey
        val apiHost = settings.host

        if (apiKey.isBlank() || apiHost.isBlank()) {
            return Result.error("未配置 AI 试衣 API Key 或 Host")
        }

        Log.d(TAG, "uploadImage: 上传图片到试衣服务... host=$apiHost")

        return try {
            // 构建上传 URL（DashScope 标准文件上传端点）
            val scheme = if (apiHost.startsWith("http://") || apiHost.startsWith("https://")) {
                apiHost.substringBefore("://")
            } else {
                "https"
            }
            val hostPart = if (apiHost.contains("://")) apiHost.substringAfter("://") else apiHost
            val cleanHost = if (hostPart.endsWith("/")) hostPart.substringBeforeLast("/") else hostPart
            val uploadUrl = "$scheme://${cleanHost}/api/v1/files"

            // 准备文件 multipart body
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", uploadFile.name, uploadFile.asRequestBody(mediaType))
                .build()

            val request = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "上传失败: HTTP ${response.code}, body=$responseBody")
                return Result.error("图片上传失败：HTTP ${response.code}")
            }

            Log.d(TAG, "上传响应: $responseBody")

            // 解析 file_id
            val json = responseBody?.let { JSONObject(it) }
            val fileId = json?.optJSONObject("data")
                ?.optJSONArray("uploaded_files")
                ?.optJSONObject(0)
                ?.optString("file_id", "")
                ?: ""

            if (fileId.isBlank()) {
                Log.w(TAG, "无法从响应中提取 file_id: $responseBody")
                return Result.error("图片上传成功但无法获取 file_id")
            }

            Log.d(TAG, "文件上传成功, file_id=$fileId")

            // 查询文件信息以获取 URL
            val queryUrl = "${scheme}://${cleanHost}/api/v1/files/$fileId"
            val queryRequest = Request.Builder()
                .url(queryUrl)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val queryResponse = withContext(Dispatchers.IO) {
                okHttpClient.newCall(queryRequest).execute()
            }
            val queryBody = queryResponse.body?.string()
            Log.d(TAG, "文件查询响应: $queryBody")

            if (!queryResponse.isSuccessful) {
                Log.e(TAG, "文件查询失败: HTTP ${queryResponse.code}, body=$queryBody")
                return Result.error("文件查询失败：HTTP ${queryResponse.code}")
            }

            // 从查询结果中提取 URL（data.url 字段）
            val fileJson = queryBody?.let { JSONObject(it) }
            val fileObj = fileJson?.optJSONObject("data")
            val rawUrl = fileObj?.optString("url", "") ?: ""
            // 清除可能带有的反引号和首尾空白
            var fileUrl = rawUrl.trim { it == ' ' || it == '`' || it == '\t' || it == '\n' }
            // OSS URL 转为 HTTPS
            if (fileUrl.startsWith("http://")) {
                fileUrl = "https://" + fileUrl.removePrefix("http://")
            }
            Log.d(TAG, "文件查询结果: rawUrl='$rawUrl', cleanedUrl='$fileUrl'")

            if (fileUrl.isNotBlank()) {
                Result.Success(fileUrl)
            } else {
                Log.w(TAG, "无法从文件查询结果中提取 URL: $queryBody")
                Result.error("文件上传成功但无法获取图片 URL")
            }
        } catch (e: Exception) {
            Log.w(TAG, "上传失败: ${e.message}")
            Result.error("图片上传失败：${e.message ?: e.javaClass.simpleName}")
        }
    }
}

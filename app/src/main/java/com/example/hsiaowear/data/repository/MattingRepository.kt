package com.example.hsiaowear.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.hsiaowear.data.Result
import com.example.hsiaowear.network.VolcengineApi
import com.example.hsiaowear.network.VolcengineMattingRequest
import com.example.hsiaowear.util.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HsiaoWear-VolcMatting"

/**
 * 调用火山引擎通用图像分割（主体分割）API，完成"选择图片 → 抠图 → 保存结果"全流程。
 *
 * API 文档：https://docs.volcengine.com/docs/86081/1660404?lang=zh
 */
@Singleton
class MattingRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val volcengineApi: VolcengineApi
) {

    /**
     * 对本地图片文件执行抠图，返回抠图结果图片的本地路径。
     *
     * @param localFilePath 原始图片的本地路径
     * @return 抠图成功返回本地路径，失败返回 [Result.error]
     */
    suspend fun matting(localFilePath: String): Result<String> {
        Log.d(TAG, "=== 开始抠图流程（火山引擎）===")
        Log.d(TAG, "原始文件路径: $localFilePath")

        // 1. 图片转 Base64
        val base64 = ImageUtils.fileToBase64(localFilePath, maxWidth = 1024, quality = 90)
        if (base64 == null) {
            Log.e(TAG, "图片转 Base64 失败")
            return Result.error("图片读取失败，无法转换为 Base64")
        }
        Log.d(TAG, "Base64 长度: ${base64.length} 字符")

        // 2. 构造请求
        val request = VolcengineMattingRequest(
            binary_data_base64 = listOf(base64),
            only_mask = 3,          // 返回原图大小的 BGRA 透明前景图
            rgb = listOf(-1, -1, -1), // 配合 only_mask=3 返回透明底图
            refine_mask = 2          // 边缘增强（matting large）
        )

        // 3. 调用 API
        return try {
            Log.d(TAG, "正在调用 Volcengine CVProcess API...")
            val startTime = System.currentTimeMillis()
            val rawResponse = volcengineApi.cvProcess(request = request)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "API 返回耗时: ${elapsed}ms")
            Log.d(TAG, "HTTP 状态码: ${rawResponse.code()}")

            val response = if (rawResponse.isSuccessful) {
                rawResponse.body()
            } else {
                val errorBody = rawResponse.errorBody()?.string()
                Log.e(TAG, "HTTP 错误: ${rawResponse.code()}, errorBody=$errorBody")
                return Result.error("抠图 API 请求失败：HTTP ${rawResponse.code()} $errorBody")
            }

            if (response == null) {
                Log.e(TAG, "API 响应体为 null")
                return Result.error("抠图 API 无响应")
            }

            Log.d(TAG, "API 响应: code=${response.code}, message='${response.message}'")

            if (response.code != 10000) {
                Log.e(TAG, "API 返回错误: code=${response.code}, message=${response.message}")
                return Result.error("抠图 API 返回错误：${response.message} (code=${response.code})")
            }

            val data = response.data
            if (data == null) {
                Log.e(TAG, "API 返回 data 为 null")
                return Result.error("抠图 API 返回数据为空")
            }

            // 检查算法状态
            data.algorithm_base_resp?.let { algoResp ->
                if (algoResp.status_code != 0) {
                    Log.e(TAG, "算法错误: status_code=${algoResp.status_code}, message=${algoResp.status_message}")
                    return Result.error("抠图算法错误：${algoResp.status_message}")
                }
            }

            // 4. 获取结果图片 base64
            val resultBase64List = data.binary_data_base64
            if (resultBase64List.isNullOrEmpty()) {
                Log.e(TAG, "binary_data_base64 为空")
                return Result.error("抠图结果中无图片数据")
            }

            val resultBase64 = resultBase64List.first()
            if (resultBase64.isBlank()) {
                Log.e(TAG, "抠图结果 base64 为空")
                return Result.error("抠图结果图片数据为空")
            }

            Log.d(TAG, "结果 base64 长度: ${resultBase64.length} 字符")

            // 5. 解码 base64 并保存为本地文件
            return try {
                val imageBytes = Base64.decode(resultBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap == null) {
                    Log.e(TAG, "Base64 解码为 Bitmap 失败")
                    return Result.error("抠图结果图片解码失败")
                }

                val localPath = ImageUtils.saveBitmap(appContext, bitmap, prefix = "matted")
                Log.d(TAG, "抠图结果保存到: $localPath")
                Log.d(TAG, "=== 抠图流程成功完成 ===")
                Result.Success(localPath)
            } catch (e: Exception) {
                Log.e(TAG, "保存抠图结果失败", e)
                Result.error("保存抠图结果图片失败：${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "抠图 API 调用异常", e)
            Result.error("抠图 API 调用异常：${e.message ?: e.javaClass.simpleName}")
        }
    }
}

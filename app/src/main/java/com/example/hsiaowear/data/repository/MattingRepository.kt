package com.example.hsiaowear.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.util.Log
import com.example.hsiaowear.data.Result
import com.example.hsiaowear.network.VolcengineApi
import com.example.hsiaowear.network.VolcengineMattingRequest
import com.example.hsiaowear.util.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "HsiaoWear-Matting"

// ==================== 在线抠图（火山引擎）====================
/**
 * 调用火山引擎通用图像分割（主体分割）API，完成"选择图片 → 抠图 → 保存结果"全流程。
 *
 * API 文档：https://docs.volcengine.com/docs/86081/1660404?lang=zh
 */

// ==================== 离线抠图（ML Kit 本地推理）====================
/**
 * 使用 Google ML Kit Selfie Segmentation 进行离线抠图。
 * - 通过 Google Play Services 运行，无模型下载
 * - 使用 NPU/DSP 硬件加速
 * - 输入任意尺寸，输出前景掩码
 */

@Singleton
class MattingRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val volcengineApi: VolcengineApi
) {

    // ==================== 在线抠图（火山引擎 API）====================

    /**
     * 对本地图片文件执行在线抠图（火山引擎 API），返回抠图结果图片的本地路径。
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

    // ==================== 离线抠图（ML Kit 本地推理）====================

    // ML Kit Segmenter 实例（线程安全，可复用）
    @Volatile
    private var segmenter: com.google.mlkit.vision.segmentation.Segmenter? = null

    /**
     * 获取或创建 ML Kit Segmenter（延迟初始化）。
     * ML Kit 内部管理模型生命周期，不需要手动下载模型文件。
     */
    private fun getSegmenter(): com.google.mlkit.vision.segmentation.Segmenter {
        val existing = segmenter
        if (existing != null) return existing

        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val newSegmenter = Segmentation.getClient(options)
        segmenter = newSegmenter
        Log.d(TAG, "ML Kit Segmenter 创建成功")
        return newSegmenter
    }

    /**
     * 对本地图片执行离线抠图（使用 Google ML Kit 在手机上本地推理）。
     * 无需下载模型文件，通过 Google Play Services 运行，利用设备 NPU/DSP 加速。
     *
     * @param localFilePath 原始图片的本地路径
     * @return 抠图成功返回本地路径，失败返回 [Result.error]
     */
    suspend fun mattingLocal(localFilePath: String): Result<String> {
        Log.d(TAG, "=== 开始本地离线抠图（ML Kit）===")
        Log.d(TAG, "原始文件路径: $localFilePath")

        return withContext(Dispatchers.IO) {
            try {
                // 1. 读取原图
                val originalBitmap = BitmapFactory.decodeFile(localFilePath)
                if (originalBitmap == null) {
                    Log.e(TAG, "图片解码失败，无法读取文件")
                    return@withContext Result.error("图片读取失败")
                }

                val originalWidth = originalBitmap.width
                val originalHeight = originalBitmap.height
                Log.d(TAG, "原图尺寸: ${originalWidth}x${originalHeight}")

                // 2. 创建 ML Kit InputImage
                val inputImage = InputImage.fromBitmap(originalBitmap, 0)
                Log.d(TAG, "InputImage 创建完成")

                // 3. 获取 Segmenter 并使用 Tasks.await 运行推理（同步风格）
                val seg = getSegmenter()
                val startInfer = System.currentTimeMillis()
                Log.d(TAG, "开始 ML Kit 分割推理...")

                val segmentationMask = try {
                    com.google.android.gms.tasks.Tasks.await(seg.process(inputImage))
                } catch (e: Exception) {
                    Log.e(TAG, "ML Kit 推理失败", e)
                    return@withContext Result.error("ML Kit 抠图失败：${e.message}")
                }

                val inferenceTime = System.currentTimeMillis() - startInfer
                Log.d(TAG, "ML Kit 推理完成，耗时: ${inferenceTime}ms")

                // 4. 读取掩码数据（FloatBuffer）
                val startPostprocess = System.currentTimeMillis()
                val maskBuffer = segmentationMask.buffer
                val maskWidth = segmentationMask.width
                val maskHeight = segmentationMask.height
                Log.d(TAG, "掩码尺寸: ${maskWidth}x${maskHeight}")

                maskBuffer.rewind()

                // 创建掩码 Bitmap
                val maskBitmap = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
                val maskPixels = IntArray(maskWidth * maskHeight)
                for (i in maskPixels.indices) {
                    val confidence = maskBuffer.getFloat()
                    val alpha = (confidence * 255).toInt().coerceIn(0, 255)
                    maskPixels[i] = Color.argb(alpha, 255, 255, 255)
                }
                maskBitmap.setPixels(maskPixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

                // 缩放到原图尺寸
                val scaledMask = Bitmap.createScaledBitmap(maskBitmap, originalWidth, originalHeight, true)
                maskBitmap.recycle()

                // 5. 合成结果图：原图 × mask（alpha 通道）
                val resultBitmap = Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
                val originalPixels = IntArray(originalWidth * originalHeight)
                val maskPixelsScaled = IntArray(originalWidth * originalHeight)
                originalBitmap.getPixels(originalPixels, 0, originalWidth, 0, 0, originalWidth, originalHeight)
                scaledMask.getPixels(maskPixelsScaled, 0, originalWidth, 0, 0, originalWidth, originalHeight)

                for (i in originalPixels.indices) {
                    val origColor = originalPixels[i]
                    val maskAlpha = Color.alpha(maskPixelsScaled[i])
                    if (maskAlpha > 0) {
                        val r = (Color.red(origColor) * maskAlpha / 255)
                        val g = (Color.green(origColor) * maskAlpha / 255)
                        val b = (Color.blue(origColor) * maskAlpha / 255)
                        resultBitmap.setPixel(
                            i % originalWidth,
                            i / originalWidth,
                            Color.argb(maskAlpha, r, g, b)
                        )
                    } else {
                        resultBitmap.setPixel(
                            i % originalWidth,
                            i / originalWidth,
                            Color.TRANSPARENT
                        )
                    }
                }

                originalBitmap.recycle()
                scaledMask.recycle()

                val postprocessTime = System.currentTimeMillis() - startPostprocess
                Log.d(TAG, "后处理完成，耗时: ${postprocessTime}ms")

                // 6. 保存结果（PNG 格式保留透明通道）
                val localPath = ImageUtils.saveBitmap(appContext, resultBitmap, prefix = "matted_local")
                resultBitmap.recycle()

                Log.i(TAG, "=== 本地抠图成功 ===")
                Log.i(TAG, "结果保存到: $localPath")
                Log.i(TAG, "总耗时: ${inferenceTime + postprocessTime}ms (推理: ${inferenceTime}ms, 后处理: ${postprocessTime}ms)")
                Result.Success(localPath)
            } catch (e: Exception) {
                Log.e(TAG, "本地抠图异常", e)
                Result.error("本地抠图失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}

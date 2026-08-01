package com.example.hsiaowear.data.repository

import com.example.hsiaowear.data.ApiSettings
import com.example.hsiaowear.data.Result
import com.example.hsiaowear.data.SettingsDataStore
import com.example.hsiaowear.network.LLMApi
import com.example.hsiaowear.network.LLMMessage
import com.example.hsiaowear.network.LLMRequest
import com.example.hsiaowear.network.LLMResponse
import com.example.hsiaowear.network.OutfitRecommendation
import com.example.hsiaowear.network.ToolDefinition
import com.example.hsiaowear.network.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "HsiaoWear-AIRepo"
    }

    private var llmApi: LLMApi? = null
    private var apiSettings: ApiSettings = ApiSettings()

    init {
        kotlinx.coroutines.GlobalScope.launch {
            settingsDataStore.apiSettingsFlow.collect {
                apiSettings = it
                llmApi = createLLMApi(it)
            }
        }
    }

    private fun createLLMApi(settings: ApiSettings): LLMApi? {
        if (settings.baseUrl.isBlank()) return null

        // 用户可能输入了完整 URL（如 https://api.deepseek.com/v1/chat/completions），
        // 也可能是缺少 / 结尾的 baseUrl，统一提取 origin 部分 + "/"
        val cleanBaseUrl = try {
            val url = java.net.URL(settings.baseUrl)
            // 只保留协议 + 主机 + 端口，忽略用户可能在设置中输入的路径部分
            url.run {
                val portStr = if (port > 0) ":${port}" else ""
                "${protocol}://${host}${portStr}/"
            }
        } catch (e: Exception) {
            // URL 解析失败，至少确保以 / 结尾
            settings.baseUrl.let { if (it.endsWith("/")) it else "$it/" }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer ${settings.apiKey}")
                    .build()
                chain.proceed(request)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(cleanBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LLMApi::class.java)
    }

    /**
     * 测试连接：直接指定 baseUrl + apiKey + modelName，不依赖异步 flow。
     * 如果测试成功还会同步更新内部的 llmApi 供后续使用。
     */
    suspend fun testConnection(baseUrl: String, apiKey: String, modelName: String): Result<Unit> {
        if (baseUrl.isBlank()) return Result.Error(Exception("API Endpoint 为空"))
        if (apiKey.isBlank()) return Result.Error(Exception("API Key 为空"))
        if (modelName.isBlank()) return Result.Error(Exception("Model Name 为空"))

        val tempApi = createLLMApi(ApiSettings(baseUrl = baseUrl, apiKey = apiKey, modelName = modelName))
            ?: return Result.Error(Exception("无法创建 API 客户端"))

        return try {
            val request = LLMRequest(
                model = modelName,
                messages = listOf(LLMMessage(role = "user", content = "hello")),
                temperature = 0.0,
                max_tokens = 1
            )
            tempApi.chatCompletions(request)
            // 测试成功 → 同步更新内部 llmApi，后续聊天可直接使用
            synchronized(this) {
                apiSettings = ApiSettings(baseUrl = baseUrl, apiKey = apiKey, modelName = modelName)
                llmApi = tempApi
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, e.message ?: "连接测试失败：${e.javaClass.simpleName}")
        }
    }

    /**
     * 从当前 API 获取模型列表，返回模型 id 列表。
     * 调用失败或 API 不支持时返回空列表。
     */
    suspend fun fetchModels(): List<String> {
        val api = llmApi ?: return emptyList()
        return try {
            val response = api.listModels()
            response.data?.map { it.id } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getWeather(city: String = "重庆"): Result<WeatherInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedCity = URLEncoder.encode(city, "UTF-8")
                val url = URL("https://uapis.cn/api/v1/misc/weather?city=$encodedCity")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.setRequestProperty("Accept", "application/json")

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = com.google.gson.Gson().fromJson(response, UapisWeatherResponse::class.java)

                val now = LocalDate.now()
                val dateFormatter = DateTimeFormatter.ofPattern("M月d日")
                val weekdays = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
                val weekday = weekdays[now.dayOfWeek.value % 7]

                Result.Success(WeatherInfo(
                    city = json.city,
                    temperature = json.temperature.toString(),
                    description = json.weather,
                    humidity = json.humidity.toString(),
                    wind = "${json.wind_direction} ${json.wind_power}",
                    dateStr = now.format(dateFormatter),
                    weekday = weekday
                ))
            } catch (e: Exception) {
                android.util.Log.e("HsiaoWear-Weather", "获取天气失败", e)
                Result.Error(e, e.message ?: "请求失败：${e.javaClass.simpleName}")
            }
        }
    }

    suspend fun chatWithAI(messages: List<LLMMessage>, tools: List<ToolDefinition>? = null): Result<LLMResponse> {
        val api = llmApi ?: return Result.Error(
            Exception("API 未配置"),
            "API 未配置，请先在设置中填写 API Endpoint 和 API Key"
        )
        return try {
            android.util.Log.d(TAG, "chatWithAI: model=${apiSettings.modelName}, messages=${messages.size}")
            val request = LLMRequest(
                model = apiSettings.modelName,
                messages = messages,
                temperature = apiSettings.temperature,
                max_tokens = apiSettings.maxTokens,
                tools = tools
            )
            val response = api.chatCompletions(request)
            Result.Success(response)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "chatWithAI 失败", e)
            Result.Error(e, e.message ?: "AI 请求异常：${e.javaClass.simpleName}")
        }
    }

    suspend fun getOutfitRecommendation(
        weather: WeatherInfo,
        clothes: List<ClothingRecommendationItem>
    ): Result<OutfitRecommendation> {
        if (clothes.isEmpty()) {
            android.util.Log.w(TAG, "getOutfitRecommendation: 衣橱为空")
            return Result.Error(Exception("衣橱为空"))
        }

        val api = llmApi
        if (api == null) {
            android.util.Log.e(TAG, "getOutfitRecommendation: llmApi = null, API 未配置")
            android.util.Log.e(TAG, "  baseUrl='${apiSettings.baseUrl}', apiKey='${apiSettings.apiKey.take(8)}...'")
            return Result.Error(Exception("API 未配置，请检查设置中的 API Endpoint 和 API Key"))
        }

        android.util.Log.d(TAG, "=== getOutfitRecommendation 开始 ===")
        android.util.Log.d(TAG, "天气: city=${weather.city}, temp=${weather.temperature}°C, desc=${weather.description}")
        android.util.Log.d(TAG, "衣橱共有 ${clothes.size} 件衣服")
        for (c in clothes) {
            android.util.Log.d(TAG, "  衣物: id=${c.id}, name=${c.name}, category=${c.category}, color=${c.color}")
        }
        android.util.Log.d(TAG, "API 配置: baseUrl=${apiSettings.baseUrl}, model=${apiSettings.modelName}")

        val clothesInfo = buildClothesInfo(clothes)
        val prompt = """
你是一个专业的穿搭顾问。用户请求你根据当前天气和衣橱中的衣服，推荐一套今日穿搭。

📍 **当前天气：**
• 城市：${weather.city}
• 日期：${weather.dateStr} ${weather.weekday}
• 温度：${weather.temperature}°C
• 天气：${weather.description}

👔 **用户衣橱中的衣服：**
$clothesInfo

请根据上面的天气信息和衣服，从衣橱中选择最合适的衣服进行搭配。

要求：
1. 推荐必须包含：上装、下装、鞋子（三个都要有）
2. 每件衣服必须从上面衣橱列表中选择（使用ID）
3. 考虑颜色搭配（尽量选择协调的颜色）
4. 用简洁的语言给出推荐理由

请按以下JSON格式返回推荐结果（必须是可以被JSON解析的格式）：
{
  "upper_id": "选择的上装ID（数字）",
  "upper_name": "选择的上装名称",
  "upper_color": "上装颜色",
  "lower_id": "选择的下装ID（数字）",
  "lower_name": "选择的下装名称",
  "lower_color": "下装颜色",
  "shoes_id": "选择的鞋子ID（数字）",
  "shoes_name": "鞋子名称",
  "shoes_color": "鞋子颜色",
  "reason": "推荐理由"
}

直接返回JSON，不要有其他内容。
        """.trimIndent()

        val messages = listOf(
            LLMMessage(
                role = "system",
                content = "你是一个专业的穿搭顾问，擅长根据天气和衣服特点给出搭配建议。"
            ),
            LLMMessage(
                role = "user",
                content = prompt
            )
        )

        return try {
            val request = LLMRequest(
                model = apiSettings.modelName,
                messages = messages,
                temperature = 0.5,
                max_tokens = 1024
            )
            android.util.Log.d(TAG, "发送 LLM 请求: model=${apiSettings.modelName}")
            val response = api.chatCompletions(request)
            android.util.Log.d(TAG, "LLM 响应收到: choices=${response.choices?.size ?: 0}")
            if (response.choices.isNullOrEmpty()) {
                android.util.Log.e(TAG, "LLM 响应 choices 为空或 null")
                android.util.Log.e(TAG, "完整响应: ${com.google.gson.Gson().toJson(response)}")
                return Result.Error(Exception("AI 返回空结果"))
            }
            val content = response.choices.firstOrNull()?.message?.content ?: ""
            android.util.Log.d(TAG, "AI 返回内容 (前 200 字符): ${content.take(200)}")
            android.util.Log.d(TAG, "AI 返回内容 (完整长度): ${content.length} 字符")
            val recommendation = parseOutfitRecommendation(content, clothes)
            android.util.Log.d(TAG, "解析结果: upperId=${recommendation.upperId}, lowerId=${recommendation.lowerId}, shoesId=${recommendation.shoesId}")
            android.util.Log.d(TAG, "推荐理由: ${recommendation.reason.take(100)}")
            Result.Success(recommendation)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getOutfitRecommendation 异常", e)
            Result.Error(e)
        }
    }

    private fun buildClothesInfo(clothes: List<ClothingRecommendationItem>): String {
        val byCategory = clothes.groupBy { it.category }
        val categoryNames = mapOf(
            "上装" to "上装",
            "下装" to "下装",
            "鞋" to "鞋履"
        )

        val sb = StringBuilder()
        for ((cat, items) in byCategory) {
            val catName = categoryNames[cat] ?: cat
            sb.append("**$catName**（${items.size}件）：\n")
            for ((i, item) in items.withIndex()) {
                sb.append("  ${i + 1}. ${item.name}（${item.color}，ID: ${item.id}）\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun parseOutfitRecommendation(content: String, allClothes: List<ClothingRecommendationItem>): OutfitRecommendation {
        try {
            val startIdx = content.indexOf("{")
            val endIdx = content.lastIndexOf("}")
            if (startIdx == -1 || endIdx == -1) {
                android.util.Log.w(TAG, "parseOutfitRecommendation: 找不到 JSON 花括号, content='${content.take(100)}'")
                return getFallbackRecommendation(allClothes)
            }

            val jsonStr = content.substring(startIdx..endIdx)
            android.util.Log.d(TAG, "parseOutfitRecommendation: 提取的 JSON: ${jsonStr.take(200)}")

            val gson = com.google.gson.Gson()
            // ===== 1. 尝试用 Map 方式解析 =====
            val parsed: Map<*, *> = gson.fromJson(jsonStr, Map::class.java)
            android.util.Log.d(TAG, "parseOutfitRecommendation: 解析的 Map: $parsed")

            // 详细日志：打印每个字段的类型和值
            listOf("upper_id", "upper_name", "upper_color", "lower_id", "lower_name", "lower_color", "shoes_id", "shoes_name", "shoes_color", "reason").forEach { key ->
                val v = parsed[key]
                android.util.Log.d(TAG, "  key='$key' value=$v type=${v?.javaClass?.name}")
            }

            // 安全的 ID 解析函数
            fun parseId(value: Any?): Long? {
                if (value == null) return null
                return when (value) {
                    is Number -> value.toLong()
                    is String -> value.toLongOrNull()
                    else -> {
                        android.util.Log.w(TAG, "  parseId 无法识别的类型: ${value.javaClass.name}")
                        null
                    }
                }
            }

            return OutfitRecommendation(
                upperId = parseId(parsed["upper_id"]),
                upperName = parsed["upper_name"]?.toString() ?: "",
                upperColor = parsed["upper_color"]?.toString() ?: "",
                lowerId = parseId(parsed["lower_id"]),
                lowerName = parsed["lower_name"]?.toString() ?: "",
                lowerColor = parsed["lower_color"]?.toString() ?: "",
                shoesId = parseId(parsed["shoes_id"]),
                shoesName = parsed["shoes_name"]?.toString() ?: "",
                shoesColor = parsed["shoes_color"]?.toString() ?: "",
                reason = parsed["reason"]?.toString() ?: ""
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "parseOutfitRecommendation 解析异常", e)
            return getFallbackRecommendation(allClothes)
        }
    }

    private fun getFallbackRecommendation(clothes: List<ClothingRecommendationItem>): OutfitRecommendation {
        android.util.Log.w(TAG, "getFallbackRecommendation: 使用降级推荐，衣橱 ${clothes.size} 件")
        val tops = clothes.filter { it.category == "上装" }
        val bottoms = clothes.filter { it.category == "下装" }
        val shoes = clothes.filter { it.category == "鞋" }
        android.util.Log.d(TAG, "  上装候选: ${tops.size} 件, 下装: ${bottoms.size} 件, 鞋: ${shoes.size} 件")

        return OutfitRecommendation(
            upperId = tops.firstOrNull()?.id,
            upperName = tops.firstOrNull()?.name ?: "",
            upperColor = tops.firstOrNull()?.color ?: "",
            lowerId = bottoms.firstOrNull()?.id,
            lowerName = bottoms.firstOrNull()?.name ?: "",
            lowerColor = bottoms.firstOrNull()?.color ?: "",
            shoesId = shoes.firstOrNull()?.id,
            shoesName = shoes.firstOrNull()?.name ?: "",
            shoesColor = shoes.firstOrNull()?.color ?: "",
            reason = "根据您的衣橱随机推荐一套搭配"
        )
    }
}

data class ClothingRecommendationItem(
    val id: Long,
    val name: String,
    val category: String,
    val color: String,
    val imageUrl: String
)

/** uapis.cn 天气 API 响应体 */
data class UapisWeatherResponse(
    val province: String = "",
    val city: String = "",
    val adcode: String = "",
    val weather: String = "",
    val weather_icon: String = "",
    val temperature: Int = 0,
    val wind_direction: String = "",
    val wind_power: String = "",
    val humidity: Int = 0,
    val report_time: String = "",
    val alerts: List<WeatherAlert>? = null
)

data class WeatherAlert(
    val title: String = "",
    val type: String = "",
    val level: String = "",
    val text: String = "",
    val publish_time: String = "",
    val publisher: String = "",
    val guidance: List<String>? = null
)
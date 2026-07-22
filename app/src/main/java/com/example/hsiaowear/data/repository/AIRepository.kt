package com.example.hsiaowear.data.repository

import android.content.Context
import com.example.hsiaowear.data.ApiSettings
import com.example.hsiaowear.data.Result
import com.example.hsiaowear.data.SettingsDataStore
import com.example.hsiaowear.network.LLMApi
import com.example.hsiaowear.network.LLMMessage
import com.example.hsiaowear.network.LLMRequest
import com.example.hsiaowear.network.LLMResponse
import com.example.hsiaowear.network.OutfitRecommendation
import com.example.hsiaowear.network.ToolCall
import com.example.hsiaowear.network.ToolCallFunction
import com.example.hsiaowear.network.ToolDefinition
import com.example.hsiaowear.network.ToolFunction
import com.example.hsiaowear.network.ToolParameters
import com.example.hsiaowear.network.ToolProperty
import com.example.hsiaowear.network.WeatherInfo
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
) {
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

    suspend fun testConnection(): Result<Unit> {
        val api = llmApi ?: return Result.Error(Exception("API 未配置"))
        return try {
            val request = LLMRequest(
                model = apiSettings.modelName,
                messages = listOf(LLMMessage(role = "user", content = "hello")),
                temperature = 0.0,
                max_tokens = 1
            )
            api.chatCompletions(request)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
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
                Result.Error(e)
            }
        }
    }

    suspend fun chatWithAI(messages: List<LLMMessage>, tools: List<ToolDefinition>? = null): Result<LLMResponse> {
        val api = llmApi ?: return Result.Error(Exception("API 未配置"))
        return try {
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
            Result.Error(e)
        }
    }

    suspend fun getOutfitRecommendation(
        weather: WeatherInfo,
        clothes: List<ClothingRecommendationItem>
    ): Result<OutfitRecommendation> {
        if (clothes.isEmpty()) {
            return Result.Error(Exception("衣橱为空"))
        }

        val api = llmApi ?: return Result.Error(Exception("API 未配置"))

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
            val response = api.chatCompletions(request)
            val content = response.choices.firstOrNull()?.message?.content ?: ""
            val recommendation = parseOutfitRecommendation(content, clothes)
            Result.Success(recommendation)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun buildClothesInfo(clothes: List<ClothingRecommendationItem>): String {
        val byCategory = clothes.groupBy { it.category }
        val categoryNames = mapOf(
            "上衣" to "上装",
            "下装" to "下装",
            "外套" to "外套",
            "连衣裙" to "连衣裙",
            "配饰" to "配饰",
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
                return getFallbackRecommendation(allClothes)
            }

            val jsonStr = content.substring(startIdx..endIdx)
            val gson = com.google.gson.Gson()
            val parsed = gson.fromJson(jsonStr, Map::class.java)

            return OutfitRecommendation(
                upperId = (parsed["upper_id"] as? Number)?.toLong(),
                upperName = parsed["upper_name"]?.toString() ?: "",
                upperColor = parsed["upper_color"]?.toString() ?: "",
                lowerId = (parsed["lower_id"] as? Number)?.toLong(),
                lowerName = parsed["lower_name"]?.toString() ?: "",
                lowerColor = parsed["lower_color"]?.toString() ?: "",
                shoesId = (parsed["shoes_id"] as? Number)?.toLong(),
                shoesName = parsed["shoes_name"]?.toString() ?: "",
                shoesColor = parsed["shoes_color"]?.toString() ?: "",
                reason = parsed["reason"]?.toString() ?: ""
            )
        } catch (e: Exception) {
            return getFallbackRecommendation(allClothes)
        }
    }

    private fun getFallbackRecommendation(clothes: List<ClothingRecommendationItem>): OutfitRecommendation {
        val tops = clothes.filter { it.category == "上衣" || it.category == "外套" }
        val bottoms = clothes.filter { it.category == "下装" || it.category == "连衣裙" }
        val shoes = clothes.filter { it.category == "鞋" }

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
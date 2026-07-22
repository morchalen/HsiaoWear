package com.example.hsiaowear.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface LLMApi {
    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: LLMRequest): LLMResponse

    @POST("completions")
    suspend fun completions(@Body request: LLMCompletionRequest): LLMResponse

    // 获取模型列表（OpenAI 兼容格式）
    @GET("models")
    suspend fun listModels(): ModelsListResponse
}

data class ModelsListResponse(
    val data: List<ModelInfo>? = null,
    val object_field: String? = null
)

data class ModelInfo(
    val id: String,
    val object_field: String? = null,
    val created: Long? = null,
    val owned_by: String? = null
)

data class LLMRequest(
    val model: String,
    val messages: List<LLMMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 2048,
    val tools: List<ToolDefinition>? = null,
    val tool_choice: String? = null
)

data class LLMCompletionRequest(
    val model: String,
    val prompt: String,
    val temperature: Double = 0.7,
    val max_tokens: Int = 2048
)

data class LLMMessage(
    val role: String,
    val content: String,
    val tool_calls: List<ToolCall>? = null,
    val tool_call_id: String? = null
)

data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction
)

data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: ToolParameters
)

data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolProperty>,
    val required: List<String> = emptyList()
)

data class ToolProperty(
    val type: String,
    val description: String
)

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction
)

data class ToolCallFunction(
    val name: String,
    val arguments: String
)

data class LLMResponse(
    val id: String,
    val choices: List<LLMChoice>,
    val usage: LLMUsage?
)

data class LLMChoice(
    val message: LLMMessage,
    val finish_reason: String
)

data class LLMUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

data class WeatherInfo(
    val city: String,
    val temperature: String,
    val description: String,
    val humidity: String,
    val wind: String,
    val dateStr: String,
    val weekday: String
)

data class OutfitRecommendation(
    val upperId: Long? = null,
    val upperName: String = "",
    val upperColor: String = "",
    val lowerId: Long? = null,
    val lowerName: String = "",
    val lowerColor: String = "",
    val shoesId: Long? = null,
    val shoesName: String = "",
    val shoesColor: String = "",
    val reason: String = ""
)
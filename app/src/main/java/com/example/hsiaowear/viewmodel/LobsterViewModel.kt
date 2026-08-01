package com.example.hsiaowear.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hsiaowear.data.Result
import com.example.hsiaowear.data.repository.AIRepository
import com.example.hsiaowear.data.repository.ClothingRepository
import com.example.hsiaowear.data.repository.ClothingRecommendationItem
import com.example.hsiaowear.network.LLMMessage
import com.example.hsiaowear.network.LLMResponse
import com.example.hsiaowear.network.ToolCall
import com.example.hsiaowear.network.ToolCallFunction
import com.example.hsiaowear.network.ToolDefinition
import com.example.hsiaowear.network.ToolFunction
import com.example.hsiaowear.network.ToolParameters
import com.example.hsiaowear.network.ToolProperty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val phase: Int? = null
)

@HiltViewModel
class LobsterViewModel @Inject constructor(
    private val aiRepository: AIRepository,
    private val clothingRepository: ClothingRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _chatHistory = mutableListOf<LLMMessage>()

    init {
        val welcomeMessage = ChatMessage(
            id = System.currentTimeMillis(),
            text = """你好，我是 AI 助手

我可以帮您：

• 查询衣橱统计（我有多少衣服）
• 查看衣服列表（我有哪些衣服）
• 推荐今日穿搭（今天穿什么）
• 清空衣橱

请告诉我您需要什么帮助。""",
            isUser = false
        )
        _messages.value = listOf(welcomeMessage)
    }

    fun sendMessage(text: String) {
        val userMessage = ChatMessage(
            id = System.currentTimeMillis(),
            text = text,
            isUser = true
        )
        _messages.value = _messages.value + userMessage

        _isTyping.value = true

        viewModelScope.launch {
            processUserMessage(text)
        }
    }

    private suspend fun processUserMessage(text: String) {
        _chatHistory.add(LLMMessage(role = "user", content = text))

        val tools = getWardrobeTools()
        val systemPrompt = """
你现在是 SmartWardrobe 系统的智能衣橱助手。

## 你的身份与性格
- 你是用户的智能衣橱助手，性格克制、专业、高效
- 使用中文回复用户

## 你的能力（可用工具）
你有权限调用以下工具来帮助用户：

1. **get_wardrobe_stats** - 查询衣物统计
   - 当用户想了解衣橱情况、衣服数量等统计信息时使用
   - 无需额外参数

2. **list_clothes** - 获取衣服列表
   - 当用户想查看自己有哪些衣服、列出衣服清单时使用
   - 可选参数：category（衣服类别，可选值：上衣、下装、外套、连衣裙、配饰、鞋）
   - 如果用户没有指定类别，返回所有衣服

3. **clear_wardrobe** - 清空衣橱
   - 当用户明确要求删除所有衣物时使用
   - ⚠️ 这是危险操作，需要确认用户意图

4. **recommend_outfit** - 今日穿搭推荐
   - 当用户请求今日穿搭推荐、搭配建议、今天穿什么时使用
   - 会获取当前天气和用户衣橱全部衣服，然后生成穿搭方案

## 执行规则
1. 分析用户输入，选择最合适的 Tool
2. 如果用户提供的信息不足以调用 Tool，直接询问缺失的信息，不要猜测
3. 保持回答简短、直接
4. 如果用户的问题不需要调用任何工具（如闲聊），直接友好回复即可

## 回复格式
- 成功时使用 ✅ 符号
- 失败时使用 ❌ 符号并说明原因
        """.trimIndent()

        try {
            val messages = listOf(LLMMessage(role = "system", content = systemPrompt)) + _chatHistory
            val result = aiRepository.chatWithAI(messages, tools)

            when (result) {
                is Result.Success -> handleAIResponse(result.data)
                is Result.Error -> {
                    addBotMessage("抱歉，AI 服务暂时不可用：${result.message}")
                    _isTyping.value = false
                }
                Result.Loading -> {}
            }
        } catch (e: Exception) {
            addBotMessage("处理请求时出错：${e.message}")
            _isTyping.value = false
        }
    }

    private fun getWardrobeTools(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                function = ToolFunction(
                    name = "get_wardrobe_stats",
                    description = "当用户想了解自己的衣橱情况，如衣服总数、各类别数量等统计信息时调用此工具。触发词包括：多少衣服、衣橱统计、衣物数量、我有几件衣服等。",
                    parameters = ToolParameters(
                        properties = emptyMap(),
                        required = emptyList()
                    )
                )
            ),
            ToolDefinition(
                function = ToolFunction(
                    name = "list_clothes",
                    description = "当用户想查看自己有哪些衣服、列出衣服清单，或询问某类别的衣服时使用。触发词包括：有哪些衣服、看看我的衣服、列出衣服、我的上衣有哪些等。",
                    parameters = ToolParameters(
                        properties = mapOf(
                            "category" to ToolProperty(
                                type = "string",
                                description = "衣服类别，可选值：上衣、下装、外套、连衣裙、配饰、鞋。如果用户没有指定可不填。"
                            )
                        ),
                        required = emptyList()
                    )
                )
            ),
            ToolDefinition(
                function = ToolFunction(
                    name = "clear_wardrobe",
                    description = "当用户明确要求删除、清空衣橱中的所有衣物时调用此工具。⚠️ 这是一个不可逆的危险操作，只有在用户明确表达清空意图时才调用。触发词包括：清空衣橱、删除所有衣服、清空衣柜等。",
                    parameters = ToolParameters(
                        properties = emptyMap(),
                        required = emptyList()
                    )
                )
            ),
            ToolDefinition(
                function = ToolFunction(
                    name = "recommend_outfit",
                    description = "当用户请求今日穿搭推荐、搭配建议、今天穿什么、帮我选衣服等时使用。会获取当前天气和用户衣橱全部衣服，然后生成穿搭方案。",
                    parameters = ToolParameters(
                        properties = emptyMap(),
                        required = emptyList()
                    )
                )
            )
        )
    }

    private suspend fun handleAIResponse(response: LLMResponse) {
        val choice = response.choices.firstOrNull() ?: run {
            addBotMessage("AI 未返回有效响应")
            _isTyping.value = false
            return
        }

        val message = choice.message

        if (message.tool_calls?.isNotEmpty() == true) {
            handleToolCall(message.tool_calls.first())
        } else {
            addBotMessage(message.content)
            _chatHistory.add(LLMMessage(role = "assistant", content = message.content))
            _isTyping.value = false
        }
    }

    private suspend fun handleToolCall(toolCall: ToolCall) {
        val toolName = toolCall.function.name
        val argsJson = toolCall.function.arguments

        addBotMessage(buildPhase1Message(toolName, argsJson), phase = 1)

        try {
            val result = executeTool(toolName, argsJson)
            addBotMessage(result, phase = 2)

            _chatHistory.add(
                LLMMessage(
                    role = "assistant",
                    content = "",
                    tool_calls = listOf(toolCall)
                )
            )
            _chatHistory.add(
                LLMMessage(
                    role = "tool",
                    content = result,
                    tool_call_id = toolCall.id
                )
            )

            summarizeResult(toolName, result)
        } catch (e: Exception) {
            addBotMessage("❌ 执行失败：${e.message}", phase = 2)
        }

        _isTyping.value = false
    }

    private fun buildPhase1Message(toolName: String, argsJson: String): String {
        val nameCN = when (toolName) {
            "get_wardrobe_stats" -> "查询衣物统计"
            "list_clothes" -> "获取衣服列表"
            "clear_wardrobe" -> "清空衣橱"
            "recommend_outfit" -> "今日穿搭推荐"
            else -> toolName
        }

        val sb = StringBuilder()
        sb.append("正在$nameCN")

        if (argsJson.isNotBlank() && argsJson != "{}") {
            try {
                val gson = com.google.gson.Gson()
                val args = gson.fromJson(argsJson, Map::class.java)
                val details = args.map { (key, value) ->
                    val label = when (key) {
                        "category" -> "类别"
                        else -> key
                    }
                    "$label：$value"
                }
                if (details.isNotEmpty()) {
                    sb.append("（${details.joinToString("，")}）")
                }
            } catch (_: Exception) {
            }
        }

        sb.append("…")
        return sb.toString()
    }

    private suspend fun executeTool(toolName: String, argsJson: String): String {
        return when (toolName) {
            "get_wardrobe_stats" -> executeGetWardrobeStats()
            "list_clothes" -> executeListClothes(argsJson)
            "clear_wardrobe" -> executeClearWardrobe()
            "recommend_outfit" -> executeRecommendOutfit()
            else -> "未知工具：$toolName"
        }
    }

    private suspend fun executeGetWardrobeStats(): String {
        val clothes = clothingRepository.getAllClothes().first()
        val total = clothes.size
        val byCategory = clothes.groupBy { it.category }

        val sb = StringBuilder()
        sb.append("✅ 您的衣橱统计：\n\n")
        sb.append("👕 总衣物数：$total 件\n\n")

        for ((cat, items) in byCategory) {
            val icon = getCategoryIcon(cat)
            sb.append("$icon $cat：${items.size} 件\n")
        }

        if (total == 0) {
            sb.append("\n您的衣橱还是空的，快去添加几件衣服吧！")
        }

        return sb.toString()
    }

    private suspend fun executeListClothes(argsJson: String): String {
        var category: String? = null
        try {
            val gson = com.google.gson.Gson()
            val args = gson.fromJson(argsJson, Map::class.java)
            category = args["category"]?.toString()
        } catch (_: Exception) {
        }

        val clothes = if (category.isNullOrBlank()) {
            clothingRepository.getAllClothes().first()
        } else {
            clothingRepository.getClothesByCategory(category).first()
        }

        if (clothes.isEmpty()) {
            return "您的衣橱中还没有任何衣服哦~\n\n快去添加几件衣服吧！"
        }

        val categoryNames = mapOf(
            "上装" to "上装",
            "下装" to "下装",
            "鞋" to "鞋履"
        )

        val byCategory = clothes.groupBy { it.category }

        val sb = StringBuilder()
        sb.append("✅ 您共有 ${clothes.size} 件衣服：\n\n")

        for ((cat, items) in byCategory) {
            val catName = categoryNames[cat] ?: cat
            val icon = getCategoryIcon(cat)
            sb.append("$icon $catName（${items.size}件）：\n")
            for ((i, c) in items.withIndex()) {
                sb.append("   ${i + 1}. ${c.name}（${c.color}）\n")
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    private suspend fun executeClearWardrobe(): String {
        val clothes = clothingRepository.getAllClothes().first()
        val deletedCount = clothes.size

        for (clothing in clothes) {
            clothingRepository.deleteClothingById(clothing.id)
        }

        return "✅ 衣橱已成功清空！\n\n🗑️ 删除了 $deletedCount 件衣物\n\n您的衣橱现在是空的了~"
    }

    private suspend fun executeRecommendOutfit(): String {
        val weatherResult = aiRepository.getWeather()
        val weather = if (weatherResult is Result.Success) {
            weatherResult.data
        } else {
            return "无法获取天气信息，请检查网络连接。"
        }

        val clothes = clothingRepository.getAllClothes().first()
        if (clothes.isEmpty()) {
            return "您的衣橱还是空的，没有衣服可以搭配哦~\n\n快去添加几件衣服吧！"
        }

        val clothingItems = clothes.map {
            ClothingRecommendationItem(
                id = it.id,
                name = it.name,
                category = it.category,
                color = it.color,
                imageUrl = it.imageUrl
            )
        }

        val recommendationResult = aiRepository.getOutfitRecommendation(weather, clothingItems)
        return if (recommendationResult is Result.Success) {
            val rec = recommendationResult.data
            """今日穿搭推荐：

📍 今日天气：${weather.city} ${weather.temperature}°C ${weather.description}

👕 上装：${rec.upperName}（${rec.upperColor}）
👖 下装：${rec.lowerName}（${rec.lowerColor}）
👟 鞋子：${rec.shoesName}（${rec.shoesColor}）

推荐理由：${rec.reason}"""
        } else {
            "生成穿搭推荐时出错：${(recommendationResult as Result.Error).message}"
        }
    }

    private suspend fun summarizeResult(toolName: String, result: String) {
        if (toolName == "list_clothes") {
            return
        }

        try {
            val systemPrompt = "你是一个简洁的总结助手，请用一句话总结以下结果："
            val messages = listOf(
                LLMMessage(role = "system", content = systemPrompt),
                LLMMessage(role = "user", content = result)
            )
            val summaryResult = aiRepository.chatWithAI(messages)
            if (summaryResult is Result.Success) {
                val summary = summaryResult.data.choices.firstOrNull()?.message?.content
                if (!summary.isNullOrBlank()) {
                    addBotMessage("💡 $summary")
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun addBotMessage(text: String, phase: Int? = null) {
        val message = ChatMessage(
            id = System.currentTimeMillis(),
            text = text,
            isUser = false,
            phase = phase
        )
        _messages.value = _messages.value + message
    }

    private fun getCategoryIcon(category: String): String {
        return when (category) {
            "上装" -> "👕"
            "下装" -> "👖"
            "鞋" -> "👟"
            else -> "👔"
        }
    }
}
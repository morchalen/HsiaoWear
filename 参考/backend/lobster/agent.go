package lobster

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math/rand"
	"net/http"
	"strings"
	"time"
)

// Agent 龙虾 AI 助手核心结构体
type Agent struct {
	apiKey     string
	baseURL    string
	model      string
	httpClient *http.Client
	DB         DBInterface // 数据库接口，用于执行实际操作（导出以便 handler 访问）
}

// DBInterface 定义数据库操作接口
type DBInterface interface {
	GetUserByID(id string) (User, error)
	UpdatePassword(userID, newPassword string) error
	GetClothingStats(userID string) (ClothingStats, error)
	ClearWardrobe(userID string) (int64, error)
	ListClothes(userID string, category string) ([]Clothing, error)
}

// User 用户信息结构
type User struct {
	ID        string
	Email     string
	Role      string
	IsActive  bool
	CreatedAt time.Time
}

// ClothingStats 衣物统计
type ClothingStats struct {
	Total         int64
	ThinWardrobe  int64
	ThickWardrobe int64
}

// Clothing 衣物信息
type Clothing struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Category string `json:"category"`
	Color    string `json:"color"`
	ImageURL string `json:"image_url"`
	Seasons  string `json:"seasons"`
}

// NewAgent 创建新的龙虾 Agent 实例
func NewAgent(apiKey, baseURL, model string, db DBInterface) *Agent {
	return &Agent{
		apiKey:  apiKey,
		baseURL: baseURL,
		model:   model,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
		DB: db,
	}
}

// SystemPrompt 系统提示词（赋予龙虾灵魂）
const SystemPrompt = `
你现在是 SmartWardrobe 系统的核心智能中枢，代号"衣柜龙虾"（Lobster）🦞。

## 你的身份与性格
- 你是用户的智能衣橱助手，性格克制、专业、高效
- 带有极简主义的科技感，回答简洁明了
- 使用中文回复用户

## 你的能力（可用工具）
你有权限调用以下工具来帮助用户：

1. **change_password** - 修改登录密码
   - 当用户想要修改、重置或更新密码时使用
   - 必须从用户话语中提取：当前密码(current_password)、新密码(new_password)

2. **get_profile** - 查看个人信息
   - 当用户想了解自己的账户信息时使用
   - 无需额外参数

3. **get_wardrobe_stats** - 查询衣物统计
   - 当用户想了解衣橱情况、衣服数量等统计信息时使用
   - 无需额外参数

4. **list_clothes** - 获取衣服列表
   - 当用户想查看自己有哪些衣服、列出衣服清单时使用
   - 可选参数：category（衣服类别），可选值包括：tops(上装)、bottoms(下装)、shoes(鞋履)、accessories(配饰)
   - 如果用户没有指定类别，返回所有衣服

5. **clear_wardrobe** - 清空衣橱
   - 当用户明确要求删除所有衣物时使用
   - ⚠️ 这是危险操作，需要确认用户意图

## 执行规则
1. 分析用户输入，选择最合适的 Tool
2. 如果用户提供的信息不足以调用 Tool（如改密码没提供旧密码），**直接询问**缺失的信息，不要猜测
3. 保持回答简短、直接，不要使用过多的语气词
4. 如果用户的问题不需要调用任何工具（如闲聊），直接友好回复即可
5. 工具执行成功后，用自然语言总结结果告知用户

## 回复格式
- 成功时使用 ✅ 符号
- 失败时使用 ❌ 符号并说明原因
- 等待信息时使用 ⏳ 符号
`

// ChatMessage 消息项
type ChatMessage struct {
	Role       string     `json:"role"`
	Content    string     `json:"content"`
	ToolCalls  []ToolCall `json:"tool_calls,omitempty"`
	ToolCallID string     `json:"tool_call_id,omitempty"`
}

// ToolCall 工具调用请求
type ToolCall struct {
	ID       string            `json:"id"`
	Type     string            `json:"type"`
	Function *ToolCallFunction `json:"function"`
}

// ToolCallFunction 函数调用详情
type ToolCallFunction struct {
	Name      string          `json:"name"`
	Arguments json.RawMessage `json:"arguments"`
}

// ChatRequest Kimi API 请求结构
type ChatRequest struct {
	Model       string        `json:"model"`
	Messages    []ChatMessage `json:"messages"`
	Temperature float64       `json:"temperature"`
	Tools       []Tool        `json:"tools,omitempty"`
}

// ChatResponse Kimi API 响应结构
type ChatResponse struct {
	ID      string   `json:"id"`
	Choices []Choice `json:"choices"`
	Usage   Usage    `json:"usage"`
}

// Choice 选择项
type Choice struct {
	Message      ResponseMessage `json:"message"`
	FinishReason string          `json:"finish_reason"`
}

// ResponseMessage 响应消息
type ResponseMessage struct {
	Content   string     `json:"content"`
	ToolCalls []ToolCall `json:"tool_calls,omitempty"`
}

// Usage Token 使用量
type Usage struct {
	PromptTokens     int `json:"prompt_tokens"`
	CompletionTokens int `json:"completion_tokens"`
	TotalTokens      int `json:"total_tokens"`
}

// CallLLM 调用大模型
func (a *Agent) CallLLM(ctx context.Context, messages []ChatMessage, tools []Tool) (*ChatResponse, error) {
	reqBody := ChatRequest{
		Model:       a.model,
		Messages:    messages,
		Temperature: 0.7,
	}

	if len(tools) > 0 {
		reqBody.Tools = tools
	}

	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("序列化请求失败: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, "POST", a.baseURL+"/chat/completions", bytes.NewBuffer(jsonData))
	if err != nil {
		return nil, fmt.Errorf("创建请求失败: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+a.apiKey)

	resp, err := a.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("发送请求失败: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("读取响应失败: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("API 错误 (%d): %s", resp.StatusCode, string(body))
	}

	var chatResp ChatResponse
	if err := json.Unmarshal(body, &chatResp); err != nil {
		return nil, fmt.Errorf("解析响应失败: %w", err)
	}

	return &chatResp, nil
}

// ProcessUserMessage 处理用户消息（主入口）
func (a *Agent) ProcessUserMessage(ctx context.Context, userMessage string, userID string) (*LobsterResponse, error) {
	// 构建消息历史
	messages := []ChatMessage{
		{Role: "system", Content: SystemPrompt},
		{Role: "user", Content: userMessage},
	}

	// 第一轮调用 LLM（带 Tools）
	resp, err := a.CallLLM(ctx, messages, GetTools())
	if err != nil {
		return nil, fmt.Errorf("LLM 调用失败: %w", err)
	}

	if len(resp.Choices) == 0 {
		return nil, fmt.Errorf("LLM 未返回有效响应")
	}

	choice := resp.Choices[0].Message

	// 检查是否有 Tool Call
	if len(choice.ToolCalls) > 0 {
		// 有工具调用 -> 两阶段响应
		return a.handleToolCall(ctx, messages, choice, userID)
	}

	// 无工具调用 -> 直接返回 LLM 的文本回复
	return &LobsterResponse{
		Intent: IntentInfo{
			Type:       "chat",
			Name:       "普通对话",
			Confidence: 1.0,
		},
		Phase1Message: "",
		Phase2Message: choice.Content,
		ExecutionResult: ExecutionResult{
			Success: true,
			Message: choice.Content,
		},
	}, nil
}

// handleToolCall 处理工具调用（两阶段响应）
func (a *Agent) handleToolCall(ctx context.Context, messages []ChatMessage, msg ResponseMessage, userID string) (*LobsterResponse, error) {
	toolCall := msg.ToolCalls[0]

	// 解析参数 - 兼容字符串和对象两种格式
	var args map[string]interface{}
	argsRaw := toolCall.Function.Arguments

	// 检查是否是字符串格式（某些 API 会返回字符串而非 JSON 对象）
	if len(argsRaw) > 0 && argsRaw[0] == '"' {
		// 是 JSON 字符串，需要再次解析
		var argsStr string
		if err := json.Unmarshal(argsRaw, &argsStr); err != nil {
			return nil, fmt.Errorf("解析工具参数失败(字符串解码): %w", err)
		}
		if err := json.Unmarshal([]byte(argsStr), &args); err != nil {
			return nil, fmt.Errorf("解析工具参数失败(JSON解析): %w", err)
		}
	} else {
		// 直接是 JSON 对象
		if err := json.Unmarshal(argsRaw, &args); err != nil {
			return nil, fmt.Errorf("解析工具参数失败: %w", err)
		}
	}

	// 构建 Phase 1 消息（意图确认）
	phase1Msg := buildPhase1FromToolCall(toolCall.Function.Name, args)

	// 执行工具
	result, err := ExecuteTool(toolCall.Function.Name, args, a.DB, userID)
	if err != nil {
		return &LobsterResponse{
			Intent: IntentInfo{
				Type:       toolCall.Function.Name,
				Name:       getToolNameCN(toolCall.Function.Name),
				Confidence: 0.9,
			},
			ExtractedParams: convertArgsToStringMap(args),
			Phase1Message:   phase1Msg,
			Phase2Message:   fmt.Sprintf("❌ 执行失败：%s", err.Error()),
			ExecutionResult: ExecutionResult{
				Success: false,
				Message: err.Error(),
			},
		}, nil
	}

	// 某些工具（如 list_clothes）直接返回详细结果，不需要 LLM 总结
	// 避免 LLM 把详细列表总结成统计信息
	if toolCall.Function.Name == "list_clothes" {
		return &LobsterResponse{
			Intent: IntentInfo{
				Type:       toolCall.Function.Name,
				Name:       getToolNameCN(toolCall.Function.Name),
				Confidence: 0.95,
			},
			ExtractedParams: convertArgsToStringMap(args),
			Phase1Message:   phase1Msg,
			Phase2Message:   result,
			ExecutionResult: ExecutionResult{
				Success: true,
				Message: result,
			},
		}, nil
	}

	// 将工具结果追加到消息历史
	messages = append(messages,
		ChatMessage{Role: "assistant", Content: "", ToolCalls: []ToolCall{toolCall}},
		ChatMessage{Role: "tool", Content: result, ToolCallID: toolCall.ID},
	)
	log.Printf("🔄 [agent] 准备进行第二轮 LLM 调用")
	// 第二轮调用 LLM（让 LLM 总结结果）
	resp2, err := a.CallLLM(ctx, messages, nil)
	if err != nil {
		log.Printf("❌ [agent] 第二轮 LLM 调用失败：%v", err)
		// 如果 LLM 总结失败，直接使用原始结果
		return &LobsterResponse{
			Intent: IntentInfo{
				Type:       toolCall.Function.Name,
				Name:       getToolNameCN(toolCall.Function.Name),
				Confidence: 0.9,
			},
			ExtractedParams: convertArgsToStringMap(args),
			Phase1Message:   phase1Msg,
			Phase2Message:   result,
			ExecutionResult: ExecutionResult{
				Success: true,
				Message: result,
			},
		}, nil
	}
	log.Printf("✅ [agent] 第二轮 LLM 调用成功，结果：%+v", resp2.Choices[0].Message.Content)

	phase2Msg := resp2.Choices[0].Message.Content
	if phase2Msg == "" {
		phase2Msg = result
	}

	return &LobsterResponse{
		Intent: IntentInfo{
			Type:       toolCall.Function.Name,
			Name:       getToolNameCN(toolCall.Function.Name),
			Confidence: 0.95,
		},
		ExtractedParams: convertArgsToStringMap(args),
		Phase1Message:   phase1Msg,
		Phase2Message:   phase2Msg,
		ExecutionResult: ExecutionResult{
			Success: true,
			Message: result,
		},
	}, nil
}

// 辅助函数
func buildPhase1FromToolCall(toolName string, args map[string]interface{}) string {
	nameCN := getToolNameCN(toolName)
	msg := fmt.Sprintf("🦞 我理解到您的意图是【%s】\n\n", nameCN)

	if len(args) > 0 {
		msg += "提取到的信息：\n"
		for key, value := range args {
			label := getParamLabel(key)
			msg += fmt.Sprintf("• %s：%v\n", label, value)
		}
		msg += "\n"
	}

	msg += "我将调用相应的 API 为您执行此操作，请稍候..."
	return msg
}

func convertArgsToStringMap(args map[string]interface{}) map[string]string {
	result := make(map[string]string)
	for k, v := range args {
		if str, ok := v.(string); ok {
			result[k] = str
		} else {
			result[k] = fmt.Sprintf("%v", v)
		}
	}
	return result
}

func getToolNameCN(toolType string) string {
	names := map[string]string{
		"change_password":    "修改密码",
		"get_profile":        "查看个人信息",
		"get_wardrobe_stats": "查询衣物统计",
		"list_clothes":       "获取衣服列表",
		"clear_wardrobe":     "清空衣橱",
	}
	if name, ok := names[toolType]; ok {
		return name
	}
	return toolType
}

// GetSmartOutfitRecommendation 获取AI智能穿搭推荐（通过AI分析天气+衣橱+颜色搭配）
func (a *Agent) GetSmartOutfitRecommendation(userID string) (*OutfitRecommendationResult, error) {
	log.Printf("[智能推荐] 开始为用户 %v 获取AI穿搭推荐", userID)

	weather, err := getWeather("重庆")
	if err != nil {
		weather = &WeatherInfo{
			City:        "重庆",
			Temperature: "24°C",
			Description: "晴朗",
			Humidity:    "50%",
			DateStr:     time.Now().Format("1月2日"),
		}
	}
	log.Printf("[智能推荐] 天气信息: %+v", weather)

	clothes, err := a.DB.ListClothes(userID, "")
	if err != nil {
		return nil, fmt.Errorf("获取衣橱失败: %v", err)
	}
	log.Printf("[智能推荐] 获取到 %d 件衣服", len(clothes))

	if len(clothes) == 0 {
		return nil, fmt.Errorf("衣橱为空，请先添加衣服")
	}

	categoryNames := map[string]string{
		"tops":        "上装",
		"bottoms":     "下装",
		"shoes":       "鞋履",
		"outerwear":   "外套",
		"accessories": "配饰",
	}

	byCategory := make(map[string][]Clothing)
	for _, c := range clothes {
		catName := categoryNames[c.Category]
		if catName == "" {
			catName = c.Category
		}
		byCategory[catName] = append(byCategory[catName], c)
	}

	var clothesInfo string
	for catName, items := range byCategory {
		if len(items) == 0 {
			continue
		}
		clothesInfo += fmt.Sprintf("**%s**（%d件）：\n", catName, len(items))
		for i, item := range items {
			clothesInfo += fmt.Sprintf("  %d. %s（%s，ID: %s）\n", i+1, item.Name, item.Color, item.ID)
		}
		clothesInfo += "\n"
	}

	prompt := fmt.Sprintf(`
你是一个专业的穿搭顾问。用户请求你根据当前天气和衣橱中的衣服，推荐一套今日穿搭。

📍 **当前天气：**
• 城市：%s
• 日期：%s
• 温度：%s
• 天气：%s

👔 **用户衣橱中的衣服：**
%s

请根据上面的天气信息和衣服，从衣橱中选择最合适的衣服进行搭配。

要求：
1. 推荐必须包含：上装、下装、鞋子（三个都要有）
2. 不需要外套（除非天气很冷）
3. 每件衣服必须从上面衣橱列表中选择（使用ID）
4. 考虑当前季节（现在是5月初，属于春季）
5. 考虑颜色搭配（尽量选择协调的颜色）
6. 用简洁的语言给出推荐理由

请按以下JSON格式返回推荐结果（必须是可以被JSON解析的格式）：
{
  "upper_id": "选择的上装ID",
  "upper_name": "选择的上装名称",
  "upper_color": "上装颜色",
  "lower_id": "选择的下装ID",
  "lower_name": "选择的下装名称",
  "lower_color": "下装颜色",
  "shoes_id": "选择的鞋子ID",
  "shoes_name": "鞋子名称",
  "shoes_color": "鞋子颜色",
  "reason": "推荐理由"
}

直接返回JSON，不要有其他内容。
`, weather.City, weather.DateStr, weather.Temperature, weather.Description, clothesInfo)

	messages := []ChatMessage{
		{Role: "system", Content: "你是一个专业的穿搭顾问，擅长根据天气和衣服特点给出搭配建议。"},
		{Role: "user", Content: prompt},
	}

	resp, err := a.CallLLM(context.Background(), messages, nil)
	if err != nil {
		return nil, fmt.Errorf("调用AI失败: %v", err)
	}

	aiContent := resp.Choices[0].Message.Content
	log.Printf("[智能推荐] AI回复: %s", aiContent)

	// 解析AI回复，提取衣服ID
	result, err := parseAIOutfitResponse(aiContent, clothes)
	if err != nil {
		log.Printf("[智能推荐] 解析AI回复失败: %v，尝试使用随机推荐", err)
		// 如果解析失败，回退到随机推荐
		return getRandomRecommendation(clothes), nil
	}

	log.Printf("[智能推荐] ✅ AI推荐成功: 上装=%s, 下装=%s, 鞋子=%s",
		result.Upper.Name, result.Lower.Name, result.Feet.Name)
	return result, nil
}

// parseAIOutfitResponse 解析AI的回复，提取推荐的衣服
func parseAIOutfitResponse(aiContent string, allClothes []Clothing) (*OutfitRecommendationResult, error) {
	// 尝试提取JSON
	startIdx := strings.Index(aiContent, "{")
	endIdx := strings.LastIndex(aiContent, "}")
	if startIdx == -1 || endIdx == -1 {
		return nil, fmt.Errorf("未找到JSON格式")
	}

	jsonStr := aiContent[startIdx : endIdx+1]

	var resp struct {
		UpperID   string `json:"upper_id"`
		UpperName string `json:"upper_name"`
		LowerID   string `json:"lower_id"`
		LowerName string `json:"lower_name"`
		ShoesID   string `json:"shoes_id"`
		ShoesName string `json:"shoes_name"`
		Reason    string `json:"reason"`
	}

	if err := json.Unmarshal([]byte(jsonStr), &resp); err != nil {
		return nil, fmt.Errorf("JSON解析失败: %v", err)
	}

	// 通过ID查找衣服
	clothesMap := make(map[string]Clothing)
	for _, c := range allClothes {
		clothesMap[c.ID] = c
	}

	result := &OutfitRecommendationResult{}

	if resp.UpperID != "" {
		if c, ok := clothesMap[resp.UpperID]; ok {
			result.Upper = &c
		}
	}
	if resp.LowerID != "" {
		if c, ok := clothesMap[resp.LowerID]; ok {
			result.Lower = &c
		}
	}
	if resp.ShoesID != "" {
		if c, ok := clothesMap[resp.ShoesID]; ok {
			result.Feet = &c
		}
	}

	// 如果有任何一个没找到，通过名称匹配
	if result.Upper == nil && resp.UpperName != "" {
		for _, c := range allClothes {
			if c.Name == resp.UpperName {
				result.Upper = &c
				break
			}
		}
	}
	if result.Lower == nil && resp.LowerName != "" {
		for _, c := range allClothes {
			if c.Name == resp.LowerName {
				result.Lower = &c
				break
			}
		}
	}
	if result.Feet == nil && resp.ShoesName != "" {
		for _, c := range allClothes {
			if c.Name == resp.ShoesName {
				result.Feet = &c
				break
			}
		}
	}

	if result.Upper == nil || result.Lower == nil || result.Feet == nil {
		return nil, fmt.Errorf("未能完整匹配推荐的衣服")
	}

	return result, nil
}

// getRandomRecommendation 获取随机推荐（回退方案）
func getRandomRecommendation(clothes []Clothing) *OutfitRecommendationResult {
	categoryNames := map[string]string{
		"tops":    "上装",
		"bottoms": "下装",
		"shoes":   "鞋履",
	}

	var tops, bottoms, shoes []Clothing
	for _, c := range clothes {
		catName := categoryNames[c.Category]
		if catName == "上装" {
			tops = append(tops, c)
		} else if catName == "下装" {
			bottoms = append(bottoms, c)
		} else if catName == "鞋履" {
			shoes = append(shoes, c)
		}
	}

	result := &OutfitRecommendationResult{}
	rng := rand.New(rand.NewSource(time.Now().UnixNano()))

	if len(tops) > 0 {
		idx := rng.Intn(len(tops))
		result.Upper = &tops[idx]
	}
	if len(bottoms) > 0 {
		idx := rng.Intn(len(bottoms))
		result.Lower = &bottoms[idx]
	}
	if len(shoes) > 0 {
		idx := rng.Intn(len(shoes))
		result.Feet = &shoes[idx]
	}

	return result
}

func getParamLabel(field string) string {
	labels := map[string]string{
		"current_password": "当前密码",
		"new_password":     "新密码",
		"email":            "邮箱",
	}
	if label, ok := labels[field]; ok {
		return label
	}
	return field
}

// RateOutfit 对穿搭进行评分（1-10分）
func (a *Agent) RateOutfit(userID, upperName, lowerName, feetName, upperColor, lowerColor, feetColor string) (*OutfitRatingResult, error) {
	log.Printf("[穿搭评分] 开始为用户 %v 评分穿搭", userID)

	prompt := fmt.Sprintf(`
你是一个专业的时尚穿搭评分专家。请根据以下穿搭组合进行评分。

👔 **穿搭组合：**
• 上装：%s（%s）
• 下装：%s（%s）
• 鞋履：%s（%s）

请从以下维度进行评分（满分10分）：
1. 颜色搭配协调度：颜色之间是否和谐
2. 风格一致性：整体风格是否统一
3. 场合适配性：适合什么场合
4. 季节适配性：是否适合当前季节（春季）

评分标准：
- 1-3分：较差，明显不协调
- 4-6分：一般，有改进空间
- 7-8分：良好，搭配得体
- 9-10分：优秀，完美搭配

请按以下JSON格式返回评分结果：
{
  "score": 8,
  "breakdown": {
    "color_harmony": 8,
    "style_coherence": 7,
    "occasion_fit": 8,
    "season_fit": 9
  },
  "reason": "评分理由，说明为什么打这个分数",
  "suggestion": "改进建议（如果有）"
}

直接返回JSON，不要有其他内容。
`, upperName, upperColor, lowerName, lowerColor, feetName, feetColor)

	messages := []ChatMessage{
		{Role: "system", Content: "你是一个专业的时尚穿搭评分专家，擅长评估穿搭搭配的协调性和美感。"},
		{Role: "user", Content: prompt},
	}

	resp, err := a.CallLLM(context.Background(), messages, nil)
	if err != nil {
		return nil, fmt.Errorf("调用AI评分失败: %v", err)
	}

	aiContent := resp.Choices[0].Message.Content
	log.Printf("[穿搭评分] AI回复: %s", aiContent)

	// 尝试解析JSON
	startIdx := strings.Index(aiContent, "{")
	endIdx := strings.LastIndex(aiContent, "}")
	if startIdx == -1 || endIdx == -1 {
		return nil, fmt.Errorf("未找到JSON格式")
	}

	jsonStr := aiContent[startIdx : endIdx+1]

	var result OutfitRatingResult
	if err := json.Unmarshal([]byte(jsonStr), &result); err != nil {
		return nil, fmt.Errorf("JSON解析失败: %v", err)
	}

	log.Printf("[穿搭评分] ✅ 评分完成: %d/10", result.Score)
	return &result, nil
}

// OutfitRatingResult 穿搭评分结果
type OutfitRatingResult struct {
	Score      int             `json:"score"`
	Breakdown  RatingBreakdown `json:"breakdown"`
	Reason     string          `json:"reason"`
	Suggestion string          `json:"suggestion"`
}

// RatingBreakdown 评分明细
type RatingBreakdown struct {
	ColorHarmony   int `json:"color_harmony"`
	StyleCoherence int `json:"style_coherence"`
	OccasionFit    int `json:"occasion_fit"`
	SeasonFit      int `json:"season_fit"`
}

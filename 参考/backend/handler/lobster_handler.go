package handler

import (
	"fmt"
	"net/http"
	"regexp"
	"strings"

	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"

	"github.com/morwardrobe/api-server/internal/middleware"
	"github.com/morwardrobe/api-server/internal/model"
	"github.com/morwardrobe/api-server/pkg/response"
)

type LobsterHandler struct {
	db *gorm.DB
}

func NewLobsterHandler(db *gorm.DB) *LobsterHandler {
	return &LobsterHandler{db: db}
}

type chatRequest struct {
	Message string `json:"message" binding:"required"`
}

type Intent struct {
	Type       string         `json:"type"`
	Name       string         `json:"name"`
	Keywords   []string       `json:"keywords"`
	Patterns   []*regexp.Regexp `json:"-"`
	Extractors []Extractor    `json:"-"`
}

type Extractor struct {
	Field        string `json:"field"`
	PatternIndex int    `json:"pattern_index"`
}

// 预定义意图列表
var intents = []Intent{
	{
		Type:     "change_password",
		Name:     "修改密码",
		Keywords: []string{"改密码", "修改密码", "换密码", "设置新密码", "更新密码", "新密码", "旧密码", "原密码", "当前密码", "password"},
		Patterns: []*regexp.Regexp{
			regexp.MustCompile(`(?:原[始]?密?码?|旧密码|当前密码)[是为是：:\s]*([a-zA-Z0-9@#$%^&*]{6,})`),
			regexp.MustCompile(`(?:新密码|改成|改为|修改为|换成)[是为是：:\s]*([a-zA-Z0-9@#$%^&*]{6,})`),
			regexp.MustCompile(`从(\S{6,})[改换](\S{6,})`),
		},
		Extractors: []Extractor{
			{Field: "current_password", PatternIndex: 0},
			{Field: "new_password", PatternIndex: 1},
		},
	},
	{
		Type:     "get_profile",
		Name:     "查看个人信息",
		Keywords: []string{"我是谁", "我的信息", "查看资料", "个人信息", "账户信息", "我的资料", "who am i", "my profile", "my info"},
		Patterns:   []*regexp.Regexp{},
		Extractors: []Extractor{},
	},
	{
		Type:     "clear_wardrobe",
		Name:     "清空衣橱",
		Keywords: []string{"清空衣橱", "删除所有衣服", "清空衣柜", "衣服全删了", "清除衣物", "clear wardrobe", "delete all clothes"},
		Patterns:   []*regexp.Regexp{},
		Extractors: []Extractor{},
	},
	{
		Type:     "get_stats",
		Name:     "查询衣物统计",
		Keywords: []string{"多少衣服", "衣橱统计", "衣物数量", "我有几件衣服", "衣服统计", "how many clothes", "wardrobe stats", "count clothes"},
		Patterns:   []*regexp.Regexp{},
		Extractors: []Extractor{},
	},
}

// POST /v1/lobster/chat
func (h *LobsterHandler) Chat(c *gin.Context) {
	var req chatRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.ValidationError(c, err)
		return
	}

	userID, _ := middleware.GetUserID(c)
	message := strings.TrimSpace(req.Message)

	// Step 1: 意图识别
	intent, confidence := recognizeIntent(message)
	if intent == nil || confidence < 0.3 {
		response.Error(c, http.StatusBadRequest, 40001, buildUnknownIntentResponse())
		return
	}

	// Step 2: 参数提取
	params := extractParams(intent, message)

	// Step 3: 构建第一阶段响应（意图确认）
	phase1Message := buildPhase1Response(intent.Name, params)

	// Step 4: 执行操作并获取结果
	executionResult := h.executeIntent(c, intent.Type, params, userID)

	// Step 5: 构建第二阶段响应（执行结果）
	phase2Message := buildPhase2Response(executionResult)

	response.Success(c, gin.H{
		"intent": gin.H{
			"type":       intent.Type,
			"name":       intent.Name,
			"confidence": confidence,
		},
		"extracted_params": params,
		"execution_result": executionResult,
		"response_template": gin.H{
			"phase1": phase1Message,
			"phase2": phase2Message,
		},
	})
}

// 意图识别
func recognizeIntent(message string) (*Intent, float64) {
	message = strings.ToLower(message)
	bestMatch := &Intent{}
	bestScore := 0.0

	for _, intent := range intents {
		score := 0.0
		for _, keyword := range intent.Keywords {
			if strings.Contains(message, strings.ToLower(keyword)) {
				score += 1.0
			}
		}
		
		// 正则匹配加分
		for _, pattern := range intent.Patterns {
			if pattern.MatchString(message) {
				score += 2.0
			}
		}

		confidence := score / float64(len(intent.Keywords)+len(intent.Patterns)*2)
		if confidence > bestScore {
			bestScore = confidence
			bestMatch = &intent
		}
	}

	if bestScore > 0 {
		return bestMatch, bestScore
	}
	return nil, 0
}

// 参数提取
func extractParams(intent *Intent, message string) map[string]string {
	params := make(map[string]string)

	for _, extractor := range intent.Extractors {
		if extractor.PatternIndex < len(intent.Patterns) {
			matches := intent.Patterns[extractor.PatternIndex].FindStringSubmatch(message)
			if len(matches) > extractor.PatternIndex+1 {
				params[extractor.Field] = matches[extractor.PatternIndex+1]
			}
		}
	}

	return params
}

// 执行意图
func (h *LobsterHandler) executeIntent(c *gin.Context, intentType string, params map[string]string, userID string) map[string]interface{} {
	result := map[string]interface{}{
		"success":     false,
		"message":     "",
		"api_called":  "",
		"data":        nil,
	}

	switch intentType {
	case "change_password":
		result = h.handleChangePassword(params, userID)
	case "get_profile":
		result = h.handleGetProfile(userID)
	case "clear_wardrobe":
		result = h.handleClearWardrobe(userID)
	case "get_stats":
		result = h.handleGetStats(userID)
	default:
		result["message"] = "未实现的意图类型"
	}

	return result
}

// 修改密码
func (h *LobsterHandler) handleChangePassword(params map[string]string, userID string) map[string]interface{} {
	currentPassword := params["current_password"]
	newPassword := params["new_password"]

	result := map[string]interface{}{
		"success":    false,
		"message":    "",
		"api_called": "POST /v1/auth/change-password",
	}

	if currentPassword == "" || newPassword == "" {
		result["message"] = "❌ 参数不完整：需要提供当前密码和新密码\n\n示例：\"把密码从123456改成abcdef\""
		return result
	}

	var user model.User
	if err := h.db.First(&user, "id = ?", userID).Error; err != nil {
		result["message"] = "❌ 用户不存在"
		return result
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(currentPassword)); err != nil {
		result["message"] = "❌ 当前密码错误，请检查后重试"
		return result
	}

	hashedPassword, _ := bcrypt.GenerateFromPassword([]byte(newPassword), bcrypt.DefaultCost)
	h.db.Model(&user).Update("password_hash", string(hashedPassword))

	result["success"] = true
	result["message"] = "✅ 密码修改成功！\n\n• 原密码：" + currentPassword + "\n• 新密码：" + newPassword + "\n\n请记住您的新密码哦~ 🦞"
	return result
}

// 查看个人信息
func (h *LobsterHandler) handleGetProfile(userID string) map[string]interface{} {
	result := map[string]interface{}{
		"success":    false,
		"message":    "",
		"api_called": "GET /v1/auth/me",
	}

	var user model.User
	if err := h.db.Select("id, email, avatar_url, role, is_active, created_at").First(&user, "id = ?", userID).Error; err != nil {
		result["message"] = "❌ 获取用户信息失败"
		return result
	}

	result["success"] = true
	result["data"] = user
	result["message"] = "✅ 您的个人信息如下：\n\n📧 邮箱：" + user.Email +
		"\n👤 角色：" + getRoleName(user.Role) +
		"\n📅 注册时间：" + user.CreatedAt.Format("2006年01月02日") +
		"\n✅ 账户状态：" + getStatusName(user.IsActive)

	return result
}

// 清空衣橱
func (h *LobsterHandler) handleClearWardrobe(userID string) map[string]interface{} {
	result := map[string]interface{}{
		"success":    false,
		"message":    "",
		"api_called": "DELETE /v1/admin/users/" + userID + "/wardrobe",
	}

	deleteResult := h.db.Where("user_id = ?", userID).Delete(&model.Clothing{})
	if deleteResult.Error != nil {
		result["message"] = "❌ 清空衣橱失败"
		return result
	}

	result["success"] = true
	result["message"] = "✅ 衣橱已成功清空！\n\n🗑️ 删除了 " + formatNumber(deleteResult.RowsAffected) + " 件衣物\n\n您的衣橱现在是空的了~ 🦞"
	return result
}

// 查询衣物统计
func (h *LobsterHandler) handleGetStats(userID string) map[string]interface{} {
	result := map[string]interface{}{
		"success":    false,
		"message":    "",
		"api_called": "GET /v1/clothes/stats",
	}

	var total, thinCount, thickCount int64
	
	h.db.Model(&model.Clothing{}).Where("user_id = ?", userID).Count(&total)
	h.db.Model(&model.Clothing{}).Where("user_id = ? AND wardrobe_type = 'thin'", userID).Count(&thinCount)
	h.db.Model(&model.Clothing{}).Where("user_id = ? AND wardrobe_type = 'thick'", userID).Count(&thickCount)

	result["success"] = true
	result["data"] = gin.H{
		"total":      total,
		"thin_wardrobe": thinCount,
		"thick_wardrobe": thickCount,
	}
	
	result["message"] = "✅ 您的衣橱统计：\n\n👕 总衣物数：" + formatNumber(total) +
		"\n🌤️ 薄款衣橱：" + formatNumber(thinCount) + " 件" +
		"\n❄️ 厚款衣橱：" + formatNumber(thickCount) + " 件"

	return result
}

// 构建第一阶段响应
func buildPhase1Response(intentName string, params map[string]string) string {
	msg := "🦞 我理解到您的意图是【" + intentName + "】\n\n"
	
	if len(params) > 0 {
		msg += "提取到的信息：\n"
		for key, value := range params {
			label := getParamLabel(key)
			msg += "• " + label + "：" + value + "\n"
		}
		msg += "\n"
	}
	
	msg += "我将调用相应的 API 为您执行此操作，请稍候..."
	
	return msg
}

// 构建第二阶段响应
func buildPhase2Response(executionResult map[string]interface{}) string {
	if msg, ok := executionResult["message"].(string); ok {
		return msg
	}
	return "✅ 操作完成"
}

// 构建未知意图响应
func buildUnknownIntentResponse() string {
	return "🦞 抱歉，我没有理解您的意思 😅\n\n您可以尝试说：\n" +
		"• \"把密码从123456改成abcdef\"\n" +
		"• \"查看我的信息\"\n" +
		"• \"我有多少衣服\"\n" +
		"• \"清空我的衣橱\""
}

// 辅助函数
func getRoleName(role string) string {
	switch role {
	case "admin":
		return "管理员 👑"
	default:
		return "普通用户 👤"
	}
}

func getStatusName(isActive bool) string {
	if isActive {
		return "正常 ✅"
	}
	return "已禁用 ❌"
}

func formatNumber(n int64) string {
	if n >= 10000 {
		return fmt.Sprintf("%.1f万", float64(n)/10000)
	}
	return fmt.Sprintf("%d", n)
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

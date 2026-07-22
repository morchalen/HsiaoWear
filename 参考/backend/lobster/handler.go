package lobster

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"

	"github.com/morwardrobe/api-server/internal/middleware"
	"github.com/morwardrobe/api-server/pkg/response"
)

// Handler 龙虾 HTTP 处理器
type Handler struct {
	agent *Agent
}

// NewHandler 创建新的龙虾 Handler
func NewHandler(db *gorm.DB) *Handler {
	apiKey := "sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
	baseURL := "https://api.moonshot.cn/v1"
	model := "moonshot-v1-8k"

	gormDB := NewGormDB(db)
	agent := NewAgent(apiKey, baseURL, model, gormDB)

	return &Handler{
		agent: agent,
	}
}

// LobsterChatRequest 前端请求体（避免与 agent.go 中的 ChatRequest 冲突）
type LobsterChatRequest struct {
	Message string `json:"message" binding:"required"`
}

// RateOutfitRequest 评分请求体
type RateOutfitRequest struct {
	UpperName  string `json:"upper_name"`
	LowerName  string `json:"lower_name"`
	FeetName   string `json:"feet_name"`
	UpperColor string `json:"upper_color"`
	LowerColor string `json:"lower_color"`
	FeetColor  string `json:"feet_color"`
}

// Chat POST /v1/lobster/chat
func (h *Handler) Chat(c *gin.Context) {
	var req LobsterChatRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.ValidationError(c, err)
		return
	}

	userID, _ := middleware.GetUserID(c)
	if userID == "" {
		response.UnauthorizedError(c, "用户未登录")
		return
	}

	lobsterResp, err := h.agent.ProcessUserMessage(c.Request.Context(), req.Message, userID)
	if err != nil {
		response.Error(c, http.StatusInternalServerError, 50001, "龙虾处理失败："+err.Error())
		return
	}

	response.Success(c, gin.H{
		"intent":           lobsterResp.Intent,
		"extracted_params": lobsterResp.ExtractedParams,
		"execution_result": lobsterResp.ExecutionResult,
		"response_template": gin.H{
			"phase1": lobsterResp.Phase1Message,
			"phase2": lobsterResp.Phase2Message,
		},
	})
}

// RecommendOutfit GET /v1/lobster/recommend-outfit
func (h *Handler) RecommendOutfit(c *gin.Context) {
	userID, _ := middleware.GetUserID(c)
	if userID == "" {
		response.UnauthorizedError(c, "用户未登录")
		return
	}

	result, err := h.agent.GetSmartOutfitRecommendation(userID)
	if err != nil {
		response.Error(c, http.StatusInternalServerError, 50002, err.Error())
		return
	}

	response.Success(c, result)
}

// RateOutfit POST /v1/lobster/rate-outfit
func (h *Handler) RateOutfit(c *gin.Context) {
	var req RateOutfitRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.ValidationError(c, err)
		return
	}

	userID, _ := middleware.GetUserID(c)
	if userID == "" {
		response.UnauthorizedError(c, "用户未登录")
		return
	}

	result, err := h.agent.RateOutfit(userID, req.UpperName, req.LowerName, req.FeetName, req.UpperColor, req.LowerColor, req.FeetColor)
	if err != nil {
		response.Error(c, http.StatusInternalServerError, 50003, err.Error())
		return
	}

	response.Success(c, result)
}

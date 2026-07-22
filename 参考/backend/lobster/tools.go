package lobster

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math/rand"
	"net/http"
	"time"

	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"

	"github.com/morwardrobe/api-server/internal/model"
)

// Tool 定义工具结构（兼容 OpenAI Function Calling 格式）
type Tool struct {
	Type     string        `json:"type"`
	Function *ToolFunction `json:"function"`
}

// ToolFunction 工具函数定义
type ToolFunction struct {
	Name        string          `json:"name"`
	Description string          `json:"description"`
	Parameters  *ToolParameters `json:"parameters"`
}

// ToolParameters 工具参数定义
type ToolParameters struct {
	Type       string                   `json:"type"`
	Properties map[string]*ToolProperty `json:"properties"`
	Required   []string                 `json:"required"`
}

// ToolProperty 单个属性
type ToolProperty struct {
	Type        string `json:"type"`
	Description string `json:"description"`
}

// GetTools 获取所有可用工具列表
func GetTools() []Tool {
	return []Tool{
		ChangePasswordTool(),
		GetProfileTool(),
		GetWardrobeStatsTool(),
		ListClothesTool(),
		ClearWardrobeTool(),
		RecommendOutfitTool(),
	}
}

// ========== 1. 修改密码工具 ==========
func ChangePasswordTool() Tool {
	return Tool{
		Type: "function",
		Function: &ToolFunction{
			Name:        "change_password",
			Description: "当用户想要修改、重置或更新他们的登录密码时调用此工具。必须从用户的话语中提取当前密码和新密码。如果用户没有提供完整的密码信息，不要调用此工具，而是询问用户缺失的信息。",
			Parameters: &ToolParameters{
				Type: "object",
				Properties: map[string]*ToolProperty{
					"current_password": {
						Type:        "string",
						Description: "用户当前的旧密码，通常至少6位字符",
					},
					"new_password": {
						Type:        "string",
						Description: "用户想要设置的新密码，至少6位字符",
					},
				},
				Required: []string{"current_password", "new_password"},
			},
		},
	}
}

// ========== 2. 查看个人信息工具 ==========
func GetProfileTool() Tool {
	return Tool{
		Type: "function",
		Function: &ToolFunction{
			Name:        "get_profile",
			Description: "当用户想查看自己的账户信息、个人资料、注册时间等基本信息时调用此工具。无需额外参数。",
			Parameters: &ToolParameters{
				Type:       "object",
				Properties: map[string]*ToolProperty{},
				Required:   []string{},
			},
		},
	}
}

// ========== 3. 查询衣物统计工具 ==========
func GetWardrobeStatsTool() Tool {
	return Tool{
		Type: "function",
		Function: &ToolFunction{
			Name:        "get_wardrobe_stats",
			Description: "当用户想了解自己的衣橱情况，如衣服总数、薄款/厚款衣橱数量、长期未穿的衣服数量等统计信息时调用此工具。触发词包括：多少衣服、衣橱统计、衣物数量、我有几件衣服等。",
			Parameters: &ToolParameters{
				Type:       "object",
				Properties: map[string]*ToolProperty{},
				Required:   []string{},
			},
		},
	}
}

// ========== 4. 获取衣服列表工具 ==========
func ListClothesTool() Tool {
	return Tool{
		Type: "function",
		Function: &ToolFunction{
			Name:        "list_clothes",
			Description: "当用户想查看自己有哪些衣服、列出衣服清单，或询问某类别的衣服时使用。触发词包括：有哪些衣服、看看我的衣服、列出衣服、我的上衣有哪些、上装清单等。如果用户指定了类别（如上衣、裤子），要提取 category 参数。",
			Parameters: &ToolParameters{
				Type: "object",
				Properties: map[string]*ToolProperty{
					"category": {
						Type:        "string",
						Description: "衣服类别，可选值：tops(上装)、bottoms(下装)、shoes(鞋履)、accessories(配饰)。如果用户没有指定可不填。",
					},
				},
				Required: []string{},
			},
		},
	}
}

// ========== 5. 清空衣橱工具 ==========
func ClearWardrobeTool() Tool {
	return Tool{
		Type: "function",
		Function: &ToolFunction{
			Name:        "clear_wardrobe",
			Description: "当用户明确要求删除、清空衣橱中的所有衣物时调用此工具。⚠️ 这是一个不可逆的危险操作，只有在用户明确表达清空意图时才调用。触发词包括：清空衣橱、删除所有衣服、清空衣柜、衣服全删了等。",
			Parameters: &ToolParameters{
				Type:       "object",
				Properties: map[string]*ToolProperty{},
				Required:   []string{},
			},
		},
	}
}

// ========== 6. 今日穿搭推荐工具 ==========
func RecommendOutfitTool() Tool {
	return Tool{
		Type: "function",
		Function: &ToolFunction{
			Name:        "recommend_outfit",
			Description: "当用户请求今日穿搭推荐、搭配建议、今天穿什么、帮我选衣服等时使用。会获取当前城市（默认重庆）的天气、日期和用户衣橱全部衣服，然后生成穿搭方案。触发词包括：推荐穿搭、今日穿搭、今天穿什么、帮我搭配、给我选衣服、穿什么好等。",
			Parameters: &ToolParameters{
				Type: "object",
				Properties: map[string]*ToolProperty{
					"city": {
						Type:        "string",
						Description: "城市名称，默认为重庆。用户可以指定其他城市。",
					},
				},
				Required: []string{},
			},
		},
	}
}

// ========== 工具执行器 ==========

// ExecuteTool 执行指定的工具
func ExecuteTool(toolName string, args map[string]interface{}, db DBInterface, userID string) (string, error) {
	switch toolName {
	case "change_password":
		return executeChangePassword(args, db, userID)
	case "get_profile":
		return executeGetProfile(db, userID)
	case "get_wardrobe_stats":
		return executeGetWardrobeStats(db, userID)
	case "list_clothes":
		return executeListClothes(args, db, userID)
	case "clear_wardrobe":
		return executeClearWardrobe(db, userID)
	case "recommend_outfit":
		return executeRecommendOutfit(args, db, userID)
	default:
		return "", fmt.Errorf("未知工具：%s", toolName)
	}
}

// 执行修改密码
func executeChangePassword(args map[string]interface{}, db DBInterface, userID string) (string, error) {
	currentPassword, ok1 := args["current_password"].(string)
	newPassword, ok2 := args["new_password"].(string)

	if !ok1 || !ok2 || currentPassword == "" || newPassword == "" {
		return "", fmt.Errorf("参数不完整：需要提供 current_password 和 new_password")
	}

	if len(newPassword) < 6 {
		return "", fmt.Errorf("新密码长度不足6位")
	}

	// 直接调用更新密码（内部会验证当前密码）
	err := db.UpdatePassword(userID, newPassword)
	if err != nil {
		return "", fmt.Errorf("密码修改失败：%v", err)
	}

	return fmt.Sprintf("✅ 密码修改成功！\n\n• 原密码：%s\n• 新密码：%s\n\n请记住您的新密码哦~ 🦞", currentPassword, newPassword), nil
}

// 执行查看个人信息
func executeGetProfile(db DBInterface, userID string) (string, error) {
	user, err := db.GetUserByID(userID)
	if err != nil {
		return "", fmt.Errorf("获取用户信息失败：%v", err)
	}

	roleName := "普通用户 👤"
	if user.Role == "admin" {
		roleName = "管理员 👑"
	}

	statusName := "正常 ✅"
	if !user.IsActive {
		statusName = "已禁用 ❌"
	}

	return fmt.Sprintf(`✅ 您的个人信息如下：

📧 邮箱：%s
👤 角色：%s
📅 注册时间：%s
✅ 账户状态：%s`,
		user.Email,
		roleName,
		user.CreatedAt.Format("2006年01月02日"),
		statusName,
	), nil
}

// 执行查询衣物统计
func executeGetWardrobeStats(db DBInterface, userID string) (string, error) {
	stats, err := db.GetClothingStats(userID)
	if err != nil {
		return "", fmt.Errorf("获取衣物统计失败：%v", err)
	}

	return fmt.Sprintf(`✅ 您的衣橱统计：

👕 总衣物数：%d 件
🌤️ 薄款衣橱：%d 件
❄️ 厚款衣橱：%d 件`,
		stats.Total,
		stats.ThinWardrobe,
		stats.ThickWardrobe,
	), nil
}

// 执行获取衣服列表
func executeListClothes(args map[string]interface{}, db DBInterface, userID string) (string, error) {
	category := ""
	if cat, ok := args["category"].(string); ok {
		category = cat
	}

	clothes, err := db.ListClothes(userID, category)
	if err != nil {
		return "", fmt.Errorf("获取衣服列表失败：%v", err)
	}

	if len(clothes) == 0 {
		return "您的衣橱中还没有任何衣服哦~ 🦞\n\n快去添加几件衣服吧！", nil
	}

	categoryNames := map[string]string{
		"tops":        "上装",
		"bottoms":     "下装",
		"shoes":       "鞋履",
		"accessories": "配饰",
		"":            "全部",
	}

	categoryCN := categoryNames[category]
	if categoryCN == "" {
		categoryCN = "全部"
	}

	msg := fmt.Sprintf("✅ 您共有 %d 件%s：\n\n", len(clothes), categoryCN)

	byCategory := make(map[string][]Clothing)
	for _, c := range clothes {
		catName := categoryNames[c.Category]
		if catName == "" {
			catName = c.Category
		}
		byCategory[catName] = append(byCategory[catName], c)
	}

	icons := map[string]string{
		"上装": "👕",
		"下装": "👖",
		"鞋履": "👟",
		"配饰": "🎒",
		"全部": "👔",
	}

	seasonNames := map[string]string{
		"spring": "春",
		"summer": "夏",
		"autumn": "秋",
		"winter": "冬",
	}

	for catName, items := range byCategory {
		icon := icons[catName]
		if icon == "" {
			icon = "👔"
		}
		msg += fmt.Sprintf("%s %s（%d件）：\n", icon, catName, len(items))
		for i, c := range items {
			color := ""
			if c.Color != "" {
				color = fmt.Sprintf("（%s）", c.Color)
			}
			name := c.Name
			if name == "" {
				name = "未命名"
			}

			seasonStr := ""
			if c.Seasons != "" {
				var seasons []string
				json.Unmarshal([]byte(c.Seasons), &seasons)
				if len(seasons) > 0 {
					var seasonCN []string
					for _, s := range seasons {
						if cn, ok := seasonNames[s]; ok {
							seasonCN = append(seasonCN, cn)
						}
					}
					if len(seasonCN) > 0 {
						seasonStr = fmt.Sprintf(" | 适配季节：%v", seasonCN)
					}
				}
			}

			msg += fmt.Sprintf("   %d. %s%s%s\n", i+1, name, color, seasonStr)
		}
		msg += "\n"
	}

	return msg, nil
}

// 执行清空衣橱
func executeClearWardrobe(db DBInterface, userID string) (string, error) {
	deletedCount, err := db.ClearWardrobe(userID)
	if err != nil {
		return "", fmt.Errorf("清空衣橱失败：%v", err)
	}

	return fmt.Sprintf("✅ 衣橱已成功清空！\n\n🗑️ 删除了 %d 件衣物\n\n您的衣橱现在是空的了~ 🦞", deletedCount), nil
}

// WeatherInfo 天气信息
type WeatherInfo struct {
	City        string
	Temperature string
	Description string
	Humidity    string
	Wind        string
	DateStr     string
}

// getWeather 获取城市天气信息（使用 wttr.in 免费API）
func getWeather(city string) (*WeatherInfo, error) {
	if city == "" {
		city = "重庆"
	}

	url := fmt.Sprintf("https://wttr.in/%s?format=j1&lang=zh", city)

	client := &http.Client{Timeout: 5 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		return nil, fmt.Errorf("获取天气失败：%v", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("读取天气数据失败：%v", err)
	}

	var weatherData struct {
		CurrentCondition []struct {
			Temp_C      string `json:"temp_C"`
			WeatherDesc []struct {
				Value string `json:"value"`
			} `json:"weatherDesc"`
			Humidity      string `json:"humidity"`
			WindspeedKmph string `json:"windspeedKmph"`
		} `json:"current_condition"`
	}

	if err := json.Unmarshal(body, &weatherData); err != nil {
		return nil, fmt.Errorf("解析天气数据失败：%v", err)
	}

	if len(weatherData.CurrentCondition) == 0 {
		return nil, fmt.Errorf("无天气数据")
	}

	cc := weatherData.CurrentCondition[0]
	desc := "未知"
	if len(cc.WeatherDesc) > 0 {
		desc = cc.WeatherDesc[0].Value
	}

	now := time.Now()
	dateStr := now.Format("1月2日")
	weekdays := []string{"周日", "周一", "周二", "周三", "周四", "周五", "周六"}
	dateStr = dateStr + " " + weekdays[now.Weekday()]

	return &WeatherInfo{
		City:        city,
		Temperature: cc.Temp_C + "°C",
		Description: desc,
		Humidity:    cc.Humidity + "%",
		Wind:        cc.WindspeedKmph + "km/h",
		DateStr:     dateStr,
	}, nil
}

// 执行今日穿搭推荐
func executeRecommendOutfit(args map[string]interface{}, db DBInterface, userID string) (string, error) {
	city := "重庆"
	if c, ok := args["city"].(string); ok && c != "" {
		city = c
	}

	weather, err := getWeather(city)
	if err != nil {
		weather = &WeatherInfo{
			City:        city,
			Temperature: "--°C",
			Description: "无法获取天气信息",
			DateStr:     time.Now().Format("1月2日"),
		}
	}

	clothes, err := db.ListClothes(userID, "")
	if err != nil {
		return "", fmt.Errorf("获取衣橱失败：%v", err)
	}

	if len(clothes) == 0 {
		return "🦞 您的衣橱还是空的，没有衣服可以搭配哦~\n\n快去【添加衣物】页面添加几件衣服吧，之后我就能为您推荐穿搭了！", nil
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

	parseSeasons := func(seasonsRaw string) []string {
		if seasonsRaw == "" {
			return nil
		}
		var seasons []string
		json.Unmarshal([]byte(seasonsRaw), &seasons)
		return seasons
	}

	// 构建给AI的完整提示，包含所有衣服信息
	// 让AI来做推荐，而不是我们自己选
	var clothesInfo string
	for catName, items := range byCategory {
		if len(items) == 0 {
			continue
		}
		clothesInfo += fmt.Sprintf("**%s**（%d件）：\n", catName, len(items))
		for i, item := range items {
			seasons := parseSeasons(item.Seasons)
			clothesInfo += fmt.Sprintf("  %d. %s（%s，ID: %s）", i+1, item.Name, item.Color, item.ID)
			if len(seasons) > 0 {
				clothesInfo += fmt.Sprintf("，适配季节：%v", seasons)
			}
			if item.ImageURL != "" {
				clothesInfo += fmt.Sprintf("\n     图片：%s", item.ImageURL)
			}
			clothesInfo += "\n"
		}
		clothesInfo += "\n"
	}

	prompt := fmt.Sprintf(`
📍 **当前天气信息：**
• 城市：%s
• 日期：%s
• 温度：%s
• 天气：%s
• 湿度：%s

👔 **用户衣橱全部衣服：**
%s

---

请根据上面的天气信息和用户的所有衣服，为用户推荐一套今日穿搭方案。要求：

1. 推荐必须包含：上衣（上装）、下装、鞋子三个基本品类
2. 如果天气较冷或需要，也可以推荐外套
3. 每件推荐的衣物都必须从上面的衣橱列表中选择（引用对应的ID或名称）
4. 考虑衣服的适配季节
5. 用友好、自然的语气给出推荐
6. 推荐结果中要包含每件衣服的名称
7. 给出推荐理由，结合天气和季节
8. 保持简洁、美观的格式，便于阅读

请直接给出穿搭推荐方案！
`, weather.City, weather.DateStr, weather.Temperature, weather.Description, weather.Humidity, clothesInfo)

	return prompt, nil
}

func getRecommendReason(season string, temp int) string {
	if season == "夏" && temp > 25 {
		return "夏季炎热，选择轻薄透气的衣物"
	} else if season == "夏" && temp <= 25 {
		return "初夏时节，选择轻薄舒适的衣物"
	} else if season == "春" && temp > 20 {
		return "春末气温升高，选择轻薄透气的衣物"
	} else if season == "春" && temp > 15 {
		return "春季温暖，选择舒适透气的衣物"
	} else if season == "春" && temp <= 15 {
		return "春季早晚温差大，建议选择相对保暖的衣物"
	} else if season == "秋" && temp > 15 {
		return "秋季气温适宜，选择轻薄舒适的衣物"
	} else if season == "秋" && temp <= 15 {
		return "秋季转凉，建议选择相对保暖的衣物"
	} else if season == "冬" || temp < 10 {
		return "天气较冷，建议选择保暖的衣物"
	}
	return "选择舒适得体的衣物"
}

func getColorRecommend(season string, topColor, bottomColor, shoesColor string) string {
	if topColor == "" || bottomColor == "" {
		return ""
	}

	var suggestions []string

	if isNeutralColor(topColor) && !isNeutralColor(bottomColor) {
		suggestions = append(suggestions, fmt.Sprintf("上衣%s与下装%s形成主次分明搭配", topColor, bottomColor))
	} else if !isNeutralColor(topColor) && isNeutralColor(bottomColor) {
		suggestions = append(suggestions, fmt.Sprintf("下装%s作为百搭色，配合%s上衣显得协调", bottomColor, topColor))
	} else if isNeutralColor(topColor) && isNeutralColor(bottomColor) {
		suggestions = append(suggestions, "整体同色系搭配，简约大方")
	} else {
		suggestions = append(suggestions, fmt.Sprintf("上下撞色搭配，%s与%s形成视觉冲击", topColor, bottomColor))
	}

	if shoesColor != "" {
		if isNeutralColor(shoesColor) {
			suggestions = append(suggestions, fmt.Sprintf("鞋子%s是百搭色，整体色调统一", shoesColor))
		} else if shoesColor == topColor || shoesColor == bottomColor {
			suggestions = append(suggestions, fmt.Sprintf("鞋子与%s相呼应，细节满分", shoesColor))
		}
	}

	if len(suggestions) > 0 {
		return "颜色搭配：" + suggestions[0] + "。"
	}
	return ""
}

func isNeutralColor(color string) bool {
	neutralColors := []string{"黑色", "白色", "灰色", "米色", "卡其", "深蓝", "藏青"}
	for _, c := range neutralColors {
		if contains(color, c) || contains(c, color) {
			return true
		}
	}
	return false
}

func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(s) > 0 && containsHelper(s, substr))
}

func containsHelper(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}

// ========== LobsterResponse 响应结构体 ==========

// IntentInfo 意图信息
type IntentInfo struct {
	Type       string  `json:"type"`
	Name       string  `json:"name"`
	Confidence float64 `json:"confidence"`
}

// ExecutionResult 执行结果
type ExecutionResult struct {
	Success   bool        `json:"success"`
	Message   string      `json:"message"`
	APICalled string      `json:"api_called,omitempty"`
	Data      interface{} `json:"data,omitempty"`
}

// LobsterResponse 龙虾响应（两阶段）
type LobsterResponse struct {
	Intent          IntentInfo        `json:"intent"`
	ExtractedParams map[string]string `json:"extracted_params,omitempty"`
	Phase1Message   string            `json:"phase1_message"`
	Phase2Message   string            `json:"phase2_message"`
	ExecutionResult ExecutionResult   `json:"execution_result"`
}

// ========== GORM 实现 DBInterface ==========

// GormDB GORM 数据库实现
type GormDB struct {
	DB *gorm.DB
}

// NewGormDB 创建 GORM 数据库实例
func NewGormDB(db *gorm.DB) *GormDB {
	return &GormDB{DB: db}
}

// GetUserByID 根据ID获取用户
func (g *GormDB) GetUserByID(id string) (User, error) {
	var user model.User
	if err := g.DB.Select("id, email, role, is_active, created_at").First(&user, "id = ?", id).Error; err != nil {
		return User{}, err
	}
	return User{
		ID:        user.ID,
		Email:     user.Email,
		Role:      user.Role,
		IsActive:  user.IsActive,
		CreatedAt: user.CreatedAt,
	}, nil
}

// UpdatePassword 更新密码
func (g *GormDB) UpdatePassword(userID, newPassword string) error {
	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(newPassword), bcrypt.DefaultCost)
	if err != nil {
		return err
	}
	return g.DB.Model(&model.User{}).Where("id = ?", userID).Update("password_hash", string(hashedPassword)).Error
}

// GetClothingStats 获取衣物统计
func (g *GormDB) GetClothingStats(userID string) (ClothingStats, error) {
	var stats ClothingStats

	g.DB.Model(&model.Clothing{}).Where("user_id = ?", userID).Count(&stats.Total)
	g.DB.Model(&model.Clothing{}).Where("user_id = ? AND wardrobe_type = 'thin'", userID).Count(&stats.ThinWardrobe)
	g.DB.Model(&model.Clothing{}).Where("user_id = ? AND wardrobe_type = 'thick'", userID).Count(&stats.ThickWardrobe)

	return stats, nil
}

// ClearWardrobe 清空衣橱
func (g *GormDB) ClearWardrobe(userID string) (int64, error) {
	result := g.DB.Where("user_id = ?", userID).Delete(&model.Clothing{})
	if result.Error != nil {
		return 0, result.Error
	}
	return result.RowsAffected, nil
}

// ListClothes 获取衣服列表
func (g *GormDB) ListClothes(userID string, category string) ([]Clothing, error) {
	query := g.DB.Model(&model.Clothing{}).Where("user_id = ?", userID).Where("status = ?", "active")
	if category != "" {
		query = query.Where("category = ?", category)
	}

	var clothings []model.Clothing
	if err := query.Order("created_at DESC").Find(&clothings).Error; err != nil {
		return nil, err
	}

	result := make([]Clothing, 0, len(clothings))
	for _, c := range clothings {
		result = append(result, Clothing{
			ID:       c.ID,
			Name:     c.Name,
			Category: c.Category,
			Color:    c.Color,
			ImageURL: c.ImageURL,
			Seasons:  c.SeasonsRaw,
		})
	}
	return result, nil
}

// OutfitRecommendationResult 穿搭推荐结果（结构化返回）
type OutfitRecommendationResult struct {
	Upper *Clothing `json:"upper,omitempty"`
	Lower *Clothing `json:"lower,omitempty"`
	Feet  *Clothing `json:"feet,omitempty"`
}

// GetAIRecommendation 获取 AI 穿搭推荐（结构化）
func GetAIRecommendation(db DBInterface, userID string) (*OutfitRecommendationResult, error) {
	log.Printf("[推荐] 用户ID: %v，开始获取衣橱...", userID)
	clothes, err := db.ListClothes(userID, "")
	if err != nil {
		log.Printf("[推荐] ❌ 获取衣橱失败：%v", err)
		return nil, fmt.Errorf("获取衣橱失败：%v", err)
	}
	log.Printf("[推荐] ✅ 获取到 %v 件衣服", len(clothes))

	if len(clothes) == 0 {
		log.Printf("[推荐] ⚠️ 衣橱为空")
		return nil, fmt.Errorf("衣橱为空")
	}

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
	log.Printf("[推荐] 分类统计: 上装=%v, 下装=%v, 鞋履=%v", len(tops), len(bottoms), len(shoes))

	result := &OutfitRecommendationResult{}

	// 随机选择
	rng := rand.New(rand.NewSource(time.Now().UnixNano()))

	if len(tops) > 0 {
		idx := rng.Intn(len(tops))
		result.Upper = &tops[idx]
		log.Printf("[推荐] 随机选择上装: %v (图片URL: %v)", tops[idx].Name, tops[idx].ImageURL)
	} else {
		log.Printf("[推荐] ⚠️ 没有找到上装")
	}

	if len(bottoms) > 0 {
		idx := rng.Intn(len(bottoms))
		result.Lower = &bottoms[idx]
		log.Printf("[推荐] 随机选择下装: %v (图片URL: %v)", bottoms[idx].Name, bottoms[idx].ImageURL)
	} else {
		log.Printf("[推荐] ⚠️ 没有找到下装")
	}

	if len(shoes) > 0 {
		idx := rng.Intn(len(shoes))
		result.Feet = &shoes[idx]
		log.Printf("[推荐] 随机选择鞋履: %v (图片URL: %v)", shoes[idx].Name, shoes[idx].ImageURL)
	} else {
		log.Printf("[推荐] ⚠️ 没有找到鞋履")
	}

	log.Printf("[推荐] ✅ 返回结果: Upper=%v, Lower=%v, Feet=%v",
		result.Upper != nil, result.Lower != nil, result.Feet != nil)
	return result, nil
}

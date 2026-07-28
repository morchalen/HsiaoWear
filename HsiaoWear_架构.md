# HsiaoWear（小不衣橱）项目分析 — 架构、技术与面试指南

> 本项目是一个基于 Jetpack Compose + MVVM 的 AI 智能衣橱管理 App，功能涵盖衣橱管理、天气获取、AI 穿搭推荐、AI 虚拟试衣、AI 聊天助手等。

---

## 目录

1. [项目技术栈总览](#1-项目技术栈总览)
2. [MVVM 架构详解](#2-mvvm-架构详解)
3. [各层代码分析](#3-各层代码分析)
4. [核心技术点讲解](#4-核心技术点讲解)
5. [实习生面试常见问题](#5-实习生面试常见问题)

---

## 1. 项目技术栈总览

| 类别 | 技术 | 用途 |
|------|------|------|
| 语言 | **Kotlin** 100% | 全部代码使用 Kotlin 编写 |
| UI 框架 | **Jetpack Compose** + **Material 3** | 声明式 UI，替代传统 XML |
| 架构 | **MVVM** (Model-View-ViewModel) | 分离 UI 和业务逻辑 |
| 依赖注入 | **Hilt** (基于 Dagger) | 自动管理对象创建和生命周期 |
| 本地数据库 | **Room** | 存储衣物数据（SQLite 的 Kotlin 封装） |
| 偏好存储 | **DataStore Preferences** | 存储设置项（替代 SharedPreferences） |
| 网络请求 | **Retrofit** + **OkHttp** | HTTP 请求客户端 |
| 图片加载 | **Coil** | Compose 原生支持的图片加载库 |
| 异步编程 | **Kotlin Coroutines** + **Flow** | 协程处理异步任务，Flow 做响应式数据流 |
| 构建工具 | **Gradle Version Catalog** | 统一管理依赖版本 |
| 注解处理 | **KSP** (Kotlin Symbol Processing) | 替代 kapt，更快的注解处理 |
| 外部 API | 阿里云 DashScope / 火山引擎 / uapis.cn 天气 | AI 试衣、抠图、天气数据 |

---

## 2. MVVM 架构详解

### 2.1 什么是 MVVM？

MVVM 是 **Model-View-ViewModel** 的缩写，Android 官方推荐的架构模式。它的核心思想是**数据驱动 UI**：

```
View (Composable UI)
    │  观察 (collectAsState) / 事件回调
    ▼
ViewModel (持有状态，处理业务逻辑)
    │  调用
    ▼
Repository (数据仓库)
    │  读写
    ▼
Model (Room DB / Network API / DataStore)
```

**分层职责：**

- **View（视图层）**：Composable 函数，只负责渲染 UI 和传递用户事件。不做任何业务逻辑。
- **ViewModel**：持有 UI 状态（StateFlow），处理用户操作，调用 Repository。**不持有任何 View 引用**。
- **Repository**：作为单一数据来源，封装数据来源（本地数据库/远程 API）的细节。
- **Model**：数据实体（Entity）、数据库、网络 API、DataStore。

### 2.2 本项目 MVVM 目录结构

```
com.example.hsiaowear/
├── MainActivity.kt          ← View 层入口（Activity）
├── HsiaoWearApp.kt          ← Application 类（Hilt 入口）
│
├── viewmodel/               ← ViewModel 层
│   ├── TodayViewModel.kt    ← 今日推荐 + 天气 + 抠图 + 试衣
│   ├── WardrobeViewModel.kt ← 衣橱 CRUD
│   ├── LobsterViewModel.kt  ← AI 聊天助手
│   └── SettingsViewModel.kt ← 设置管理
│
├── ui/
│   ├── screen/              ← View 层（Composable 页面）
│   │   ├── TodayScreen.kt
│   │   ├── WardrobeScreen.kt
│   │   ├── LobsterScreen.kt
│   │   ├── SettingsScreen.kt
│   │   ├── AddClothingScreen.kt
│   │   ├── ClothingDetailScreen.kt
│   │   └── OnboardingScreen.kt
│   ├── components/          ← 可复用组件
│   └── theme/               ← Material 3 主题
│
├── data/                    ← Model 层
│   ├── local/               ← Room 数据库
│   │   ├── AppDatabase.kt
│   │   ├── ClothingEntity.kt
│   │   └── Daos.kt
│   ├── repository/          ← 数据仓库层
│   │   ├── ClothingRepository.kt
│   │   ├── AIRepository.kt
│   │   ├── MattingRepository.kt
│   │   ├── TryOnRepository.kt
│   │   └── ...
│   ├── SettingsDataStore.kt ← DataStore 偏好存储
│   └── Result.kt            ← 统一结果封装（sealed class）
│
├── network/                 ← 网络层
│   ├── ApiService.kt        ← Retrofit 接口定义
│   ├── LLMApi.kt            ← AI 聊天 API
│   ├── TryOnApi.kt          ← 试衣 API
│   ├── Volcengine*.kt       ← 火山引擎 API（抠图）
│   └── ...
│
└── di/                      ← Hilt 依赖注入模块
    ├── NetworkModule.kt
    ├── DatabaseModule.kt
    └── Qualifiers.kt
```

### 2.3 数据流向（以"AI 穿搭推荐"为例）

```
用户点击 "AI推荐" 按钮
    │
    ▼
TodayScreen.kt
    → viewModel.performAiRecommendation()
    │
    ▼
TodayViewModel.kt
    → 1. 设置 _isAiRecommending = true（UI 显示加载动画）
    → 2. 调用 aiRepository.getWeather()          ← 网络请求
    → 3. 调用 clothingRepository.getAllClothes()  ← Room 查询
    → 4. 调用 aiRepository.getOutfitRecommendation() ← LLM API
    → 5. 解析结果，更新 _aiUpperClothing / _aiLowerClothing / _aiShoesClothing
    → 6. 设置 _isAiRecommending = false
    │
    ▼
TodayScreen.kt 通过 collectAsState() 观察到状态变化
    → UI 自动重组渲染推荐结果
```

这里最关键的点是：**ViewModel 通过 StateFlow 暴露状态，UI 通过 collectAsState() 观察这些状态**。当状态变化时，Composable 会自动重组，不需要手动刷新 UI。

### 2.4 为什么 MVVM 好？

1. **可测试性**：ViewModel 不持有 View 引用，因此可以纯单元测试
2. **生命周期安全**：ViewModel 在屏幕旋转时不会销毁
3. **关注点分离**：UI 只做渲染，业务逻辑在 ViewModel
4. **可维护性**：每层职责清晰，修改不影响其他层

---

## 3. 各层代码分析

### 3.1 View 层（Composable）

**特征：**
- 函数式编程，用 `@Composable` 注解标记
- 通过 `viewModel.collectAsState()` 收集 ViewModel 的状态
- 不直接持有数据，只通过回调把用户事件传递给 ViewModel

**关键代码片段：**

```kotlin
// TodayScreen.kt — 观察 ViewModel 的状态
val weather by viewModel.weather.collectAsState()
val isLoading by viewModel.isLoading.collectAsState()

// 将用户事件传递给 ViewModel
Button(onClick = { viewModel.performAiRecommendation() }) {
    Text("AI推荐")
}
```

**自适应的宽屏/窄屏适配（MainActivity.kt 第 129-149 行）：**
```kotlin
val adaptiveInfo = currentWindowAdaptiveInfo()
val isExpanded = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

if (isExpanded) {
    AdaptiveScreen(...)     // 平板：NavigationRail
} else {
    PhoneScreen(...)        // 手机：底部 NavigationBar
}
```

### 3.2 ViewModel 层

**特征：**
- 继承 `ViewModel()`，使用 `viewModelScope.launch` 启动协程
- 使用 `@HiltViewModel` + `@Inject constructor` 让 Hilt 自动注入依赖
- 使用 `MutableStateFlow` / `StateFlow` 持有状态
- 使用 `_xxx` (私有) + `xxx` (公开) 模式保护状态

```kotlin
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val aiRepository: AIRepository,
    private val clothingRepository: ClothingRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun performAiRecommendation() {
        viewModelScope.launch {  // 在 ViewModel 作用域中启动协程
            _isLoading.value = true
            // ... 业务逻辑
            _isLoading.value = false
        }
    }
}
```

**为什么用 StateFlow 不用 LiveData？**
- StateFlow 是 Kotlin 协程原生支持的，可以更好地和 Flow/collect 配合
- StateFlow 有 `asStateFlow()` 防止外部修改
- StateFlow 在 Compose 中通过 `collectAsState()` 收集

### 3.3 Repository 层

Repository 是 MVVM 中的关键层，它的职责是**屏蔽数据来源**。上层（ViewModel）不需要知道数据是来自 Room 还是网络 API。

```kotlin
class ClothingRepository @Inject constructor(
    private val clothingDao: ClothingDao,  // 本地
    private val api: WardrobeApi           // 远程
) {
    fun getAllClothes(): Flow<List<ClothingEntity>> {
        return clothingDao.getAllClothes()  // ViewModel 不需要知道数据来源
    }
}
```

### 3.4 Model 层

**Room（本地数据库）：**
- `@Entity` 标记数据表（`ClothingEntity`）
- `@Dao` 标记数据访问对象（增删改查）
- `@Database` 标记数据库类

**Result 密封类（统一结果封装）：**
```kotlin
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
```
这样在 ViewModel 中可以用 `when` 表达式安全地处理每种结果。

---

## 4. 核心技术点讲解

### 4.1 依赖注入（Hilt）

**什么是 DI？**
依赖注入是一种设计模式，让外部（DI 容器）来创建和管理依赖对象，而不是让类自己去 new。

**没有 DI 的写法（不推荐）：**
```kotlin
class TodayViewModel : ViewModel() {
    private val repository = ClothingRepository(...)  // 自己创建依赖
}
```

**有 DI 的写法：**
```kotlin
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: ClothingRepository  // Hilt 自动注入
) : ViewModel()
```

**本项目中的 Hilt 关键注解：**
- `@HiltAndroidApp` — Application 类上使用，Hilt 的入口
- `@AndroidEntryPoint` — Activity 上使用
- `@HiltViewModel` — ViewModel 上使用，让 Hilt 知道如何创建它
- `@Module @InstallIn(SingletonComponent::class)` — 告诉 Hilt 如何提供依赖
- `@Provides @Singleton` — 方法级别，告诉 Hilt 如何创建对象，且全局单例

### 4.2 Kotlin 协程（Coroutines）

协程是 Kotlin 的轻量级异步方案，比线程更轻量。

**关键概念：**
- **`suspend` 函数**：可暂停的函数，不会阻塞线程
- **`viewModelScope`**：ViewModel 内置的协程作用域，当 ViewModel 销毁时自动取消
- **`launch`**：启动一个新协程
- **`Dispatchers.IO`**：用于网络请求和数据库操作的线程池

```kotlin
viewModelScope.launch {           // 主线程启动协程
    val result = withContext(Dispatchers.IO) {  // 切换到 IO 线程
        repository.fetchData()    // 网络请求
    }
    // 自动回到主线程更新 UI
    _data.value = result
}
```

### 4.3 Flow 和 StateFlow

**Flow** 是 Kotlin 协程的响应式数据流，类似于 RxJava 的 Observable。

**StateFlow** 是 Flow 的一种特殊类型，有"当前值"的概念，适合做状态持有。

```kotlin
// Room DAO 返回 Flow — 数据库变化时自动通知
@Query("SELECT * FROM clothes ORDER BY createdAt DESC")
fun getAllClothes(): Flow<List<ClothingEntity>>

// ViewModel 暴露 StateFlow
private val _clothes = MutableStateFlow<List<ClothingEntity>>(emptyList())
val clothes: StateFlow<List<ClothingEntity>> = _clothes.asStateFlow()

// UI 层收集
val clothes by viewModel.clothes.collectAsState()
```

### 4.4 Jetpack Compose

Compose 是 Android 的**声明式 UI 框架**，取代传统的 XML 布局。

**声明式 vs 命令式：**
- **命令式（XML）**：创建 TextView，设置文本，添加到布局 → `findViewById` → 修改文本
- **声明式（Compose）**：`Text("Hello")` — 直接描述 UI 应该是什么样，状态变化时自动重组

**Compose 关键特性在本项目中的体现：**
- `@Composable` 函数标记 UI 组件
- `remember` / `mutableStateOf` 管理局部状态
- `LaunchedEffect` 处理副作用（如首次加载数据）
- `collectAsState()` 收集 ViewModel 的 StateFlow

### 4.5 Room 数据库

Room 是 Android 官方的 SQLite ORM 库，提供编译时的 SQL 校验。

**三个核心组件：**
1. **Entity** — 数据表映射
2. **DAO** — 数据访问对象（SQL 操作）
3. **Database** — 数据库创建入口

```kotlin
@Entity(tableName = "clothes")
data class ClothingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val category: String = "",
    val imageUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ClothingDao {
    @Query("SELECT * FROM clothes ORDER BY createdAt DESC")
    fun getAllClothes(): Flow<List<ClothingEntity>>

    @Insert
    suspend fun insertClothing(clothing: ClothingEntity): Long
}
```

### 4.6 DataStore Preferences

DataStore 是 SharedPreferences 的替代品，基于 Kotlin 协程和 Flow，更加安全。

```kotlin
// 写入
context.dataStore.edit { preferences ->
    preferences[stringPreferencesKey("api_key")] = "xxx"
}

// 读取（返回 Flow，可以实时观察变化）
val apiSettingsFlow: Flow<ApiSettings> = context.dataStore.data.map { prefs ->
    ApiSettings(
        baseUrl = prefs[KEY_API_ENDPOINT] ?: "",
        apiKey = prefs[KEY_API_KEY] ?: ""
    )
}
```

### 4.7 Retrofit + OkHttp

Retrofit 是 Android 上最流行的 HTTP 客户端，通过注解定义 API 接口。

```kotlin
interface LLMApi {
    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: LLMRequest): LLMResponse
}
```

OkHttp 是底层 HTTP 客户端，Retrofit 基于它构建。本项目中 OkHttp 的 Interceptor 被用来：
- 自动添加 Authorization Header
- 日志打印（HttpLoggingInterceptor）
- 火山引擎 API 签名（VolcengineInterceptor）

### 4.8 网络 API 集成

本项目调用了三个外部 AI API：

| API | 服务商 | 用途 | 文件 |
|-----|--------|------|------|
| LLM Chat Completions | 用户自定义（兼容 OpenAI） | 穿搭推荐、聊天助手 | `AIRepository.kt` |
| 图像分割 | 火山引擎 | 抠图（Matting） | `MattingRepository.kt` |
| 虚拟试衣 | 阿里云 DashScope (百炼) | AI 试衣 | `TryOnRepository.kt` |

---

## 5. 实习生面试常见问题

### 5.1 MVVM 相关

**Q1: 什么是 MVVM？它和 MVC 有什么区别？**

MVVM 是 Model-View-ViewModel 的缩写。在 Android 中：
- **Model**：数据和业务逻辑（数据库、网络 API）
- **View**：UI 层（Activity / Fragment / Composable），只负责展示和事件传递
- **ViewModel**：持有 UI 状态，处理业务逻辑

与 MVC 的最大区别：
- MVC 中 View 直接持有 Model 引用，耦合度高
- MVVM 中 View 和 Model 完全解耦，ViewModel 作为中间桥梁
- ViewModel 不持有 View 引用，因此可以应对屏幕旋转等生命周期变化

**Q2: 为什么 ViewModel 不会在屏幕旋转时销毁？**

因为 ViewModel 默认存储在 ViewModelStore 中，屏幕旋转时 Activity 会重新创建，但 ViewModelStore 不会销毁。新的 Activity 从同一个 ViewModelStore 中获取已有的 ViewModel 实例。

**Q3: ViewModel 和 Compose 如何通信？**

ViewModel 通过 StateFlow 暴露状态，Composable 通过 `collectAsState()` 收集：

```kotlin
// ViewModel 中
private val _data = MutableStateFlow("hello")
val data: StateFlow<String> = _data.asStateFlow()

// Composable 中
val data by viewModel.data.collectAsState()
```

### 5.2 依赖注入（Hilt / Dagger）

**Q4: 什么是依赖注入？为什么要用 Hilt？**

依赖注入（DI）是指一个对象的依赖由外部传入，而不是自己创建。好处：
1. **解耦**：类不负责创建自己的依赖
2. **可测试性**：可以轻松替换为 Mock 对象
3. **生命周期管理**：Singleton、ActivityScoped 等

Hilt 是官方推荐的 Android DI 框架，基于 Dagger，但使用更简单。

**Q5: `@HiltViewModel` 和 `@AndroidEntryPoint` 有什么区别？**

- `@HiltViewModel` 用在 ViewModel 上，让 Hilt 知道如何注入 ViewModel 的依赖
- `@AndroidEntryPoint` 用在 Android 组件（Activity / Fragment）上，让 Hilt 为其提供依赖注入支持

### 5.3 协程与 Flow

**Q6: 什么是协程？和线程有什么区别？**

协程是轻量级的并发框架。区别：
- **线程**：操作系统级别的，创建和切换成本高
- **协程**：用户态的，不需要操作系统切换，可以在同一线程上运行多个协程
- 协程支持挂起（suspend），挂起时不阻塞线程

**Q7: `viewModelScope.launch` 和 `GlobalScope.launch` 有什么区别？**

- `viewModelScope` 绑定 ViewModel 的生命周期，ViewModel 销毁时自动取消协程
- `GlobalScope` 全局作用域，不会自动取消，容易造成内存泄漏
- 项目中永远应该用 `viewModelScope` 而不是 `GlobalScope`

**Q8: Flow 和 LiveData 有什么区别？**

- LiveData 和生命周期绑定，但在协程中支持不够好
- Flow 是协程原生的，可以和各种操作符（map、filter、combine 等）配合
- StateFlow 是 Flow 的一种，有初始值，适合做状态持有
- 在 Compose 项目中，官方推荐 StateFlow 而不是 LiveData

### 5.4 Room 数据库

**Q9: Room 的核心组件有哪些？**

1. `@Entity` — 定义数据表结构
2. `@Dao` — 定义数据访问操作（SQL 增删改查）
3. `@Database` — 定义数据库版本和实体列表

**Q10: Room DAO 返回 Flow 有什么好处？**

当数据库数据变化时，Flow 会自动重新发射数据，UI 层通过 collectAsState() 观察到变化后自动更新，实现了"数据变化 → UI 自动刷新"的响应式编程。

### 5.5 Jetpack Compose

**Q11: Compose 和传统 XML 布局有什么区别？**

| Compose | XML 布局 |
|---------|----------|
| 声明式：描述 UI 应该长什么样 | 命令式：一步步告诉 UI 做什么 |
| 状态变化时自动重组 | 需要手动 `findViewById` + 更新 |
| 全部 Kotlin 代码 | Layout XML + Activity/Fragment 代码 |
| 没有 findViewById | 需要 findViewById 或 ViewBinding |

**Q12: `remember` 和 `mutableStateOf` 的作用是什么？**

- `mutableStateOf` 创建一个可观察的状态，当值变化时触发重组
- `remember` 让这个值在重组时保持住（不会重新初始化）

```kotlin
var count by remember { mutableStateOf(0) }
// 点击后 count + 1，UI 自动更新
Button(onClick = { count++ }) {
    Text("点击了 $count 次")
}
```

### 5.6 项目业务逻辑问题

**Q13: 描述 AI 穿搭推荐功能的完整流程。**

1. ViewModel 获取天气信息（调用 uapis.cn API）
2. ViewModel 从 Room 读取衣橱中的所有衣物
3. 构造 Prompt，发送给 LLM API（兼容 OpenAI 格式）
4. LLM 返回 JSON 格式的推荐结果（上装、下装、鞋子、推荐理由）
5. ViewModel 解析 JSON，更新对应的 StateFlow
6. UI 通过 collectAsState() 观察到新状态，渲染推荐结果

**Q14: AI 试衣功能是如何实现的？**

1. 用户上传身体照片和选择衣物
2. MattingRepository 调用火山引擎 API 对照片进行抠图（去除背景）
3. TryOnRepository 将身体照片和衣物图片上传到阿里云百炼 DashScope
4. DashScope 创建异步任务处理 AI 试衣
5. TryOnRepository 轮询任务状态（最多 60 次，每次 5 秒）
6. 任务完成后，下载结果图片到本地
7. 显示在 UI 上

**Q15: 抠图失败时怎么处理的？**

项目中有多层降级策略：
1. 上传身体照片时自动触发抠图
2. 如果抠图成功，显示抠图结果并持久化
3. 如果抠图失败，保留原图并提示"抠图失败，将使用原图"
4. 用户在 UI 中可以手动点击抠图按钮重试

### 5.7 通用 Android 问题

**Q16: Activity 的生命周期有哪些？**

`onCreate` → `onStart` → `onResume` → `onPause` → `onStop` → `onDestroy`

**Q17: 密封类（sealed class）是什么？为什么本项目要用它？**

密封类是一种特殊的类，限制子类类型，常用于表示有限状态。本项目的 `Result<T>` 使用 sealed class 表示三种状态：`Success`、`Error`、`Loading`。这比用布尔值更安全，因为：
- 使用 `when` 表达式时，编译器会检查是否覆盖了所有分支
- 不会出现"既不是成功也不是失败"的中间状态

**Q18: 什么是 KSP？为什么用它？**

KSP（Kotlin Symbol Processing）是 Kotlin 的注解处理器，替代了旧的 kapt。它直接处理 Kotlin 代码（而不是先转成 Java），速度可以快 2 倍以上。项目中 Hilt 和 Room 的注解处理都使用了 KSP。

**Q19: Coil 图片加载库有什么特点？**

1. 专为 Kotlin Coroutines 设计
2. 为 Compose 提供原生支持（`AsyncImage` composable）
3. 支持本地文件和网络 URL
4. 自动缓存和内存管理

**Q20: 解释以下这行代码的作用**

```kotlin
@Inject constructor(
    @ApplicationContext private val appContext: Context
)
```

意思是：这个类的构造函数依赖 `Context` 对象，而 `@ApplicationContext` 告诉 Hilt 注入的是 Application 级别的 Context（不是 Activity 级别的），由 Hilt 自动提供。

---

## 总结

这个项目虽然是用 AI 生成的，但它实际上涵盖了很多现代 Android 开发的核心技术：

- **架构层面**：MVVM + Repository 模式，代码分层清晰
- **依赖注入**：Hilt，已经是 Android 官方标准
- **UI**：Jetpack Compose + Material 3 + 自适应布局
- **数据持久化**：Room + DataStore
- **异步编程**：Kotlin Coroutines + Flow
- **网络**：Retrofit + OkHttp
- **AI 集成**：对接了三大云服务商的 AI API

作为面试准备，你需要重点理解以下概念：
1. MVVM 的"数据驱动 UI"思想
2. ViewModel 的生命周期特性
3. StateFlow 和 collectAsState 的配合
4. Hilt 的 @Inject / @Provides / @HiltViewModel
5. suspend 函数的执行流程
6. Compose 的声明式 UI 和重组机制

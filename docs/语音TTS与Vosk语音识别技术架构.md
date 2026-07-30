# HsiaoWear 语音 TTS 与 Vosk 语音识别技术架构

## 目录

1. [TTS 文字朗读系统](#1-tts-文字朗读系统)
2. [Vosk 语音识别系统](#2-vosk-语音识别系统)
3. [自动播放功能](#3-自动播放功能)
4. [录音状态浮层](#4-录音状态浮层)
5. [依赖与配置](#5-依赖与配置)
6. [常见问题与调试](#6-常见问题与调试)

---

## 1. TTS 文字朗读系统

### 1.1 架构概述

```
LobsterScreen (UI)
    │  点击朗读按钮 / 自动播放触发
    ▼
TtsManager (核心管理类)
    │  speak() / speakQueued() / stop()
    ▼
Android TextToSpeech API
    │  bindService → TTS_SERVICE
    ▼
TTS Engine (Google / OPPO / 其他)
    │  synthesizeToFile / speak
    ▼
AudioTrack → 扬声器
```

### 1.2 TtsManager 类 ([`util/TtsManager.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/util/TtsManager.kt))

#### 生命周期

```
初始化 → 引擎发现 → 绑定 → 就绪 → speak/stop → shutdown
```

#### 引擎发现与 fallback 机制

```kotlin
// 1. 默认 TextToSpeech(context, listener) → 依赖系统 TTS 服务框架
// 失败时逐一尝试已知引擎
val knownTtsEnginePackages = listOf(
    "com.oplus.ttsaccessibilityengine",  // OPPO / OnePlus
    "com.google.android.tts",            // Google
    "com.xiaomi.mibrain.speech",         // 小米
    "com.iflytek.speechcloud",           // 讯飞
    "com.baidu.duersdk.tts",            // 百度
)
```

**关键方法：**

| 方法 | 功能 | TTS 队列模式 |
|---|---|---|
| `speak(text)` | 朗读文本（打断当前） | `QUEUE_FLUSH` |
| `speakQueued(text)` | 追加到队列末尾 | `QUEUE_ADD` |
| `stop()` | 停止朗读清空队列 | `QUEUE_FLUSH` + 空文本 |

#### 初始化流程

```
logAvailableEngines()        → 通过 PackageManager 查询所有 TTS_SERVICE
createTts(context, null)     → 默认 TextToSpeech(context)
├── 成功 → onTtsReady()      → 设置语言、语速、音调、UtteranceProgressListener
└── 失败 → tryNextEngine()   → 逐一尝试 knownTtsEnginePackages
    ├── 成功 → onTtsReady()
    └── 全部失败 → 日志 "TTS 不可用"
```

#### 引擎可见性修复（Android 11+）

```xml
<!-- AndroidManifest.xml - 关键！Android 11+ 包可见性限制 -->
<queries>
    <intent>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent>
</queries>
```

不加此声明，`PackageManager.queryIntentServices(TTS_SERVICE)` 返回空列表。

#### 语言设置策略

```kotlin
// 依次尝试，取第一个可用的
val localesToTry = listOf(
    Locale.SIMPLIFIED_CHINESE to "简体中文",
    Locale.CHINESE to "中文",
    Locale.US to "英语"
)
// 判断标准：LANG_COUNTRY_AVAILABLE || LANG_AVAILABLE
```

#### 预热机制

```kotlin
private fun warmupTts() {
    // 静默预热（音量 0），解决小米/OPPO 引擎首次无声
    val warmupParams = Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0f)
    }
    tts?.speak(" ", TextToSpeech.QUEUE_FLUSH, warmupParams, "tts_warmup")
}
```

#### 回调监听

```kotlin
tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
    override fun onStart(utteranceId: String?)  // 开始朗读
    override fun onDone(utteranceId: String?)   // 朗读结束
    override fun onError(utteranceId: String?)  // 朗读出错
})
// isSpeaking 状态 + onSpeakingStateChanged 回调
```

---

## 2. Vosk 语音识别系统

### 2.1 架构概述

```
LobsterScreen (UI)
    │  按下语音按钮 → startRecording()
    ▼
VoskSpeechHelper (管理类)
    │  加载模型 → 启动 SpeechService
    ▼
Vosk Native Library (libvosk.so)
    │  麦克风音频流 → 解码
    ▼
onPartialResult → 实时更新 inputText
onResult / onFinalResult → 发送到 ViewModel
```

### 2.2 VoskSpeechHelper 类 ([`util/VoskSpeechHelper.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/util/VoskSpeechHelper.kt))

#### 状态机

```
IDLE → MODEL_DOWNLOADING → MODEL_READY → RECORDING → IDLE
                                                        │
                                                    ERROR (重试)
```

#### 模型下载与加载

```kotlin
// 模型来源：AlphaCephei 中文小模型
private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
private const val MODEL_DIR_NAME = "vosk-model-small-cn-0.22"

// 存储在 App 私有目录
val modelDir = File(context.filesDir, MODEL_DIR_NAME)
```

**关键验证：** 加载前检查目录完整性

```kotlin
// 检查 modelDir 下是否包含 am/、conf/、graph/ 等关键子目录
val requiredSubDirs = listOf("am", "conf", "graph")
val missing = requiredSubDirs.filter { !File(modelDir, it).exists() }
```

如果目录不完整，自动删除并重新下载。

#### 录音流程

```kotlin
// 开始录音
fun startRecording() {
    speechService?.startListening(object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            // 实时部分结果 → onPartialResult 回调 → 更新 inputText
        }
        override fun onResult(hypothesis: String) {
            // 最终结果 → onResult 回调 → 发送消息
        }
        override fun onFinalResult(hypothesis: String) {
            // 最终结果（另一种回调）→ onResult 回调
        }
        override fun onError(e: Exception) { ... }
        override fun onTimeout() { ... }
    })
}

// 停止录音
fun stopRecording() {
    speechService?.stop()    // 会触发 onResult / onFinalResult
    speechService = null
    isRecording = false
}
```

#### 权限处理

```kotlin
// AndroidManifest.xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />

// 运行时请求
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) {
        voskHelper.initialize(scope)
        voskHelper.startRecording()
    }
}
```

### 2.3 JNA 依赖处理（关键坑）

Vosk Android 依赖 JNA（Java Native Access），但传递依赖是 JAR 版本，不包含 Android 的 `.so` 文件。

```kotlin
// build.gradle.kts - 正确配置
implementation(libs.vosk.android) {
    exclude(group = "net.java.dev.jna", module = "jna")  // 排除 JAR
}
implementation("net.java.dev.jna:jna:5.14.0@aar")        // 显式 AAR
```

JNA AAR 包含 `libjnidispatch.so`，需放置到 `jniLibs/` 目录：

```
app/src/main/jniLibs/
├── arm64-v8a/libjnidispatch.so
├── armeabi-v7a/libjnidispatch.so
├── x86/libjnidispatch.so
└── x86_64/libjnidispatch.so
```

---

## 3. 自动播放功能

### 3.1 逻辑流程

```
用户开启"自动朗读"开关 (autoPlay = true)
    │
    ├── 用户发送消息
    │   └── LaunchedEffect(messages.lastOrNull{it.isUser}?.id)
    │       └── ttsManager.stop()   ← 停止当前朗读
    │
    └── AI 回复到达 (messages.size 变化)
        └── LaunchedEffect(messages.size, autoPlay)
            └── 获取最后一条用户消息之后的 AI 回复
                ├── 第一条: ttsManager.speak()       ← QUEUE_FLUSH
                └── 后续:   ttsManager.speakQueued()  ← QUEUE_ADD
```

### 3.2 关键代码

```kotlin
// 自动播放开关
var autoPlay by remember { mutableStateOf(false) }
val lastAutoPlayedMessageId = remember { mutableStateOf<Long?>(null) }

// 监听新用户消息 → 停止 TTS
LaunchedEffect(messages.lastOrNull { it.isUser }?.id) {
    if (autoPlay) {
        ttsManager.stop()
        lastAutoPlayedMessageId.value = null
    }
}

// 监听新 AI 回复 → 排队朗读
LaunchedEffect(messages.size, autoPlay) {
    if (!autoPlay || messages.isEmpty()) return@LaunchedEffect
    val lastUserIdx = messages.indexOfLast { it.isUser }
    if (lastUserIdx < 0) return@LaunchedEffect
    val unspoken = messages.drop(lastUserIdx + 1)
        .filter { !it.isUser && it.id != lastAutoPlayedMessageId.value }
    if (unspoken.isEmpty()) return@LaunchedEffect
    
    unspoken.forEachIndexed { i, msg ->
        if (i == 0) ttsManager.speak(msg.text)
        else ttsManager.speakQueued(msg.text)
        lastAutoPlayedMessageId.value = msg.id
    }
}
```

### 3.3 UI 开关

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp),
    horizontalArrangement = Arrangement.End
) {
    Text("自动朗读")
    Switch(checked = autoPlay, onCheckedChange = { ... })
}
```

---

## 4. 录音状态浮层

### 4.1 触发条件

`isVoiceRecording = true` 时显示全屏半透明浮层。

### 4.2 UI 布局

```
┌─────────────────────────────┐
│     半透明暗色遮罩           │
│                             │
│         ○ 脉冲光圈          │
│        / \
│       ○ 大号麦克风          │
│        \ /  (脉动缩放)      │
│                             │
│     "正在聆听..."           │
│     或实时识别文字           │
│                             │
│      松开发送               │
│                             │
└─────────────────────────────┘
```

### 4.3 动画实现

```kotlin
// 脉冲动画：无限循环
val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f, targetValue = 1.15f,
    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
)
val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.6f, targetValue = 0.2f,
    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
)
```

### 4.4 实时文字更新

```kotlin
// Vosk 部分结果 → 直接更新 inputText
voskHelper.onPartialResult = { text ->
    inputText = text  // 输入框实时更新
}

// RecordingOverlay 显示相同文字
RecordingOverlay(partialText = inputText)
```

---

## 5. 依赖与配置

### 5.1 build.gradle.kts

```kotlin
// TTS：无额外依赖，使用 Android SDK 内置 API
// 仅需 AndroidManifest.xml 中加 <queries>

// Vosk
implementation(libs.vosk.android) {
    exclude(group = "net.java.dev.jna", module = "jna")
}
implementation("net.java.dev.jna:jna:5.14.0@aar")
```

### 5.2 version catalog (libs.versions.toml)

```toml
[versions]
vosk-android = "0.3.47+"

[libraries]
vosk-android = { module = "com.alphacephei:vosk-android", version.ref = "vosk-android" }
```

### 5.3 AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Android 11+ 包可见性 -->
<queries>
    <intent>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent>
</queries>
```

---

## 6. 常见问题与调试

### 6.1 日志 Tag

| 模块 | Tag | 命令 |
|---|---|---|
| TTS 朗读 | `HsiaoWear-TTS` | `adb logcat -s HsiaoWear-TTS` |
| 语音识别 | `HsiaoWear-Voice` | `adb logcat -s HsiaoWear-Voice` |
| Vosk 内部 | `VoskAPI` | `adb logcat -s VoskAPI` |
| 自动播放 | `HsiaoWear-AutoPlay` | `adb logcat -s HsiaoWear-AutoPlay` |

### 6.2 调试 TTS 引擎列表

```kotlin
// TtsManager 初始化时自动打印
adb logcat -s HsiaoWear-TTS | findstr "系统可见"
// 输出示例:
// 系统可见的 TTS 引擎:
//   [1] com.google.android.tts/...GoogleTtsService (enabled=true)
//   [2] com.oplus.ttsaccessibilityengine/...TtsService (enabled=true)
```

### 6.3 已知问题

| 问题 | 原因 | 解决 |
|---|---|---|
| TTS status=-1 | 系统 TTS 框架缺失（OPPO 等 ROM） | 安装 Google TTS 或使用 fallback 引擎 |
| TTS 无声音 | 引擎已初始化但语音数据未下载 | 去设置 → 文字转语音 → 下载语音数据 |
| `libjnidispatch.so` not found | JNA 传递依赖是 JAR 非 AAR | 使用 `@aar` 显式依赖 + jniLibs |
| Vosk SIGSEGV | 模型文件损坏或不完整 | 自动检测并重新下载 |

### 6.4 ADB 命令

```powershell
# 查看默认 TTS 引擎
adb shell settings get secure tts_default_synth

# 设置默认 TTS 引擎
adb shell settings put secure tts_default_synth com.google.android.tts

# 打开 TTS 设置页面（部分 ROM 不支持）
adb shell am start -a android.settings.TTS_SETTINGS

# 安装 Google TTS（从 APK）
adb install-multiple base.apk split_config.arm64_v8a.apk split_config.zh.apk

# 查看 Vosk 模型目录
adb shell ls -la /data/data/com.example.hsiaowear/files/vosk-model-small-cn-0.22/

# 清除 App 数据（重置所有状态）
adb shell pm clear com.example.hsiaowear
```

### 6.5 代码文件索引

| 文件 | 作用 |
|---|---|
| [`util/TtsManager.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/util/TtsManager.kt) | TTS 核心管理类 |
| [`util/VoskSpeechHelper.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/util/VoskSpeechHelper.kt) | Vosk 语音识别管理类 |
| [`util/SuperEllipseShape.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/util/SuperEllipseShape.kt) | 按钮形状定义 |
| [`ui/screen/LobsterScreen.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/ui/screen/LobsterScreen.kt) | AI 对话页面（集成 TTS + Vosk） |
| [`AndroidManifest.xml`](file:///z:/2projects/HsiaoWear/app/src/main/AndroidManifest.xml) | 权限与查询声明 |
| [`app/build.gradle.kts`](file:///z:/2projects/HsiaoWear/app/build.gradle.kts) | JNA 依赖配置 |

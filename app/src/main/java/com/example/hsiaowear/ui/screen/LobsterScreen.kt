package com.example.hsiaowear.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hsiaowear.R
import android.util.Log
import com.example.hsiaowear.ui.components.EmptyState
import com.example.hsiaowear.util.rememberTtsManager
import com.example.hsiaowear.util.VoskSpeechHelper
import com.example.hsiaowear.viewmodel.ChatMessage
import com.example.hsiaowear.viewmodel.LobsterViewModel

@Composable
fun LobsterScreen(viewModel: LobsterViewModel, paddingValues: PaddingValues) {
    val context = LocalContext.current
    val TAG = "HsiaoWear-Voice"
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val ttsManager = rememberTtsManager()
    var speakingMessageId by remember { mutableStateOf<Long?>(null) }

    // 自动播放开关
    var autoPlay by remember { mutableStateOf(false) }
    // 记录最后播放过的 AI 消息 ID，避免重复播放
    val lastAutoPlayedMessageId = remember { mutableStateOf<Long?>(null) }

    // Vosk 语音识别
    val voskHelper = remember { VoskSpeechHelper(context) }
    val scope = rememberCoroutineScope()
    var isVoiceRecording by remember { mutableStateOf(false) }

    // 监听 Vosk 状态变化
    DisposableEffect(Unit) {
        voskHelper.onStateChanged = { state ->
            Log.d(TAG, "Vosk 状态变化: $state")
        }
        onDispose {
            voskHelper.onStateChanged = null
        }
    }

    // 录音权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "录音权限结果: granted=$granted")
        if (granted) {
            Log.d(TAG, "权限已授予，初始化并开始录音")
            voskHelper.initialize(scope)
            if (voskHelper.isModelReady) {
                voskHelper.startRecording()
                isVoiceRecording = true
                Log.d(TAG, "录音已开始")
            } else {
                Log.d(TAG, "模型未就绪，等待自动初始化完成后开始")
            }
        } else {
            Log.w(TAG, "用户拒绝了录音权限，无法使用语音输入")
        }
    }

    // 初始化 Vosk 模型（后台下载）
    LaunchedEffect(Unit) {
        Log.d(TAG, "开始初始化 Vosk 模型")
        voskHelper.initialize(scope)
    }

    // 监听 Vosk 结果和状态
    DisposableEffect(Unit) {
        voskHelper.onResult = { text ->
            if (text.isNotBlank()) {
                Log.d(TAG, "Vosk 识别结果: '$text'")
                viewModel.sendMessage(text)
            } else {
                Log.d(TAG, "Vosk 识别结果为空，忽略")
            }
        }
        voskHelper.onPartialResult = { text ->
            if (text.isNotBlank()) {
                Log.d(TAG, "Vosk 部分识别: '$text'")
                inputText = text
            }
        }
        voskHelper.onStateChanged = { state ->
            Log.d(TAG, "Vosk 状态变化: $state")
        }
        onDispose {
            voskHelper.onResult = null
            voskHelper.onPartialResult = null
            voskHelper.onStateChanged = null
            voskHelper.release()
            Log.d(TAG, "Vosk 资源已释放")
        }
    }

    // 监听朗读状态，朗读结束时清除 speakingMessageId
    DisposableEffect(Unit) {
        ttsManager.onSpeakingStateChanged = { isSpeaking ->
            if (!isSpeaking) {
                speakingMessageId = null
            }
        }
        onDispose {
            ttsManager.onSpeakingStateChanged = null
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // 自动播放逻辑：检测新用户消息 -> 停止当前 TTS
    LaunchedEffect(messages.lastOrNull { it.isUser }?.id) {
        if (autoPlay) {
            Log.d("HsiaoWear-AutoPlay", "检测到新用户消息，停止自动播放")
            ttsManager.stop()
            lastAutoPlayedMessageId.value = null
        }
    }

    // 自动播放逻辑：检测新 AI 回复 -> 加入 TTS 队列
    LaunchedEffect(messages.size, autoPlay) {
        if (!autoPlay || messages.isEmpty()) return@LaunchedEffect
        val lastUserIdx = messages.indexOfLast { it.isUser }
        if (lastUserIdx < 0) return@LaunchedEffect
        // 获取最后一条用户消息之后、未播放过的 AI 回复
        val unspoken = messages.drop(lastUserIdx + 1)
            .filter { !it.isUser && it.id != lastAutoPlayedMessageId.value }
        if (unspoken.isEmpty()) return@LaunchedEffect
        Log.d("HsiaoWear-AutoPlay", "自动播放 ${unspoken.size} 条 AI 回复")
        // 第一条用 QUEUE_FLUSH（打断当前），后续用 QUEUE_ADD（排队）
        unspoken.forEachIndexed { i, msg ->
            if (i == 0) {
                ttsManager.speak(msg.text)
            } else {
                ttsManager.speakQueued(msg.text)
            }
            lastAutoPlayedMessageId.value = msg.id
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 自动播放开关（顶部靠右）
            Row(
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "自动朗读",
                style = MaterialTheme.typography.labelSmall,
                color = if (autoPlay)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = autoPlay,
                onCheckedChange = {
                    autoPlay = it
                    if (!it) {
                        ttsManager.stop()
                        lastAutoPlayedMessageId.value = null
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.height(24.dp)
            )
        }

        if (messages.isEmpty()) {
            LobsterEmptyState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        isSpeaking = speakingMessageId == message.id,
                        onSpeakToggle = {
                            if (speakingMessageId == message.id) {
                                ttsManager.stop()
                                speakingMessageId = null
                            } else {
                                ttsManager.stop()
                                ttsManager.speak(message.text)
                                speakingMessageId = message.id
                            }
                        }
                    )
                }
                if (isTyping) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(
                                            topStart = 20.dp,
                                            topEnd = 20.dp,
                                            bottomStart = 4.dp,
                                            bottomEnd = 20.dp
                                        )
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                TypingIndicator()
                            }
                        }
                    }
                }
            }
        }

        // TTS 引擎切换提示
        Text(
            text = "朗读语音可在 设置 > 辅助功能 > 文字转语音 > 首选引擎 更换",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
        )

        InputBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                }
            },
            isLoading = isTyping,
            isVoiceRecording = isVoiceRecording,
            onVoiceStart = {
                Log.d(TAG, "语音按钮按下, isModelReady=${voskHelper.isModelReady}, hasPermission=${voskHelper.hasPermission()}")
                if (voskHelper.isModelReady) {
                    if (voskHelper.hasPermission()) {
                        voskHelper.startRecording()
                        isVoiceRecording = true
                        Log.d(TAG, "✓ 录音已开始")
                    } else {
                        Log.d(TAG, "请求录音权限")
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    Log.w(TAG, "Vosk 模型未就绪，无法录音")
                }
            },
            onVoiceEnd = {
                if (isVoiceRecording) {
                    Log.d(TAG, "语音按钮释放，停止录音")
                    voskHelper.stopRecording()
                    isVoiceRecording = false
                    Log.d(TAG, "✓ 录音已停止")
                }
            }
        )

        // 关闭 Column
    }

        // 录音状态浮层（按住说话时覆盖全屏）
        if (isVoiceRecording) {
            RecordingOverlay(partialText = inputText)
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isSpeaking: Boolean = false,
    onSpeakToggle: () -> Unit = {}
) {
    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(
                    color = if (isUser)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(
                        topStart = if (isUser) 20.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 20.dp,
                        bottomStart = 20.dp,
                        bottomEnd = 20.dp
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize()
        ) {
            if (isUser) {
                // 用户消息：只显示文本
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                // AI 消息：文本 + 朗读按钮
                Column {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onSpeakToggle,
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isSpeaking)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSpeaking)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = if (isSpeaking)
                                    Icons.Default.VolumeUp
                                else
                                    Icons.Default.VolumeUp,
                                contentDescription = if (isSpeaking) "停止朗读" else "朗读",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Dot(delay = 0)
        Dot(delay = 150)
        Dot(delay = 300)
    }
}

@Composable
private fun Dot(delay: Int) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(delay.toLong())
            visible = !visible
            kotlinx.coroutines.delay(400)
            visible = !visible
        }
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (visible) 0.8f else 0.2f),
                shape = RoundedCornerShape(50)
            )
    )
}

@Composable
private fun InputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    isVoiceRecording: Boolean = false,
    onVoiceStart: () -> Unit = {},
    onVoiceEnd: () -> Unit = {}
) {
    var isVoicePressed by remember { mutableStateOf(false) }

    val micTint by animateColorAsState(
        targetValue = when {
            isVoiceRecording -> MaterialTheme.colorScheme.error
            isVoicePressed -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "micTint"
    )

    val micBg by animateColorAsState(
        targetValue = when {
            isVoicePressed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else -> Color.Transparent
        },
        label = "micBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            placeholder = { Text(stringResource(R.string.lobster_placeholder), style = MaterialTheme.typography.bodyLarge) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            textStyle = MaterialTheme.typography.bodyLarge,
            maxLines = 4
        )
        Spacer(modifier = Modifier.width(4.dp))
        // Vosk 语音按钮（按住说话，松手自动发送）
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(micBg)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isVoicePressed = true
                            onVoiceStart()
                            try {
                                awaitRelease()
                            } catch (_: kotlinx.coroutines.CancellationException) { }
                            isVoicePressed = false
                            onVoiceEnd()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "语音输入",
                tint = micTint,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(
            onClick = onSend,
            enabled = inputText.isNotBlank() && !isLoading
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.lobster_send),
                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun LobsterEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = "🤖",
            title = stringResource(R.string.lobster_empty),
            subtitle = stringResource(R.string.lobster_empty_hint),
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/** 按住说话时的全屏录音状态浮层 */
@Composable
private fun RecordingOverlay(partialText: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = androidx.compose.animation.core.EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = androidx.compose.animation.core.EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // 阻止触摸穿透
            ),
        contentAlignment = Alignment.Center
    ) {
        val errorColor = MaterialTheme.colorScheme.error
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // 脉冲光圈
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    drawCircle(
                        color = errorColor.copy(alpha = pulseAlpha),
                        radius = size.minDimension / 2
                    )
                    drawCircle(
                        color = errorColor.copy(alpha = pulseAlpha * 0.5f),
                        radius = size.minDimension / 2.5f
                    )
                }
                // 大号麦克风图标
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "录音中",
                    tint = Color.White,
                    modifier = Modifier
                        .size(64.dp)
                        .scale(pulseScale)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 识别中的文字
            Text(
                text = partialText.ifBlank { "正在聆听..." },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 松手提示
            Text(
                text = "松开发送",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

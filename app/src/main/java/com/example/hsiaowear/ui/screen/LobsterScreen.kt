package com.example.hsiaowear.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hsiaowear.R
import com.example.hsiaowear.ui.components.EmptyState
import com.example.hsiaowear.util.rememberTtsManager
import com.example.hsiaowear.util.VoskSpeechHelper
import com.example.hsiaowear.viewmodel.ChatMessage
import com.example.hsiaowear.viewmodel.LobsterViewModel

@Composable
fun LobsterScreen(viewModel: LobsterViewModel, paddingValues: PaddingValues) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val ttsManager = rememberTtsManager()
    var speakingMessageId by remember { mutableStateOf<Long?>(null) }

    // Vosk 语音识别
    val voskHelper = remember { VoskSpeechHelper(context) }
    val scope = rememberCoroutineScope()
    var isVoiceRecording by remember { mutableStateOf(false) }

    // 录音权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voskHelper.initialize(scope)
            voskHelper.startRecording(scope)
            isVoiceRecording = true
        }
    }

    // 初始化 Vosk 模型（后台下载）
    LaunchedEffect(Unit) {
        voskHelper.initialize(scope)
    }

    // 监听 Vosk 结果和状态
    DisposableEffect(Unit) {
        voskHelper.onResult = { text ->
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)
            }
        }
        onDispose {
            voskHelper.onResult = null
            voskHelper.onStateChanged = null
            voskHelper.release()
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

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding()) {
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
                if (voskHelper.isModelReady) {
                    if (voskHelper.hasPermission()) {
                        voskHelper.startRecording(scope)
                        isVoiceRecording = true
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            onVoiceEnd = {
                if (isVoiceRecording) {
                    voskHelper.stopRecording()
                    isVoiceRecording = false
                }
            }
        )
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
    val micTint by animateColorAsState(
        targetValue = if (isVoiceRecording)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        label = "micTint"
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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onVoiceStart()
                            try {
                                awaitRelease()
                            } catch (_: kotlinx.coroutines.CancellationException) { }
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

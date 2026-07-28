package com.example.hsiaowear.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * TTS（文字转语音）管理器，用于朗读 AI 回复文本。
 * 需要在 Compose 中通过 remember + DisposableEffect 管理生命周期。
 */
class TtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    /** TTS 初始化回调 */
    private var onInitCallback: (() -> Unit)? = null

    /** 朗读状态变化回调 */
    var onSpeakingStateChanged: ((isSpeaking: Boolean) -> Unit)? = null

    /** 当前是否正在朗读 */
    var isSpeaking: Boolean = false
        private set

    init {
        tts = TextToSpeech(context) { status ->
            isInitialized = status == TextToSpeech.SUCCESS
            if (isInitialized) {
                tts?.language = Locale.CHINESE
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        onSpeakingStateChanged?.invoke(true)
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        onSpeakingStateChanged?.invoke(false)
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        onSpeakingStateChanged?.invoke(false)
                    }
                })
            }
            onInitCallback?.invoke()
        }
    }

    /**
     * 朗读指定的文本。
     * 如果 TTS 未初始化完成，会等待初始化后再朗读。
     */
    fun speak(text: String) {
        if (!isInitialized) {
            onInitCallback = {
                speakInternal(text)
                onInitCallback = null
            }
            return
        }
        speakInternal(text)
    }

    private fun speakInternal(text: String) {
        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
    }

    /** 停止当前朗读 */
    fun stop() {
        tts?.stop()
        isSpeaking = false
        onSpeakingStateChanged?.invoke(false)
    }

    /** 释放 TTS 资源 */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        isInitialized = false
    }
}

/**
 * 在 Composable 中记住一个 TtsManager 实例，并在离开时自动释放资源。
 */
@Composable
fun rememberTtsManager(): TtsManager {
    val context = LocalContext.current
    val manager = remember { TtsManager(context) }
    DisposableEffect(Unit) {
        onDispose {
            manager.shutdown()
        }
    }
    return manager
}

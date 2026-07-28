package com.example.hsiaowear.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Vosk 语音识别辅助类。
 * 管理模型下载、音频录制和语音识别。
 */
class VoskSpeechHelper(private val context: Context) {

    companion object {
        private const val TAG = "VoskSpeechHelper"
        /** Vosk 中文小模型 */
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
        private const val MODEL_DIR_NAME = "vosk-model-small-cn-0.22"
        private const val SAMPLE_RATE = 16000
    }

    /** 模型是否已就绪 */
    var isModelReady: Boolean = false
        private set

    /** 模型下载进度 0.0 ~ 1.0 */
    var downloadProgress: Float = 0f
        private set

    /** 是否正在录音 */
    var isRecording: Boolean = false
        private set

    /** 识别结果回调 */
    var onResult: ((String) -> Unit)? = null

    /** 状态变化回调 */
    var onStateChanged: ((State) -> Unit)? = null

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    /** 语音助手状态 */
    enum class State {
        MODEL_DOWNLOADING,
        MODEL_READY,
        RECORDING,
        RECOGNIZING,
        ERROR
    }

    // ==================== 模型管理 ====================

    private fun getModelPath(): String {
        return File(context.filesDir, MODEL_DIR_NAME).absolutePath
    }

    fun isModelDownloaded(): Boolean {
        val modelDir = File(getModelPath())
        return modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true
    }

    fun initialize(scope: CoroutineScope) {
        if (isModelDownloaded()) {
            loadModel()
        } else {
            onStateChanged?.invoke(State.MODEL_DOWNLOADING)
            scope.launch {
                downloadModel()
            }
        }
    }

    private suspend fun downloadModel() = withContext(Dispatchers.IO) {
        try {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            val totalSize = connection.contentLengthLong
            var downloadedSize = 0L

            val inputStream = connection.inputStream
            val zipStream = ZipInputStream(inputStream)
            val buffer = ByteArray(8192)

            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val targetFile = File(context.filesDir, entry.name)
                    targetFile.parentFile?.mkdirs()
                    val fos = FileOutputStream(targetFile)
                    var bytesRead: Int
                    while (zipStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        if (totalSize > 0) {
                            downloadProgress = downloadedSize.toFloat() / totalSize
                        }
                    }
                    fos.close()
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()
            inputStream.close()

            withContext(Dispatchers.Main) {
                downloadProgress = 1f
                loadModel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "模型下载失败", e)
            withContext(Dispatchers.Main) {
                onStateChanged?.invoke(State.ERROR)
            }
        }
    }

    private fun loadModel() {
        try {
            model = Model(getModelPath())
            isModelReady = true
            onStateChanged?.invoke(State.MODEL_READY)
            Log.d(TAG, "Vosk 模型加载成功")
        } catch (e: Exception) {
            Log.e(TAG, "模型加载失败", e)
            onStateChanged?.invoke(State.ERROR)
        }
    }

    // ==================== 录音与识别 ====================

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(scope: CoroutineScope) {
        if (!isModelReady || isRecording) return
        if (!hasPermission()) {
            onStateChanged?.invoke(State.ERROR)
            return
        }

        isRecording = true
        onStateChanged?.invoke(State.RECORDING)

        try {
            recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord?.startRecording()

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        recognizer?.acceptWaveform(buffer, bytesRead)
                    }
                }
            }
            Log.d(TAG, "开始录音识别")
        } catch (e: Exception) {
            Log.e(TAG, "录音启动失败", e)
            isRecording = false
            onStateChanged?.invoke(State.ERROR)
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        onStateChanged?.invoke(State.RECOGNIZING)

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord 释放异常", e)
        }

        val resultText = parseResult()
        recognizer?.reset()
        recognizer = null

        Log.d(TAG, "识别结果: $resultText")

        if (resultText.isNotBlank()) {
            onResult?.invoke(resultText)
        }

        onStateChanged?.invoke(State.MODEL_READY)
    }

    private fun parseResult(): String {
        return try {
            val resultJson = recognizer?.result ?: "{}"
            val json = JSONObject(resultJson)
            json.optString("text", "").trim()
        } catch (e: Exception) {
            Log.w(TAG, "结果解析失败", e)
            ""
        }
    }

    fun release() {
        try {
            isRecording = false
            recordingJob?.cancel()
            recordingJob = null
            audioRecord?.release()
            audioRecord = null
            recognizer?.reset()
            recognizer = null
            model = null
            isModelReady = false
        } catch (e: Exception) {
            Log.w(TAG, "资源释放异常", e)
        }
    }
}

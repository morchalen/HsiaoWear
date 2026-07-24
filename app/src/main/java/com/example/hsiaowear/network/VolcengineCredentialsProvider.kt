package com.example.hsiaowear.network

import com.example.hsiaowear.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 火山引擎 AK/SK 凭证提供者，支持运行时动态更新。
 * 拦截器通过此提供者获取最新的密钥，无需重建 OkHttpClient。
 */
@Singleton
class VolcengineCredentialsProvider @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    @Volatile
    private var cachedAk: String = ""

    @Volatile
    private var cachedSk: String = ""

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 监听 DataStore 变化并缓存到内存
        scope.launch {
            settingsDataStore.apiSettingsFlow.collect { settings ->
                cachedAk = settings.volcEngineAccessKey
                cachedSk = settings.volcEngineSecretKey
            }
        }
    }

    fun getAccessKey(): String = cachedAk
    fun getSecretKey(): String = cachedSk
}

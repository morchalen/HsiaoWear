package com.example.hsiaowear.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hsiaowear.data.ApiSettings
import com.example.hsiaowear.data.Result
import com.example.hsiaowear.data.SettingsDataStore
import com.example.hsiaowear.data.ThemeSettings
import com.example.hsiaowear.data.TryOnSettings
import com.example.hsiaowear.data.repository.AIRepository
import com.example.hsiaowear.network.ApiProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val apiProviderRegistry: ApiProviderRegistry,
    private val aiRepository: AIRepository
) : ViewModel() {

    private val _apiSettings = MutableStateFlow(ApiSettings())
    val apiSettings: StateFlow<ApiSettings> = _apiSettings.asStateFlow()

    private val _themeSettings = MutableStateFlow(ThemeSettings())
    val themeSettings: StateFlow<ThemeSettings> = _themeSettings.asStateFlow()

    private val _testResult = MutableStateFlow<Boolean?>(null)
    val testResult: StateFlow<Boolean?> = _testResult.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _models = MutableStateFlow<List<String>>(listOf("gpt-3.5-turbo", "gpt-4", "gpt-4o"))
    val models: StateFlow<List<String>> = _models.asStateFlow()

    // ==================== AI 试衣设置 ====================

    private val _tryOnSettings = MutableStateFlow(TryOnSettings())
    val tryOnSettings: StateFlow<TryOnSettings> = _tryOnSettings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.tryOnSettingsFlow.collect {
                _tryOnSettings.value = it
            }
        }
        // 原有的 init 逻辑继续执行
        viewModelScope.launch {
            settingsDataStore.apiSettingsFlow.collect {
                _apiSettings.value = it
                apiProviderRegistry.setBaseUrl(it.baseUrl)
                apiProviderRegistry.setApiKey(it.apiKey)
            }
        }
        viewModelScope.launch {
            settingsDataStore.themeSettingsFlow.collect {
                _themeSettings.value = it
            }
        }
    }

    fun updateApiEndpoint(endpoint: String) {
        viewModelScope.launch {
            settingsDataStore.updateApiEndpoint(endpoint)
            apiProviderRegistry.setBaseUrl(endpoint)
        }
    }

    fun updateApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsDataStore.updateApiKey(apiKey)
        }
    }

    fun updateModelName(modelName: String) {
        viewModelScope.launch {
            settingsDataStore.updateModelName(modelName)
        }
    }

    fun updateSystemPrompt(prompt: String) {
        viewModelScope.launch {
            settingsDataStore.updateSystemPrompt(prompt)
        }
    }

    fun updateTemperature(temperature: Double) {
        viewModelScope.launch {
            settingsDataStore.updateTemperature(temperature)
        }
    }

    fun updateMaxTokens(maxTokens: Int) {
        viewModelScope.launch {
            settingsDataStore.updateMaxTokens(maxTokens)
        }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.updateThemeMode(mode)
        }
    }

    fun updateFontScale(scale: Int) {
        viewModelScope.launch {
            settingsDataStore.updateFontScale(scale)
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            val models = aiRepository.fetchModels()
            if (models.isNotEmpty()) {
                _models.value = models
            }
        }
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return settingsDataStore.apiSettingsFlow.first().isConfigured
    }

    suspend fun markOnboardingCompleted() {
        settingsDataStore.markOnboardingCompleted()
    }

    fun updateTryOnApiKey(key: String) {
        viewModelScope.launch {
            settingsDataStore.updateTryOnApiKey(key)
        }
    }

    fun updateTryOnApiHost(host: String) {
        viewModelScope.launch {
            settingsDataStore.updateTryOnApiHost(host)
        }
    }

    // ==================== 火山方舟（抠图）设置 ====================

    fun updateVolcengineAccessKey(accessKey: String) {
        viewModelScope.launch {
            settingsDataStore.updateVolcengineAccessKey(accessKey)
        }
    }

    fun updateVolcengineSecretKey(secretKey: String) {
        viewModelScope.launch {
            settingsDataStore.updateVolcengineSecretKey(secretKey)
        }
    }

    fun testApiConnection(baseUrl: String, apiKey: String, modelName: String = "gpt-3.5-turbo") {
        viewModelScope.launch {
            _isTesting.value = true
            try {
                apiProviderRegistry.setBaseUrl(baseUrl)
                apiProviderRegistry.setApiKey(apiKey)
                val result = aiRepository.testConnection(baseUrl, apiKey, modelName)
                _testResult.value = result is Result.Success
            } catch (e: Exception) {
                _testResult.value = false
            } finally {
                _isTesting.value = false
            }
        }
    }
}

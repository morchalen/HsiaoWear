package com.example.hsiaowear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hsiaowear_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_API_ENDPOINT = stringPreferencesKey("api_endpoint")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_MODEL_NAME = stringPreferencesKey("model_name")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val KEY_TEMPERATURE = stringPreferencesKey("temperature")
        private val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        private val KEY_VOLCENGINE_ACCESS_KEY = stringPreferencesKey("volcengine_access_key")
        private val KEY_VOLCENGINE_SECRET_KEY = stringPreferencesKey("volcengine_secret_key")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_FONT_SCALE = intPreferencesKey("font_scale")
        private val KEY_TRYON_API_KEY = stringPreferencesKey("tryon_api_key")
        private val KEY_TRYON_API_HOST = stringPreferencesKey("tryon_api_host")
    }

    val apiSettingsFlow: Flow<ApiSettings> = context.dataStore.data.map { prefs ->
        ApiSettings(
            baseUrl = prefs[KEY_API_ENDPOINT] ?: "",
            apiKey = prefs[KEY_API_KEY] ?: "",
            modelName = prefs[KEY_MODEL_NAME] ?: "gpt-3.5-turbo",
            systemPrompt = prefs[KEY_SYSTEM_PROMPT] ?: "",
            temperature = prefs[KEY_TEMPERATURE]?.toDoubleOrNull() ?: 0.7,
            maxTokens = prefs[KEY_MAX_TOKENS] ?: 2048,
            volcEngineAccessKey = prefs[KEY_VOLCENGINE_ACCESS_KEY] ?: "",
            volcEngineSecretKey = prefs[KEY_VOLCENGINE_SECRET_KEY] ?: "",
            isConfigured = prefs[KEY_ONBOARDING_COMPLETED] ?: false
        )
    }

    val themeSettingsFlow: Flow<ThemeSettings> = context.dataStore.data.map { prefs ->
        ThemeSettings(
            themeMode = prefs[KEY_THEME_MODE] ?: "system",
            fontScale = prefs[KEY_FONT_SCALE] ?: 2
        )
    }

    val tryOnSettingsFlow: Flow<TryOnSettings> = context.dataStore.data.map { prefs ->
        TryOnSettings(
            apiKey = prefs[KEY_TRYON_API_KEY] ?: "",
            host = prefs[KEY_TRYON_API_HOST] ?: ""
        )
    }

    suspend fun getTryOnSettings(): TryOnSettings {
        return context.dataStore.data.first().let { prefs ->
            TryOnSettings(
                apiKey = prefs[KEY_TRYON_API_KEY] ?: "",
                host = prefs[KEY_TRYON_API_HOST] ?: ""
            )
        }
    }

    suspend fun updateTryOnApiKey(key: String) {
        context.dataStore.edit { it[KEY_TRYON_API_KEY] = key }
    }

    suspend fun updateTryOnApiHost(host: String) {
        context.dataStore.edit { it[KEY_TRYON_API_HOST] = host }
    }

    suspend fun updateApiEndpoint(endpoint: String) {
        context.dataStore.edit { it[KEY_API_ENDPOINT] = endpoint }
    }

    suspend fun updateApiKey(apiKey: String) {
        context.dataStore.edit { it[KEY_API_KEY] = apiKey }
    }

    suspend fun updateModelName(modelName: String) {
        context.dataStore.edit { it[KEY_MODEL_NAME] = modelName }
    }

    suspend fun updateSystemPrompt(prompt: String) {
        context.dataStore.edit { it[KEY_SYSTEM_PROMPT] = prompt }
    }

    suspend fun updateTemperature(temperature: Double) {
        context.dataStore.edit { it[KEY_TEMPERATURE] = temperature.toString() }
    }

    suspend fun updateMaxTokens(maxTokens: Int) {
        context.dataStore.edit { it[KEY_MAX_TOKENS] = maxTokens }
    }

    suspend fun updateVolcEngineKeys(accessKey: String, secretKey: String) {
        context.dataStore.edit {
            it[KEY_VOLCENGINE_ACCESS_KEY] = accessKey
            it[KEY_VOLCENGINE_SECRET_KEY] = secretKey
        }
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun updateFontScale(scale: Int) {
        context.dataStore.edit { it[KEY_FONT_SCALE] = scale }
    }

    suspend fun markOnboardingCompleted() {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = true }
    }
}

data class ApiSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "gpt-3.5-turbo",
    val systemPrompt: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val volcEngineAccessKey: String = "",
    val volcEngineSecretKey: String = "",
    val isConfigured: Boolean = false
)

data class ThemeSettings(
    val themeMode: String = "system",
    val fontScale: Int = 2
)

data class TryOnSettings(
    val apiKey: String = "",
    val host: String = ""
)

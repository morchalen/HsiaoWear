package com.example.hsiaowear.network

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiProviderRegistry @Inject constructor() {
    private var baseUrl: String = ""
    private var apiKey: String = ""

    fun getBaseUrl(): String = baseUrl

    fun setBaseUrl(url: String) {
        this.baseUrl = url.trim()
        if (!this.baseUrl.endsWith("/")) {
            this.baseUrl += "/"
        }
    }

    fun getApiKey(): String = apiKey

    fun setApiKey(key: String) {
        this.apiKey = key.trim()
    }

    fun isConfigured(): Boolean = baseUrl.isNotBlank() && apiKey.isNotBlank()
}
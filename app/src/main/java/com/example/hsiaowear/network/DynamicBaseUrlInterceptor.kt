package com.example.hsiaowear.network

import okhttp3.Interceptor
import okhttp3.Response

class DynamicBaseUrlInterceptor(
    private val apiProviderRegistry: ApiProviderRegistry
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val baseUrl = apiProviderRegistry.getBaseUrl()

        if (baseUrl.isNotBlank()) {
            val originalUrl = request.url
            val urlBuilder = originalUrl.newBuilder()

            if (baseUrl.contains("://")) {
                val scheme = baseUrl.substringBefore("://")
                val hostAndPath = baseUrl.substringAfter("://")
                val host = hostAndPath.substringBefore("/")
                val path = hostAndPath.substringAfter("/").ifEmpty { "" }

                urlBuilder.scheme(scheme).host(host)
                if (path.isNotBlank()) {
                    urlBuilder.addPathSegments(path)
                }
            } else {
                urlBuilder.host(baseUrl)
            }

            request = request.newBuilder().url(urlBuilder.build()).build()
        }

        return chain.proceed(request)
    }
}
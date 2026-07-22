package com.example.hsiaowear.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * OkHttp 拦截器，为请求自动添加美图开放平台的签名认证头。
 * 签名前会读取完整的请求体以计算 Content-SHA256。
 */
class MeituInterceptor(
    private val accessKey: String,
    private val secretKey: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // 读取请求体
        val bodyString = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        } ?: ""

        // 收集已有 headers
        val existingHeaders = mutableMapOf<String, String>()
        for (i in 0 until request.headers.size) {
            val name = request.headers.name(i)
            val value = request.headers.value(i)
            existingHeaders[name] = value
        }

        // 计算签名（带 body hash）
        val signed = MeituSigner.sign(
            ak = accessKey,
            sk = secretKey,
            method = request.method,
            url = request.url.toString(),
            existingHeaders = existingHeaders,
            body = bodyString
        )

        // 用签名后的 headers + 可重读的 body 重建请求
        val newBody = bodyString.toRequestBody("application/json".toMediaType())
        val newRequest = request.newBuilder().apply {
            // 移除可能重复的 header，再统一设置
            listOf("Host", "Content-Type", "X-Sdk-Date", "Authorization").forEach {
                removeHeader(it)
            }
            signed.headers.forEach { (k, v) ->
                addHeader(k, v)
            }
            // 替换 body（原始 body 已被消费）
            method(request.method, newBody)
        }.build()

        return chain.proceed(newRequest)
    }
}

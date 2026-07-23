package com.example.hsiaowear.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

private const val TAG = "HsiaoWear-VolcSign"

/**
 * OkHttp 拦截器，为请求自动添加火山引擎 API 签名认证头。
 *
 * 使用 AWS Signature V4 兼容的 HMAC-SHA256 签名算法。
 * AK/SK 通过 [VolcengineCredentialsProvider] 动态获取，支持运行时修改。
 */
class VolcengineInterceptor(
    private val credentialsProvider: VolcengineCredentialsProvider,
    private val region: String,
    private val service: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val accessKey = credentialsProvider.getAccessKey()
        val secretKey = credentialsProvider.getSecretKey()

        if (accessKey.isBlank() || secretKey.isBlank()) {
            Log.w(TAG, "火山引擎 AK/SK 未配置，跳过签名")
            return chain.proceed(request)
        }

        // 读取请求体
        val bodyString = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        } ?: ""

        // 计算签名
        val signed = VolcengineSigner.sign(
            ak = accessKey,
            sk = secretKey,
            method = request.method,
            url = request.url.toString(),
            region = region,
            service = service,
            body = bodyString
        )

        // 日志
        Log.d(TAG, "Request URL: ${request.url}")
        Log.d(TAG, "AK: ${accessKey.take(8)}...")
        Log.d(TAG, "Signed Host: ${signed.headers["Host"]}")
        Log.d(TAG, "Signed X-Date: ${signed.headers["X-Date"]}")
        Log.d(TAG, "Signed Authorization: ${signed.headers["Authorization"]?.take(120)}...")

        // 重建请求
        val newBody = bodyString.toRequestBody("application/json".toMediaType())
        val newRequest = request.newBuilder().apply {
            // 移除可能存在的旧签名头
            listOf("Host", "Content-Type", "X-Date", "Authorization").forEach {
                removeHeader(it)
            }
            signed.headers.forEach { (k, v) ->
                addHeader(k, v)
            }
            method(request.method, newBody)
        }.build()

        return chain.proceed(newRequest)
    }
}

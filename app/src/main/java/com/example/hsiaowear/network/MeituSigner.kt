package com.example.hsiaowear.network

import java.net.URI
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 美图开放平台 API 签名工具（SDK-HMAC-SHA256）
 * 参考：https://ai.meitu.com/doc/?id=218&type=api&lang=zh
 *
 * 签名流程：
 * 1. 构造 CanonicalRequest（含 body hash）
 * 2. 用 CanonicalRequest hash 构造 StringToSign
 * 3. 用 SecretKey 对 StringToSign 做 HMAC-SHA256
 * 4. 拼接 Authorization 头
 */
object MeituSigner {

    private const val ALGORITHM = "SDK-HMAC-SHA256"
    private const val HMAC_SHA256 = "HmacSHA256"
    private const val SHA_256 = "SHA-256"

    data class SignedResult(
        val headers: Map<String, String>,
        val xSdkDate: String
    )

    fun sign(
        ak: String,
        sk: String,
        method: String,
        url: String,
        existingHeaders: Map<String, String> = emptyMap(),
        body: String = ""
    ): SignedResult {
        val uri = URI(url)
        val host = uri.host ?: ""
        val xSdkDate = nowUtcFormatted()

        // ---- 1. 计算 body hash ----
        val payloadHash = sha256Hex(body)

        // ---- 2. 构造 CanonicalRequest ----
        val canonicalUri = uri.rawPath ?: "/"
        val canonicalQueryString = uri.rawQuery ?: ""

        val contentType = existingHeaders.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value ?: "application/json"

        // 参与签名计算的 headers（排序、小写）
        val signHeaders = linkedMapOf(
            "Content-Type" to contentType.trim(),
            "Host" to host,
            "X-Sdk-Date" to xSdkDate
        )

        val sortedHeaderNames = signHeaders.keys.map { it.lowercase() }.sorted()
        val canonicalHeaders = sortedHeaderNames.joinToString("") { name ->
            val value = signHeaders.entries.first { it.key.lowercase() == name }.value
            "$name:$value\n"
        }
        val signedHeadersStr = sortedHeaderNames.joinToString(";")

        // CanonicalRequest = HTTPMethod + \n + CanonicalURI + \n + CanonicalQueryString + \n + CanonicalHeaders + \n + SignedHeaders + \n + HashedPayload
        val canonicalRequest = buildString {
            append(method); append('\n')
            append(canonicalUri); append('\n')
            append(canonicalQueryString); append('\n')
            append(canonicalHeaders); append('\n')  // canonicalHeaders 已自带尾部 \n
            append(signedHeadersStr); append('\n')
            append(payloadHash)
        }

        // ---- 3. 构造 StringToSign ----
        val canonicalRequestHash = sha256Hex(canonicalRequest)
        val stringToSign = "$ALGORITHM\n$xSdkDate\n$canonicalRequestHash"

        // ---- 4. 计算签名 ----
        val signature = hmacSha256Hex(sk, stringToSign)

        // ---- 5. 构造 Authorization ----
        val authorization = "$ALGORITHM Access=$ak, SignedHeaders=$signedHeadersStr, Signature=$signature"

        val resultHeaders = mutableMapOf<String, String>()
        resultHeaders["Content-Type"] = contentType
        resultHeaders["Host"] = host
        resultHeaders["X-Sdk-Date"] = xSdkDate
        resultHeaders["Authorization"] = authorization

        return SignedResult(headers = resultHeaders, xSdkDate = xSdkDate)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance(SHA_256)
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256Hex(key: String, data: String): String {
        val mac = Mac.getInstance(HMAC_SHA256)
        val keySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), HMAC_SHA256)
        mac.init(keySpec)
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun nowUtcFormatted(): String {
        return ZonedDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
    }
}

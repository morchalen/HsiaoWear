package com.example.hsiaowear.network

import java.net.URL
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 火山引擎 API 签名工具（AWS Signature V4 兼容）
 *
 * 参考：https://www.volcengine.com/docs/6369/67269
 *
 * 签名流程：
 * 1. 构造 CanonicalRequest
 * 2. 构造 StringToSign
 * 3. 派生 SigningKey（SK → ShortDate → Region → Service → "request"）
 * 4. 计算 Signature = Hex(HMAC-SHA256(SigningKey, StringToSign))
 * 5. 构造 Authorization 头
 */
object VolcengineSigner {

    private const val ALGORITHM = "HMAC-SHA256"
    private const val HMAC_SHA256 = "HmacSHA256"
    private const val SHA_256 = "SHA-256"

    data class SignedResult(
        val headers: Map<String, String>,
        val xDate: String
    )

    /**
     * @param ak     Access Key ID
     * @param sk     Secret Access Key
     * @param method HTTP 方法（GET/POST/...）
     * @param url    完整请求 URL（含 query 参数）
     * @param region 服务地域（如 "cn-north-1"）
     * @param service 服务名（如 "cv"）
     * @param body   请求体字符串
     */
    fun sign(
        ak: String,
        sk: String,
        method: String,
        url: String,
        region: String,
        service: String,
        body: String = ""
    ): SignedResult {
        val parsedUrl = URL(url)
        val host = parsedUrl.host
        val path = parsedUrl.path.ifEmpty { "/" }
        // 对 Query 参数按 ASCII 升序排序（火山引擎签名规范要求）
        val query = parsedUrl.query?.let { rawQuery ->
            rawQuery.split("&")
                .filter { it.isNotBlank() }
                .sorted()
                .joinToString("&")
        } ?: ""
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val xDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val shortDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        // ---- 1. 计算 body hash ----
        val payloadHash = sha256Hex(body)

        // ---- 2. 构造 CanonicalRequest ----
        // CanonicalHeaders: host;x-date （必选参与签名）
        val canonicalHeaders = buildString {
            append("host:"); append(host); append('\n')
            append("x-date:"); append(xDate); append('\n')
        }
        val signedHeaders = "host;x-date"

        val canonicalRequest = buildString {
            append(method); append('\n')
            append(path); append('\n')
            append(query); append('\n')
            append(canonicalHeaders); append('\n')
            append(signedHeaders); append('\n')
            append(payloadHash)
        }

        // ---- 3. 构造 StringToSign ----
        val credentialScope = "$shortDate/$region/$service/request"
        val canonicalRequestHash = sha256Hex(canonicalRequest)
        val stringToSign = "$ALGORITHM\n$xDate\n$credentialScope\n$canonicalRequestHash"

        // ---- 4. 派生 SigningKey ----
        val signingKey = deriveSigningKey(sk, shortDate, region, service)

        // ---- 5. 计算签名 ----
        val signature = hmacSha256Hex(signingKey, stringToSign)

        // ---- 6. 构造 Authorization ----
        val authorization = "$ALGORITHM Credential=$ak/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val resultHeaders = mapOf(
            "Host" to host,
            "X-Date" to xDate,
            "Authorization" to authorization,
            "Content-Type" to "application/json"
        )

        return SignedResult(headers = resultHeaders, xDate = xDate)
    }

    /**
     * 派生签名密钥链：
     *   kSecret  = bytes(SK)
     *   kDate    = HMAC-SHA256(kSecret, ShortDate)
     *   kRegion  = HMAC-SHA256(kDate, Region)
     *   kService = HMAC-SHA256(kRegion, Service)
     *   kSigning = HMAC-SHA256(kService, "request")
     */
    private fun deriveSigningKey(sk: String, shortDate: String, region: String, service: String): ByteArray {
        val kSecret = sk.toByteArray(Charsets.UTF_8)
        val kDate = hmacSha256(kSecret, shortDate)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        return hmacSha256(kService, "request")
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(key, HMAC_SHA256))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        return hmacSha256(key, data).joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance(SHA_256)
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

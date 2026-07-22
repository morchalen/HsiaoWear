package com.example.hsiaowear.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID

object ImageUtils {

    fun copyImageToPrivateDir(context: Context, uri: Uri): String? {
        return try {
            val fileName = "clothing_${UUID.randomUUID()}.jpg"
            val destFile = File(context.filesDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteImageFile(path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 将本地图片文件压缩并转成 Base64 字符串。
     * @param filePath 本地文件路径
     * @param maxWidth 最大宽度，超过则缩放（默认 1024）
     * @param quality  压缩质量 0-100（默认 80）
     */
    fun fileToBase64(filePath: String, maxWidth: Int = 1024, quality: Int = 80): String? {
        return try {
            val original = BitmapFactory.decodeFile(filePath) ?: return null
            // 等比缩放
            val scaled = if (original.width > maxWidth || original.height > maxWidth) {
                val scale = maxWidth.toFloat() / maxOf(original.width, original.height)
                Bitmap.createScaledBitmap(original,
                    (original.width * scale).toInt(),
                    (original.height * scale).toInt(), true)
            } else original

            ByteArrayOutputStream().use { baos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                val bytes = baos.toByteArray()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }.also {
                // 如果缩放了，回收原图
                if (scaled !== original) scaled.recycle()
                original.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从 URL 下载图片并保存到应用私有目录。
     * @return 保存后的本地文件路径，失败返回 null
     */
    fun downloadImage(context: Context, url: String, prefix: String = "matted"): String? {
        return try {
            val fileName = "${prefix}_${UUID.randomUUID()}.png"
            val destFile = File(context.filesDir, fileName)

            URL(url).openStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 将 Bitmap 保存为本地文件。
     * @return 本地文件路径
     */
    fun saveBitmap(context: Context, bitmap: Bitmap, prefix: String = "img"): String {
        val fileName = "${prefix}_${UUID.randomUUID()}.png"
        val destFile = File(context.filesDir, fileName)
        FileOutputStream(destFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return destFile.absolutePath
    }
}

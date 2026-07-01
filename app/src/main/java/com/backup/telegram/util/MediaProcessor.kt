package com.backup.telegram.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * مسؤول عن:
 * 1) نسخ محتوى content:// Uri إلى ملف مؤقت في cache (مطلوب لأن OkHttp يحتاج File للرفع).
 * 2) ضغط الصور (JPEG quality) لتقليل استهلاك البيانات قبل الرفع، اختيارياً.
 *
 * ملاحظة: لا نضغط الفيديو هنا (يتطلب MediaCodec/FFmpeg وتعقيداً إضافياً)؛
 * يُكتفى برفعه كما هو ضمن حدود حجم Telegram.
 */
object MediaProcessor {

    /**
     * ينسخ الملف من content Uri إلى الـ cache directory مع تطبيق ضغط
     * اختياري على الصور. يُعيد الملف الناتج الجاهز للرفع.
     */
    fun prepareFileForUpload(
        context: Context,
        uri: Uri,
        mimeType: String,
        displayName: String,
        compress: Boolean,
        jpegQuality: Int
    ): File? {
        val cacheDir = File(context.cacheDir, "upload_tmp").apply { mkdirs() }
        val outFile = File(cacheDir, sanitizeFileName(displayName))

        return try {
            if (compress && mimeType.startsWith("image/") && mimeType != "image/gif") {
                compressImage(context, uri, outFile, jpegQuality) ?: copyRaw(context, uri, outFile)
            } else {
                copyRaw(context, uri, outFile)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun copyRaw(context: Context, uri: Uri, outFile: File): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile
        } catch (e: Exception) {
            null
        }
    }

    private fun compressImage(context: Context, uri: Uri, outFile: File, quality: Int): File? {
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null

            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), fos)
            }
            bitmap.recycle()
            outFile
        } catch (e: Exception) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String {
        // إضافة timestamp لتفادي تعارض الأسماء في مجلد الـ cache المؤقت
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "${System.currentTimeMillis()}_$safe"
    }

    fun cleanupTempFile(file: File) {
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }
    }
}

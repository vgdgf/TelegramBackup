package com.backup.telegram.data.remote

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * نتيجة عملية الرفع، تفرّق بين:
 * - نجاح
 * - فشل قابل لإعادة المحاولة (مشكلة شبكة، 5xx)
 * - فشل غير قابل لإعادة المحاولة (الملف كبير جداً، توكن خاطئ -> 401/403)
 */
sealed class UploadResult {
    object Success : UploadResult()
    data class RetryableFailure(val message: String) : UploadResult()
    data class PermanentFailure(val message: String) : UploadResult()
}

class TelegramRepository(private val context: Context) {

    // حدود Telegram Bot API الرسمية
    companion object {
        const val MAX_PHOTO_SIZE = 10 * 1024 * 1024L   // ~10MB لرفع كصورة (موصى به)
        const val MAX_FILE_SIZE = 50 * 1024 * 1024L    // 50MB حد الـ Bot API العام
    }

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.telegram.org/") // base وهمي لأننا نستخدم @Url كامل
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(TelegramApiService::class.java)

    /**
     * يرفع ملف وسائط (صورة أو فيديو) إلى Telegram.
     * يحدد تلقائياً sendPhoto / sendVideo / sendDocument حسب نوع ونوع وحجم الملف.
     */
    suspend fun uploadMedia(
        botToken: String,
        chatId: String,
        file: File,
        mimeType: String
    ): UploadResult {
        if (!file.exists()) {
            return UploadResult.PermanentFailure("الملف غير موجود محلياً")
        }
        if (file.length() > MAX_FILE_SIZE) {
            return UploadResult.PermanentFailure("حجم الملف يتجاوز الحد المسموح من Telegram (50MB)")
        }

        return try {
            val chatIdPart = chatId.toRequestBody("text/plain".toMediaTypeOrNull())
            val mediaBody: RequestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())

            val response = when {
                mimeType.startsWith("image/") && file.length() <= MAX_PHOTO_SIZE -> {
                    val part = MultipartBody.Part.createFormData("photo", file.name, mediaBody)
                    api.sendPhoto(
                        "https://api.telegram.org/bot$botToken/sendPhoto",
                        chatIdPart, part
                    )
                }
                mimeType.startsWith("video/") -> {
                    val part = MultipartBody.Part.createFormData("video", file.name, mediaBody)
                    api.sendVideo(
                        "https://api.telegram.org/bot$botToken/sendVideo",
                        chatIdPart, part
                    )
                }
                else -> {
                    // أي ملف آخر أو صورة كبيرة نرسلها كـ document للحفاظ على الجودة الأصلية
                    val part = MultipartBody.Part.createFormData("document", file.name, mediaBody)
                    api.sendDocument(
                        "https://api.telegram.org/bot$botToken/sendDocument",
                        chatIdPart, part
                    )
                }
            }

            when {
                response.isSuccessful -> UploadResult.Success
                response.code() in intArrayOf(401, 403, 400) -> {
                    UploadResult.PermanentFailure("خطأ من Telegram: ${response.code()} - تحقق من التوكن أو Chat ID")
                }
                else -> UploadResult.RetryableFailure("خطأ مؤقت من الخادم: ${response.code()}")
            }
        } catch (e: java.io.IOException) {
            // مشاكل الشبكة (انقطاع، timeout) -> قابلة لإعادة المحاولة
            UploadResult.RetryableFailure("خطأ في الشبكة: ${e.message}")
        } catch (e: Exception) {
            UploadResult.PermanentFailure("خطأ غير متوقع: ${e.message}")
        }
    }

    fun guessMimeType(uri: Uri): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}

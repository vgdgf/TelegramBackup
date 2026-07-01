package com.backup.telegram.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * يمثل كل سجل ملف وسائط (صورة أو فيديو) تم اكتشافه على الجهاز.
 * نستخدم contentUri كمعرف فريد لتفادي رفع نفس الملف أكثر من مرة (deduplication).
 */
@Entity(tableName = "media_files")
data class MediaFileEntity(
    @PrimaryKey
    val contentUri: String,       // Uri الخاص بالملف في MediaStore (فريد)
    val displayName: String,
    val filePath: String?,        // قد يكون null على بعض الأجهزة الحديثة
    val mimeType: String,         // image/* أو video/*
    val sizeBytes: Long,
    val dateAdded: Long,          // وقت إضافة الملف للجهاز (من MediaStore)
    val fileHash: String?,        // SHA-256 hash للملف لمنع التكرار حتى لو تغير الـ Uri
    val status: UploadStatus = UploadStatus.PENDING,
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val uploadedAt: Long? = null,
    val errorMessage: String? = null
)

enum class UploadStatus {
    PENDING,     // بانتظار الرفع
    UPLOADING,   // قيد الرفع حالياً
    UPLOADED,    // تم الرفع بنجاح
    FAILED,      // فشل بعد استنفاد المحاولات
    SKIPPED      // تم تجاهله (مثلاً حجم أكبر من حد Telegram)
}

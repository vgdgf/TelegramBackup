package com.backup.telegram.data.repository

import android.content.Context
import android.net.Uri
import com.backup.telegram.data.local.AppDatabase
import com.backup.telegram.data.local.entity.MediaFileEntity
import com.backup.telegram.data.local.entity.UploadStatus
import com.backup.telegram.data.remote.TelegramRepository
import com.backup.telegram.data.remote.UploadResult
import com.backup.telegram.util.FileHashUtil
import com.backup.telegram.util.MediaProcessor
import com.backup.telegram.util.MediaScanner
import com.backup.telegram.util.SecurePrefsManager
import kotlinx.coroutines.flow.Flow

/**
 * نقطة الدخول الوحيدة (Single Source of Truth) لمنطق النسخ الاحتياطي.
 * تُخفي تفاصيل DB / Network / FileSystem عن الطبقات الأعلى (Service, Worker, ViewModel).
 */
class BackupRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.mediaFileDao()
    private val scanner = MediaScanner(context)
    private val telegramRepository = TelegramRepository(context)
    private val prefs = SecurePrefsManager(context)

    fun observeAllFiles(): Flow<List<MediaFileEntity>> = dao.observeAll()
    fun observeRemainingCount(): Flow<Int> = dao.countRemaining()
    fun observeUploadedCount(): Flow<Int> = dao.countByStatus(UploadStatus.UPLOADED)

    /**
     * مسح شامل أولي + إدراج كل الملفات الجديدة غير الموجودة في DB.
     */
    suspend fun runFullScanAndQueue() {
        val files = scanner.performFullScan()
        dao.insertAll(files) // onConflict = IGNORE يتولى منع التكرار
    }

    fun startObservingNewMedia(onNew: (MediaFileEntity) -> Unit) {
        scanner.startObserving(object : MediaScanner.Listener {
            override fun onNewMediaDetected(entity: MediaFileEntity) {
                onNew(entity)
            }
        })
    }

    fun stopObservingNewMedia() = scanner.stopObserving()

    suspend fun enqueueIfNew(entity: MediaFileEntity) {
        if (dao.exists(entity.contentUri) == 0) {
            dao.insert(entity)
        }
    }

    suspend fun resetStuckUploads() = dao.resetStuckUploads()

    /**
     * يأخذ دفعة من الملفات المعلّقة ويحاول رفعها واحداً تلو الآخر (queue حقيقي).
     * يُعيد عدد الملفات التي تمت معالجتها بنجاح.
     */
    suspend fun processNextBatch(batchSize: Int = 5): Int {
        if (!prefs.isConfigured()) return 0

        val batch = dao.getNextPendingBatch(limit = batchSize)
        var successCount = 0

        for (item in batch) {
            val success = uploadSingleFile(item)
            if (success) successCount++
        }
        return successCount
    }

    private suspend fun uploadSingleFile(item: MediaFileEntity): Boolean {
        dao.update(item.copy(status = UploadStatus.UPLOADING, lastAttemptAt = System.currentTimeMillis()))

        val uri = Uri.parse(item.contentUri)

        // منع التكرار عبر hash: إن وُجد ملف آخر بنفس المحتوى تم رفعه مسبقاً، نتخطى الرفع الفعلي
        val hash = FileHashUtil.computeSha256(context, uri)
        if (hash != null) {
            val duplicate = dao.getByHash(hash)
            if (duplicate != null && duplicate.status == UploadStatus.UPLOADED && duplicate.contentUri != item.contentUri) {
                dao.update(
                    item.copy(
                        status = UploadStatus.SKIPPED,
                        fileHash = hash,
                        errorMessage = "ملف مكرر، تم رفعه مسبقاً بمسار آخر"
                    )
                )
                return true
            }
        }

        val preparedFile = MediaProcessor.prepareFileForUpload(
            context = context,
            uri = uri,
            mimeType = item.mimeType,
            displayName = item.displayName,
            compress = prefs.compressImages,
            jpegQuality = prefs.jpegQuality
        )

        if (preparedFile == null) {
            dao.update(
                item.copy(
                    status = UploadStatus.FAILED,
                    retryCount = item.retryCount + 1,
                    errorMessage = "تعذر قراءة الملف من الجهاز"
                )
            )
            return false
        }

        val botToken = prefs.botToken!!
        val chatId = prefs.chatId!!

        val result = telegramRepository.uploadMedia(botToken, chatId, preparedFile, item.mimeType)
        MediaProcessor.cleanupTempFile(preparedFile)

        return when (result) {
            is UploadResult.Success -> {
                dao.update(
                    item.copy(
                        status = UploadStatus.UPLOADED,
                        fileHash = hash,
                        uploadedAt = System.currentTimeMillis(),
                        errorMessage = null
                    )
                )
                true
            }
            is UploadResult.RetryableFailure -> {
                dao.update(
                    item.copy(
                        status = UploadStatus.FAILED,
                        retryCount = item.retryCount + 1,
                        errorMessage = result.message
                    )
                )
                false
            }
            is UploadResult.PermanentFailure -> {
                dao.update(
                    item.copy(
                        status = UploadStatus.SKIPPED,
                        retryCount = item.retryCount + 1,
                        errorMessage = result.message
                    )
                )
                false
            }
        }
    }
}

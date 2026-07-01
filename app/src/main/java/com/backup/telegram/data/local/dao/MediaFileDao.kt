package com.backup.telegram.data.local.dao

import androidx.room.*
import com.backup.telegram.data.local.entity.MediaFileEntity
import com.backup.telegram.data.local.entity.UploadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaFileDao {

    /**
     * إدراج ملف جديد. إذا كان موجوداً مسبقاً (نفس contentUri) يتم تجاهله
     * (IGNORE) حتى لا تُكرر السجلات أو يُعاد ضبط حالته.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MediaFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<MediaFileEntity>)

    @Update
    suspend fun update(entity: MediaFileEntity)

    @Query("SELECT * FROM media_files WHERE contentUri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE fileHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): MediaFileEntity?

    @Query("SELECT COUNT(*) FROM media_files WHERE contentUri = :uri")
    suspend fun exists(uri: String): Int

    /**
     * يجلب الدفعة التالية من الملفات الجاهزة للرفع، مرتبة حسب الأقدم أولاً.
     * نستثني FAILED التي تجاوزت الحد الأقصى من المحاولات.
     */
    @Query(
        """
        SELECT * FROM media_files 
        WHERE status = :pending OR (status = :failed AND retryCount < :maxRetries)
        ORDER BY dateAdded ASC
        LIMIT :limit
        """
    )
    suspend fun getNextPendingBatch(
        limit: Int = 5,
        pending: UploadStatus = UploadStatus.PENDING,
        failed: UploadStatus = UploadStatus.FAILED,
        maxRetries: Int = 5
    ): List<MediaFileEntity>

    @Query("SELECT COUNT(*) FROM media_files WHERE status = :status")
    fun countByStatus(status: UploadStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_files WHERE status = :pending OR status = :uploading")
    fun countRemaining(
        pending: UploadStatus = UploadStatus.PENDING,
        uploading: UploadStatus = UploadStatus.UPLOADING
    ): Flow<Int>

    @Query("SELECT * FROM media_files ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<MediaFileEntity>>

    @Query("UPDATE media_files SET status = :status WHERE status = :from")
    suspend fun resetStuckUploads(status: UploadStatus = UploadStatus.PENDING, from: UploadStatus = UploadStatus.UPLOADING)
}

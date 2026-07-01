package com.backup.telegram.util

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.backup.telegram.data.local.entity.MediaFileEntity
import com.backup.telegram.data.local.entity.UploadStatus

/**
 * مسؤول عن:
 * 1) مسح المكتبة كاملة مرة واحدة (initial scan) لإيجاد الملفات الموجودة مسبقاً.
 * 2) الاستماع إلى تغييرات MediaStore (ملفات جديدة) عبر ContentObserver.
 *
 * يعتمد بالكامل على MediaStore API العام، دون أي وصول لنظام الملفات الخام،
 * وهو ما يتوافق مع متطلبات Google Play الخاصة بـ Scoped Storage.
 */
class MediaScanner(private val context: Context) {

    interface Listener {
        fun onNewMediaDetected(entity: MediaFileEntity)
    }

    private var imagesObserver: ContentObserver? = null
    private var videoObserver: ContentObserver? = null

    fun startObserving(listener: Listener) {
        val handler = Handler(Looper.getMainLooper())

        imagesObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let { scanSingleUri(it, listener) }
            }
        }
        videoObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let { scanSingleUri(it, listener) }
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, imagesObserver!!
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, videoObserver!!
        )
    }

    fun stopObserving() {
        imagesObserver?.let { context.contentResolver.unregisterContentObserver(it) }
        videoObserver?.let { context.contentResolver.unregisterContentObserver(it) }
        imagesObserver = null
        videoObserver = null
    }

    /**
     * مسح شامل أولي لكل الصور والفيديوهات الموجودة فعلياً على الجهاز.
     * يُستدعى مرة واحدة عند أول تفعيل للنسخ الاحتياطي، أو يدوياً من المستخدم.
     */
    fun performFullScan(): List<MediaFileEntity> {
        val results = mutableListOf<MediaFileEntity>()
        results.addAll(queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false))
        results.addAll(queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true))
        return results
    }

    private fun scanSingleUri(uri: Uri, listener: Listener) {
        val id = try {
            ContentUris.parseId(uri)
        } catch (e: Exception) {
            return // قد يكون تغيير غير متعلق بعنصر محدد (مثلاً حذف جماعي)
        }

        val isVideo = uri.toString().contains("video", ignoreCase = true)
        val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                      else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val itemUri = ContentUris.withAppendedId(baseUri, id)

        val entity = queryMediaById(itemUri, isVideo)
        entity?.let { listener.onNewMediaDetected(it) }
    }

    private fun queryMediaById(uri: Uri, isVideo: Boolean): MediaFileEntity? {
        val projection = buildProjection()
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursorToEntity(cursor, uri, isVideo)
            }
        }
        return null
    }

    private fun queryMedia(collection: Uri, isVideo: Boolean): List<MediaFileEntity> {
        val list = mutableListOf<MediaFileEntity>()
        val projection = buildProjection()
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} ASC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val itemUri = ContentUris.withAppendedId(collection, id)
                cursorToEntity(cursor, itemUri, isVideo)?.let { list.add(it) }
            }
        }
        return list
    }

    private fun buildProjection() = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.DATA, // قد تكون deprecated لكنها لا تزال متاحة للقراءة في أغلب الحالات
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_ADDED
    )

    private fun cursorToEntity(cursor: android.database.Cursor, uri: Uri, isVideo: Boolean): MediaFileEntity? {
        return try {
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            MediaFileEntity(
                contentUri = uri.toString(),
                displayName = cursor.getString(nameIdx) ?: "unknown",
                filePath = if (pathIdx >= 0) cursor.getString(pathIdx) else null,
                mimeType = cursor.getString(mimeIdx) ?: if (isVideo) "video/*" else "image/*",
                sizeBytes = cursor.getLong(sizeIdx),
                dateAdded = cursor.getLong(dateIdx) * 1000L, // MediaStore يخزنها بالثواني
                fileHash = null, // يُحسب لاحقاً عند الرفع لتفادي تكلفة I/O أثناء المسح
                status = UploadStatus.PENDING
            )
        } catch (e: Exception) {
            null
        }
    }
}

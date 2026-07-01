package com.backup.telegram.util

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

object FileHashUtil {

    /**
     * يحسب SHA-256 hash لمحتوى الملف عبر InputStream (يعمل مع content:// URIs بدون
     * الحاجة لمسار ملف خام، وهو متوافق مع Scoped Storage).
     */
    fun computeSha256(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}

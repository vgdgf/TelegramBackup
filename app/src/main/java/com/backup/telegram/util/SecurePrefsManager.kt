package com.backup.telegram.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * يخزن البيانات الحساسة (Bot Token, Chat ID) باستخدام EncryptedSharedPreferences
 * المبني على Android Keystore، بدلاً من تخزينها كنص عادي.
 */
class SecurePrefsManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_backup_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var botToken: String?
        get() = prefs.getString(KEY_BOT_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_BOT_TOKEN, value).apply()

    var chatId: String?
        get() = prefs.getString(KEY_CHAT_ID, null)
        set(value) = prefs.edit().putString(KEY_CHAT_ID, value).apply()

    var isBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKUP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKUP_ENABLED, value).apply()

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var compressImages: Boolean
        get() = prefs.getBoolean(KEY_COMPRESS_IMAGES, true)
        set(value) = prefs.edit().putBoolean(KEY_COMPRESS_IMAGES, value).apply()

    var jpegQuality: Int
        get() = prefs.getInt(KEY_JPEG_QUALITY, 85)
        set(value) = prefs.edit().putInt(KEY_JPEG_QUALITY, value).apply()

    fun isConfigured(): Boolean = !botToken.isNullOrBlank() && !chatId.isNullOrBlank()

    companion object {
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_BACKUP_ENABLED = "backup_enabled"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_COMPRESS_IMAGES = "compress_images"
        private const val KEY_JPEG_QUALITY = "jpeg_quality"
    }
}

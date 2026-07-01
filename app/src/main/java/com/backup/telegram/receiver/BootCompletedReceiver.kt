package com.backup.telegram.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.backup.telegram.service.BackupForegroundService
import com.backup.telegram.util.SecurePrefsManager
import com.backup.telegram.worker.UploadWorker

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = SecurePrefsManager(context)
            if (prefs.isBackupEnabled && prefs.isConfigured()) {
                BackupForegroundService.start(context)
                UploadWorker.schedule(context)
            }
        }
    }
}

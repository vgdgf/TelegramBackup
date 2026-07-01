package com.backup.telegram.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.backup.telegram.data.local.AppDatabase
import com.backup.telegram.data.local.entity.MediaFileEntity
import com.backup.telegram.data.local.entity.UploadStatus
import com.backup.telegram.service.BackupForegroundService
import com.backup.telegram.util.SecurePrefsManager
import com.backup.telegram.worker.UploadWorker

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).mediaFileDao()
    private val prefs = SecurePrefsManager(application)

    val uploadedCount: LiveData<Int>             = dao.countByStatus(UploadStatus.UPLOADED).asLiveData()
    val remainingCount: LiveData<Int>            = dao.countRemaining().asLiveData()
    val failedCount: LiveData<Int>               = dao.countByStatus(UploadStatus.FAILED).asLiveData()
    val allFiles: LiveData<List<MediaFileEntity>> = dao.observeAll().asLiveData()

    fun isBackupEnabled() = prefs.isBackupEnabled
    fun isConfigured()    = prefs.isConfigured()

    fun toggleBackup(enabled: Boolean) {
        prefs.isBackupEnabled = enabled
        val ctx = getApplication<Application>().applicationContext
        if (enabled && prefs.isConfigured()) {
            BackupForegroundService.start(ctx)
            UploadWorker.schedule(ctx)
        } else {
            BackupForegroundService.stop(ctx)
            UploadWorker.cancel(ctx)
        }
    }
}

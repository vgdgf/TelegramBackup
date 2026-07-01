package com.backup.telegram.worker

import android.content.Context
import androidx.work.*
import com.backup.telegram.data.repository.BackupRepository
import com.backup.telegram.util.SecurePrefsManager
import java.util.concurrent.TimeUnit

/**
 * بعض مصنعي الأجهزة (Xiaomi, Huawei, Samsung..) يقتلون الـ Foreground Service
 * بقوة لتوفير البطارية رغم الإشعار الدائم. WorkManager يوفر شبكة أمان:
 * مهمة دورية تتأكد من استمرار معالجة قائمة الانتظار وتعيد تشغيل الخدمة عند الحاجة.
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = SecurePrefsManager(applicationContext)
        if (!prefs.isBackupEnabled || !prefs.isConfigured()) {
            return Result.success()
        }

        // يعيد تشغيل الخدمة الأساسية إن كانت متوقفة (idempotent إن كانت تعمل أصلاً)
        com.backup.telegram.service.BackupForegroundService.start(applicationContext)

        val repository = BackupRepository(applicationContext)
        val processed = repository.processNextBatch(batchSize = 5)

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "periodic_upload_safety_net"

        fun schedule(context: Context) {
            val prefs = SecurePrefsManager(context)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()

            val request = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

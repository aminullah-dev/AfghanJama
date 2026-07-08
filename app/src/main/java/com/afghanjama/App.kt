package com.afghanjama

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.afghanjama.work.AutoBackupWorker
import com.afghanjama.work.LowStockWorker
import com.afghanjama.work.WageReminderWorker
import java.util.concurrent.TimeUnit

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // یادآوری هفتگی تسویه کارمزد خیاط‌ها
        val request = PeriodicWorkRequestBuilder<WageReminderWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WageReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        // بکاپ خودکار روزانه از دیتابیس
        val backupRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(6, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AutoBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )

        // اعلان روزانهٔ کمبود موجودی انبار
        val lowStockRequest = PeriodicWorkRequestBuilder<LowStockWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(3, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            LowStockWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            lowStockRequest
        )
    }
}

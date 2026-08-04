package com.afghanjama

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.afghanjama.data.buildAppDatabase
import com.afghanjama.data.repo.Repo
import com.afghanjama.prefs.SalePrefs
import com.afghanjama.prefs.settings
import com.afghanjama.util.CrashLog
import com.afghanjama.util.PhotoStore
import com.afghanjama.work.AutoBackupWorker
import com.afghanjama.work.BreakReminderWorker
import com.afghanjama.work.LowStockWorker
import com.afghanjama.work.WageReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // **اولین کار، پیش از هر چیزِ دیگر.** هر خطایی که از این خط به
        // بعد بیفتد — در همین متد، در اکتیویتی، یا در هر کوروتینِ
        // پس‌زمینه — روی دیسک ثبت می‌شود و دفعهٔ بعد نشان داده می‌شود.
        // اگر بالاتر می‌آمد، خرابیِ خودِ راه‌اندازی از قلم می‌افتاد.
        CrashLog.install(this)

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

        // یادآورهای نان و چای: هر بار که اپ باز می‌شود از نو چیده می‌شوند،
        // تا خاموش‌بودنِ گوشی یا ری‌استارت چیزی را از بین نبرد.
        // سیاستِ فروش پیش از هر کارِ دیگری خوانده می‌شود، چون `Repo` به
        // Context دسترسی ندارد و از نسخهٔ حافظه‌ایِ همین می‌خواند.
        SalePrefs.allowNegativeStock(settings)

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { BreakReminderWorker.rescheduleAll(this@App) }
            // عکس‌های بی‌صاحب را جارو می‌کنیم: سفارشِ حذف‌شده، عکسِ نیمه‌کاره،
            // یا بازیابیِ پشتیبانی که سطرش را نداشته. یک جا برای همهٔ حالت‌ها.
            runCatching {
                val db = buildAppDatabase(this@App)
                try {
                    val live = Repo(db).livePhotoFileNames()
                    PhotoStore.dir(this@App).listFiles()?.forEach {
                        if (it.name !in live) it.delete()
                    }
                } finally {
                    db.close()
                }
            }
        }
    }
}

package com.afghanjama

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.afghanjama.data.AppDb
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.AndroidLanHost
import com.afghanjama.lan.LanHub
import com.afghanjama.prefs.CompanyPrefs
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

        // «کارگاهِ وصل»: کلیدِ اتصالِ این گوشی فقط در Keystore پیچیده می‌ماند،
        // و نامی که گوشی سرِ جفت شدن به کامپیوتر می‌گوید.
        com.afghanjama.lan.HubSecrets.store = com.afghanjama.lan.AndroidKeystoreSecretStore(this)
        com.afghanjama.lan.PhoneName.current = android.os.Build.MODEL ?: "گوشی"
        com.afghanjama.lan.PhoneName.platform = "android"

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

        // اشتراکِ کارگاه — **یک بار در هر پروسه**، نه هر بار که اکتیویتی
        // ساخته می‌شود. اگر این گوشیِ اصلی است، همین که لایسنس خوانده شد
        // (`LicenceHeartbeat`ِ `MainActivity`) و گوشی روی شبکهٔ کارگاه بود،
        // خودش روشن می‌شود؛ پیش از آن «نوشتن مجاز است» پیش‌فرض است و نباید
        // درخواستی بنشیند. پروسه‌ای که فقط برای یک کارِ پس‌زمینه بالا آمده
        // و اکتیویتی ندارد، لایسنس نمی‌خوانَد و سرور هم روشن نمی‌کند.
        LanHub.shared.autoStart(settings, AndroidLanHost(this))

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { BreakReminderWorker.rescheduleAll(this@App) }
            // عکس‌های بی‌صاحب را جارو می‌کنیم: سفارشِ حذف‌شده، عکسِ نیمه‌کاره،
            // یا بازیابیِ پشتیبانی که سطرش را نداشته. یک جا برای همهٔ حالت‌ها.
            runCatching {
                // همان دفترِ مشترکِ پروسه، نه یک نمونهٔ جدا: نوشتنِ تعمیرِ
                // پایین باید صفحه‌های بازِ همین پروسه را هم تازه کند.
                val repo = Repo(AppDb.get(this@App))
                PhotoStore.sweep(this@App, keptPhotoNames(repo))

                // تعمیرِ یک‌بارهٔ طبقه‌بندیِ نقدِ دستی.
                //
                // بی پرچم و بی شرط صدا زده می‌شود چون خودش
                // بی‌خطر در برابرِ اجرای دوباره است: بعد از یک
                // بار هیچ سطری برای جابه‌جایی نمی‌ماند و دو
                // پرس‌وجوی شمارش هزینه‌ای ندارد. پرچمی که
                // بتواند از دفتر جدا بیفتد، خودش یک اشکالِ تازه
                // است.
                repo.repairManualCashClassification()
            }
        }
    }

    /**
     * نامِ هر عکسی که **صاحب دارد** — یعنی نباید جارو شود.
     *
     * **چرا این تابع جدا شد و چرا فهرستش دو تکه است:**
     *
     * لوگوی کارگاه در `SharedPreferences` می‌ماند، نه در دیتابیس. جاروی
     * راه‌اندازی فقط `livePhotoFileNames()` را می‌پرسید که از جدول‌ها
     * می‌آید، پس لوگو در آن نبود و **هر بار که اپ باز می‌شد پاک
     * می‌شد**. از دیدِ کاربر: لوگو انتخاب می‌شد، دیده هم می‌شد، و دفعهٔ
     * بعد نبود — «عکس آپلود نمی‌شود».
     *
     * پس هر جای تازه‌ای که نامِ عکسی را بیرون از دیتابیس نگه دارد، باید
     * همین‌جا اضافه شود. `photosweep` این را نگه می‌دارد.
     */
    private suspend fun keptPhotoNames(repo: Repo): Set<String> =
        repo.livePhotoFileNames() +
            setOfNotNull(CompanyPrefs.logo(settings).takeIf { it.isNotBlank() })
}

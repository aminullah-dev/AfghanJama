// app/src/main/java/com/afghanjama/MainActivity.kt
package com.afghanjama

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.afghanjama.data.buildAppDatabase
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.AndroidLanHost
import com.afghanjama.prefs.AndroidSettings
import com.afghanjama.platform.AndroidDocs
import com.afghanjama.platform.AndroidFileExport
import com.afghanjama.platform.AndroidScreenBehavior
import com.afghanjama.platform.AndroidSystemActions
import com.afghanjama.platform.LocalDocs
import com.afghanjama.platform.LocalFileExport
import com.afghanjama.platform.LocalScreenBehavior
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.ui.platform.AndroidWidgets
import com.afghanjama.ui.platform.LocalWidgets
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.nav.AppNav
import com.afghanjama.ui.vm.VmFactory
import com.afghanjama.ui.screens.CrashReportScreen
import com.afghanjama.ui.screens.PinLockScreen
import com.afghanjama.util.AppLock
import com.afghanjama.util.CrashLog
import com.afghanjama.ui.theme.KhayatYarTheme
import com.afghanjama.prefs.settings

private const val REQ_NOTIFICATIONS = 1001

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // مجوز نوتیفیکیشن برای یادآوری تسویه هفتگی (اندروید ۱۳+).
        //
        // عمداً از ActivityResultContracts استفاده نمی‌شود: این اکتیویتی
        // یک FragmentActivity است و نسخهٔ fragment ای که biometric می‌آورد
        // فقط requestCodeهای ۱۶ بیتی را می‌پذیرد، در حالی که
        // ActivityResultRegistry کدِ بزرگ‌تر می‌سازد. مسیرِ قدیمی با کدِ
        // کوچک امن است. (تا امروز پنهان مانده بود چون اگر مجوز از قبل
        // داده شده باشد اصلاً درخواستی ارسال نمی‌شود.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS
            )
        }

        val db = buildAppDatabase(applicationContext)
        val repo = Repo(db)

        // تنظیمات یک بار ساخته می‌شود و از اینجا به کلِ درخت می‌رود.
        // صفحه‌هایی که به `:core` رفته‌اند به‌جای `LocalContext` این را
        // می‌گیرند — همان یک خط بود که نگهشان می‌داشت در `:app`.
        val settings = AndroidSettings(applicationContext)
        val system = AndroidSystemActions(applicationContext)
        val docs = AndroidDocs(applicationContext)

        setContent {
          CompositionLocalProvider(
            LocalSettings provides settings,
            LocalSystemActions provides system,
            LocalDocs provides docs,
            LocalFileExport provides AndroidFileExport,
            LocalScreenBehavior provides AndroidScreenBehavior,
            // بی این خط، هر صفحه‌ای که پنجرهٔ تأیید یا منوی بازشو
            // دارد سرِ باز شدن می‌شکند.
            LocalWidgets provides AndroidWidgets
          ) {
            KhayatYarTheme {
                /*
                 * گزارشِ خرابیِ بارِ قبل — پیش از هر چیزِ دیگر.
                 *
                 * عمداً بالاتر از قفلِ اپ و از کلِ درختِ صفحه‌هاست: اگر
                 * خرابی در ساختِ همان درخت باشد، هر جای دیگری بگذاریمش
                 * پیش از دیده شدن دوباره می‌ترکد و کاربر هیچ‌وقت متن را
                 * نمی‌بیند.
                 *
                 * ۳۸ ViewModelِ پایین‌تر هم تا وقتی این صفحه سرِ پاست
                 * ساخته نمی‌شوند، چون `return@KhayatYarTheme` جلوی
                 * رسیدن به آن‌ها را می‌گیرد. یعنی اپی که سرِ باز شدن
                 * می‌ترکید، دستِ‌کم یک بار باز می‌شود و می‌گوید چرا.
                 */
                var crash by remember { mutableStateOf(CrashLog.pending(applicationContext)) }
                val report = crash
                if (report != null) {
                    CrashReportScreen(
                        report = report,
                        onSend = { system.shareText("گزارشِ خرابیِ خیاط‌یار", report) },
                        onContinue = {
                            CrashLog.clear(applicationContext)
                            crash = null
                        }
                    )
                    return@KhayatYarTheme
                }

                // قفل اپ: اگر رمز تنظیم شده باشد، اول باید باز شود
                var unlocked by remember { mutableStateOf(!AppLock.isPinSet(applicationContext)) }
                if (!unlocked) {
                    PinLockScreen(onUnlock = { unlocked = true })
                    return@KhayatYarTheme
                }

                /*
                 * ViewModelها دیگر اینجا ساخته نمی‌شوند.
                 *
                 * تا دیروز هر ۳۷ تا در همین نقطه ساخته می‌شدند و هرکدام
                 * سرِ ساخته شدن جریان‌هایی باز می‌کرد که کلِ جدول را
                 * می‌خوانند — ۹ تای‌شان جدولِ سفارش‌ها را. یعنی در ثانیهٔ
                 * اولِ اجرا همان داده تا ۹ بار در حافظه می‌نشست، برای
                 * صفحه‌هایی که شاید هرگز باز نشوند.
                 *
                 * حالا هرکدام داخلِ مقصدِ خودش ساخته می‌شود. طولِ عمر
                 * عوض نشده — صاحبشان همین اکتیویتی است — فقط زمانِ ساخت.
                 */
                val vmFactory = remember {
                    VmFactory(repo, settings, AndroidLanHost(applicationContext))
                }

                AppNav(vmFactory)
            }
          }
        }
    }
}

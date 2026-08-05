package com.afghanjama.desktop.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.afghanjama.data.DB_NAME
import com.afghanjama.data.DESKTOP_BASELINE
import com.afghanjama.prefs.Settings
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * جایی که دفترِ کارگاه روی ویندوز نگهداری می‌شود.
 *
 * کنارِ خودِ برنامه گذاشته نمی‌شود: پوشهٔ `Program Files` روی ویندوز
 * فقط-خواندنی است و به‌روزرسانیِ برنامه هم می‌تواند رویش بنویسد. جای
 * درست، پوشهٔ دادهٔ کاربر است.
 *
 * روی لینوکس (رانرِ CI و توسعه) `APPDATA` وجود ندارد، پس به خانهٔ کاربر
 * برمی‌گردد. همین باعث می‌شود این کد همه‌جا اجرا شود نه فقط ویندوز.
 */
fun dataDir(): File {
    val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("user.home")
    return File(base, "KhayatYar").apply { mkdirs() }
}

fun databaseFile(): File = File(dataDir(), DB_NAME)

/**
 * تنظیماتِ ویندوز — کنارِ دیتابیس، نه در جای دیگری.
 *
 * پشتیبان‌گیری از کارگاه یعنی کپیِ همین یک پوشه. اگر تنظیمات جدا
 * می‌افتاد، پشتیبان نامِ کارگاه و رمزِ اتصالش را دربر نمی‌گرفت و کسی هم
 * نمی‌فهمید تا روزِ بازگردانی.
 */
fun desktopSettings(): Settings = FileSettings(dataDir())

/**
 * نسخهٔ یک فایلِ دفتر، بی باز کردنِ Room.
 *
 * لازم است چون Room برای فایلی که نمی‌تواند بالا ببرد پیامی می‌دهد که
 * به دردِ کارگاه نمی‌خورد («A migration from 55 to 61 was required but
 * not found»). با دانستنِ نسخه پیش از باز کردن، می‌شود گفت **چه باید
 * کرد**، نه فقط اینکه چه نشد.
 *
 * `user_version` همان عددی است که Room نگه می‌دارد (روی دفترِ واقعی
 * وارسی شد: ۶۱). فایلِ نساخته یا خالی صفر می‌دهد.
 *
 * خواندنِ نسخه هرگز نباید باز شدنِ دفتر را بشکند: اگر فایل قفل بود یا
 * خراب، `null` برمی‌گردد و تصمیم به خودِ Room واگذار می‌شود.
 */
fun ledgerFileVersion(file: File): Int? {
    if (!file.exists() || file.length() == 0L) return null
    return runCatching {
        val conn = BundledSQLiteDriver().open(file.absolutePath)
        try {
            val stmt = conn.prepare("PRAGMA user_version")
            try {
                if (stmt.step()) stmt.getLong(0).toInt() else null
            } finally {
                stmt.close()
            }
        } finally {
            conn.close()
        }
    }.getOrNull()
}

/**
 * باز کردنِ دفتر.
 *
 * `BundledSQLiteDriver` کتابخانهٔ بومیِ SQLite را با خودش می‌آورد، پس
 * کارگاه لازم نیست چیزی روی ویندوز نصب کند — همان چیزی که برای برنامهٔ
 * یک کارگاهِ خیاطی لازم است.
 *
 * **مهاجرت حالا هست.** تا دیروز نبود و این یک مینِ واقعی بود: دیتابیس
 * مستقیم روی `DB_VERSION` ساخته می‌شد و هیچ گامی برای رفتن از نسخه‌ای
 * به نسخهٔ بعد وجود نداشت. یعنی اولین باری که `DB_VERSION` جلو می‌رفت،
 * **هر دفترِ ویندوزیِ موجود باز نمی‌شد** و کارگاه می‌خوابید.
 *
 * گام‌ها از `:core` می‌آیند (`SCHEMA_STEPS`) و همان‌هایی‌اند که اندروید
 * هم اجرا می‌کند — یک تعریف، دو پوستهٔ نازک.
 *
 * **هیچ `fallbackToDestructiveMigration`ای اینجا نیست**، برخلافِ
 * اندروید. خاموش پاک کردنِ دفترِ کارگاه بدترین رفتارِ ممکن است؛ بهتر
 * است باز نشود و خطا بدهد تا کسی خبردار شود.
 */
fun openDatabase(file: File = databaseFile()): DesktopDatabase {
    /*
     * فایلِ قدیمی‌تر از پایهٔ ویندوز، پیش از هر کاری رد می‌شود.
     *
     * چنین فایلی فقط از گوشی می‌آید (نسخهٔ ویندوز هرگز زیرِ این عدد
     * منتشر نشده). گوشی ۴۲ مهاجرتِ خودش را دارد و دو تای‌شان با کرسر و
     * حسابِ زمانِ اجرا کار می‌کنند — یعنی روی JVM بازنویسی‌شدنی نیستند
     * مگر با یک پیاده‌سازیِ دومِ همان منطق، که همان دامِ همیشگی است.
     *
     * پس راهِ درست این است و صریح گفته می‌شود: **اول روی گوشی
     * به‌روزرسانی، بعد انتقال.** داده دست نمی‌خورد.
     */
    val existing = ledgerFileVersion(file)
    if (existing != null && existing in 1 until DESKTOP_BASELINE) {
        throw IllegalStateException(
            "این فایلِ دفتر نسخهٔ $existing است و نسخهٔ ویندوز از " +
                "$DESKTOP_BASELINE به بعد را باز می‌کند.\n" +
                "اول اپِ گوشی را به‌روز کنید تا خودش دفتر را بالا ببرد، " +
                "بعد پشتیبان را به پی‌سی بیاورید.\n" +
                "هیچ داده‌ای دست نخورد."
        )
    }

    return Room.databaseBuilder<DesktopDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*desktopMigrations())
        .build()
}

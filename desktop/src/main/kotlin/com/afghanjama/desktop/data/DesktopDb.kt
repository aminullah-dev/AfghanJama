package com.afghanjama.desktop.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.afghanjama.data.DB_NAME
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
 * باز کردنِ دفتر.
 *
 * `BundledSQLiteDriver` کتابخانهٔ بومیِ SQLite را با خودش می‌آورد، پس
 * کارگاه لازم نیست چیزی روی ویندوز نصب کند — همان چیزی که برای برنامهٔ
 * یک کارگاهِ خیاطی لازم است.
 *
 * **مهاجرت اینجا نیست، و این عمدی است.** روی اندروید ۴۲ مهاجرت هست که
 * دادهٔ واقعی را از نسخه‌ای به نسخهٔ بعد می‌برد. اینجا دیتابیس تازه
 * ساخته می‌شود و مستقیم روی `DB_VERSION` می‌نشیند. اولین باری که اسکیما
 * عوض شود، همین‌جا هم باید مهاجرت اضافه شود — وگرنه فایلِ قدیمیِ روی
 * پی‌سی باز نمی‌شود. **هیچ `fallbackToDestructiveMigration`ای اینجا
 * نیست**، چون خاموش پاک کردنِ دفترِ کارگاه بدترین رفتارِ ممکن است؛
 * بهتر است باز نشود و خطا بدهد تا کسی خبردار شود.
 */
fun openDatabase(file: File = databaseFile()): DesktopDatabase =
    Room.databaseBuilder<DesktopDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

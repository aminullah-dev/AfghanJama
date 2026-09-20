package com.afghanjama.ios.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * جای فایلِ دفتر روی آیفون.
 *
 * `NSDocumentDirectory` عمدی است و نه `Caches` یا `tmp`: سیستم آن دو را
 * هر وقت جا کم بیاورد پاک می‌کند، و دفترِ کارگاه چیزی نیست که بشود
 * دوباره ساختش. اینجا هم در پشتیبانِ iCloud/iTunes می‌آید، که برای
 * کارگاهی که یک گوشی دارد همان چیزی است که می‌خواهد.
 */
@OptIn(ExperimentalForeignApi::class)
fun databaseFilePath(): String {
    val dir: NSURL = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("پوشهٔ اسنادِ برنامه در دسترس نیست")
    return requireNotNull(dir.URLByAppendingPathComponent("afghanjama.db")?.path) {
        "مسیرِ فایلِ دفتر ساخته نشد"
    }
}

/**
 * دفترِ کارگاه را باز می‌کند.
 *
 * **مهاجرت اینجا نیست و این آگاهانه است.** نسخهٔ آیفون تازه است و
 * دیتابیسش از صفر روی [com.afghanjama.data.DB_VERSION] ساخته می‌شود.
 * اولین باری که اسکیما عوض شود، مثلِ ویندوز اینجا هم باید مهاجرت
 * اضافه شود.
 *
 * **و `fallbackToDestructiveMigration` عمداً نیست** — همان تصمیمی که
 * `:desktop` گرفت. خاموش پاک کردنِ دفترِ کارگاه بدترین رفتارِ ممکن است؛
 * بهتر است باز نشود و خطا بدهد تا کسی خبردار شود.
 *
 * `BundledSQLiteDriver` کتابخانهٔ بومیِ SQLite را با خودش می‌آورد، پس به
 * نسخه‌ای که iOS دارد وابسته نیستیم و رفتارِ دفتر با ویندوز یکی می‌مانَد.
 */
fun openDatabase(path: String = databaseFilePath()): IosDatabase =
    Room.databaseBuilder<IosDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        // روی Kotlin/Native `Dispatchers.IO` تا coroutines 1.8 وجود
        // ندارد؛ `Default` همان کارِ استخرِ نخ را می‌کند.
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()

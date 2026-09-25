package com.afghanjama.ios.data

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.afghanjama.data.SCHEMA_STEPS
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
 * **مهاجرت حالا هست.** تا نسخهٔ ۶۵ لازم نبود — نسخهٔ آیفون از صفر روی
 * [com.afghanjama.data.DB_VERSION] ساخته می‌شد. ۶۵→۶۶ (نشانیِ مشتری)
 * اولین تغییرِ اسکیما پس از آمدنِ آیفون است؛ بی مهاجرت، دفترِ آیفونی که
 * روی ۶۵ ساخته شده بود دیگر باز نمی‌شد. همان گام‌های مشترکِ
 * `SCHEMA_STEPS` که اندروید و ویندوز اجرا می‌کنند — Room از نسخهٔ فایل
 * جلو می‌رود و گام‌های پیش از آن را نادیده می‌گیرد.
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
        .addMigrations(*iosMigrations())
        .build()

/** گام‌های مشترکِ اسکیما، به شکلی که Roomِ چندسکویی می‌فهمد. */
private fun iosMigrations(): Array<Migration> = SCHEMA_STEPS.map { step ->
    object : Migration(step.from, step.to) {
        override fun migrate(connection: SQLiteConnection) {
            step.sql.forEach { connection.execSQL(it) }
        }
    }
}.toTypedArray()

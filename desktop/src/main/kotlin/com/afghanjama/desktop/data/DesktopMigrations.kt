package com.afghanjama.desktop.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.afghanjama.data.DESKTOP_STEPS
import com.afghanjama.data.SchemaStep

/**
 * گام‌های مشترکِ اسکیما، به شکلی که Roomِ ویندوز می‌فهمد.
 *
 * **چرا این فایل هست و در `:core` نیست.** نامِ کلاس روی هر دو سکو یکی
 * است (`androidx.room.migration.Migration`) ولی امضای متدش نه:
 *
 *     اندروید:  migrate(db: SupportSQLiteDatabase)
 *     ویندوز:   migrate(connection: SQLiteConnection)
 *
 * پس خودِ `Migration` مشترک‌شدنی نیست. آنچه مشترک است **دستورهای SQL**
 * است — و آن‌ها در `:core` نشسته‌اند. اینجا فقط یک پوستهٔ نازک است که
 * همان دستورها را با API این سکو اجرا می‌کند.
 *
 * `execSQL` یک تابعِ الحاقی در `androidx.sqlite` است، نه متدِ خودِ
 * `SQLiteConnection` (آن فقط `prepare` و `close` دارد). این با `javap`
 * روی خودِ jar وارسی شد نه از حافظه — همان درسی که سه رفت‌وبرگشتِ CI
 * دادِ گامِ ۲ داد.
 */
internal fun desktopMigrations(
    // تاریخی (استخراج‌شده از `Migrations.kt`) + آینده (مشترک با اندروید).
    steps: List<SchemaStep> = DESKTOP_STEPS
): Array<Migration> = (
    steps.map { step ->
        object : Migration(step.from, step.to) {
            override fun migrate(connection: SQLiteConnection) {
                /*
                 * `{now}` تنها نشانه‌ای است که در SQLِ مشترک پذیرفته
                 * می‌شود. سه مهاجرتِ تاریخی مهرِ زمان می‌خواهند و بی این
                 * نشانه ناچار بودند دستی نوشته شوند — یعنی ده دستورِ SQL
                 * دو نسخه‌ای می‌شد، برای یک عدد.
                 *
                 * **یک بار برای کلِ گام** حساب می‌شود، نه برای هر دستور:
                 * روی اندروید هم `val now` یک بار گرفته می‌شود و همهٔ
                 * دستورهای آن مهاجرت همان عدد را می‌بینند.
                 */
                val now = System.currentTimeMillis().toString()
                step.sql.forEach { connection.execSQL(it.replace("{now}", now)) }
            }
        }
    } + HAND_WRITTEN
    ).toTypedArray()

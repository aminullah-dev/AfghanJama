// app/src/main/java/com/afghanjama/data/SharedMigrations.kt
package com.afghanjama.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * گام‌های مشترکِ اسکیما (`SCHEMA_STEPS` در `:core`)، به شکلی که Roomِ
 * اندروید می‌فهمد.
 *
 * **`Migrations.kt` دست نخورد و نباید بخورد.** آن ۴۲ مهاجرت دادهٔ واقعیِ
 * کارگاه را از نسخهٔ ۱۹ تا ۶۱ بالا می‌آورند و روی گوشیِ کارفرما
 * اجرا شده‌اند. بازنویسی‌شان هیچ سودی ندارد و همه‌چیز را به خطر
 * می‌اندازد.
 *
 * این فایل فقط **آینده** را یکی می‌کند: از ۶۱ به بعد هر گام یک بار در
 * `:core` نوشته می‌شود و هر دو سکو همان را اجرا می‌کنند. تا دیروز
 * مهاجرت فقط اندرویدی بود و ویندوز هیچ نداشت — یعنی دو سکو که یکی‌شان
 * اصلاً راهِ بالا رفتن نداشت.
 *
 * امضای `migrate` اینجا `SupportSQLiteDatabase` است و روی ویندوز
 * `SQLiteConnection`؛ برای همین خودِ `Migration` مشترک نمی‌شود و فقط
 * SQL مشترک است.
 */
fun sharedMigrations(): Array<Migration> = SCHEMA_STEPS.map { step ->
    object : Migration(step.from, step.to) {
        override fun migrate(db: SupportSQLiteDatabase) {
            step.sql.forEach { db.execSQL(it) }
        }
    }
}.toTypedArray()

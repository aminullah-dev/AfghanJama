// app/src/main/java/com/afghanjama/data/Migrations.kt
package com.afghanjama.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * از این نسخه به بعد، دیتابیس با Migration واقعی ارتقا می‌یابد و
 * داده‌های کارگاه هنگام آپدیت اپ پاک نمی‌شود.
 * (نسخه‌های قدیمی‌تر از ۱۹ همچنان destructive هستند.)
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ستون‌های جدید سفارش
        db.execSQL("ALTER TABLE orders ADD COLUMN fabricSource TEXT NOT NULL DEFAULT 'NEW'")
        db.execSQL("ALTER TABLE orders ADD COLUMN agreedPrice INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE orders ADD COLUMN stageChangedAt INTEGER NOT NULL DEFAULT 0")
        // برای سفارش‌های موجود، زمان مرحله = زمان ایجاد
        db.execSQL("UPDATE orders SET stageChangedAt = createdAt WHERE stageChangedAt = 0")

        // دسته هزینه روی تراکنش‌ها
        db.execSQL("ALTER TABLE finance_transactions ADD COLUMN category TEXT NOT NULL DEFAULT ''")

        // جدول موجودی پارچه
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `fabric_stock` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`fabricType` TEXT NOT NULL, " +
                "`fabricColor` TEXT NOT NULL, " +
                "`fabricUnit` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`minLevel` REAL NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_fabric_stock_fabricType_fabricColor_fabricUnit` " +
                "ON `fabric_stock` (`fabricType`, `fabricColor`, `fabricUnit`)"
        )
    }
}

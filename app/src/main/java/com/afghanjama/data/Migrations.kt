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

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // قیمت میانگین خرید پارچه (بهای تمام‌شده)
        db.execSQL("ALTER TABLE fabric_stock ADD COLUMN avgPrice REAL NOT NULL DEFAULT 0")

        // تاریخچه مراحل سفارش
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `order_stage_logs` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`orderId` TEXT NOT NULL, " +
                "`orderCode` TEXT NOT NULL, " +
                "`fromStatus` TEXT NOT NULL, " +
                "`toStatus` TEXT NOT NULL, " +
                "`at` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_order_stage_logs_orderId` " +
                "ON `order_stage_logs` (`orderId`)"
        )
    }
}

/**
 * چند پارچه در هر سفارش + تحویل بخشی از سفارش به چند خیاط.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // چند پارچه در هر سفارش
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `order_fabrics` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`orderId` TEXT NOT NULL, " +
                "`fabricType` TEXT NOT NULL, " +
                "`fabricColor` TEXT NOT NULL, " +
                "`fabricUnit` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`price` INTEGER NOT NULL, " +
                "`source` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_order_fabrics_orderId` " +
                "ON `order_fabrics` (`orderId`)"
        )

        // تحویل بخشی از سفارش به خیاط
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sewing_assignments` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`orderId` TEXT NOT NULL, " +
                "`orderCode` TEXT NOT NULL, " +
                "`tailorLabel` TEXT NOT NULL, " +
                "`qty` INTEGER NOT NULL, " +
                "`unitWage` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`doneAt` INTEGER)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sewing_assignments_orderId` " +
                "ON `sewing_assignments` (`orderId`)"
        )

        // کارمزد خیاط: ستون assignmentId + حذف قید یکتای orderId
        db.execSQL("ALTER TABLE tailor_wages ADD COLUMN assignmentId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DROP INDEX IF EXISTS `index_tailor_wages_orderId`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tailor_wages_orderId` " +
                "ON `tailor_wages` (`orderId`)"
        )
    }
}

/**
 * چند خرج کار در هر سفارش (دکمه، لایی چسب، نوار زیبایی، ...).
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `order_work_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`orderId` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`price` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_order_work_items_orderId` " +
                "ON `order_work_items` (`orderId`)"
        )
    }
}

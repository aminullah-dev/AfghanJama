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

/**
 * انبار عمومی مواد خام + فاکتورهای خرید آزاد (چند قلم دلخواه).
 * افزوده‌شده و بدون دست‌زدن به جدول‌های قبلی؛ داده‌ها حفظ می‌شوند.
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // انبار عمومی مواد
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `material_stock` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`avgPrice` REAL NOT NULL DEFAULT 0, " +
                "`minLevel` REAL NOT NULL DEFAULT 0, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_material_stock_name_unit` " +
                "ON `material_stock` (`name`, `unit`)"
        )

        // سرِ فاکتور خرید
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `purchase_invoices` (" +
                "`id` TEXT PRIMARY KEY NOT NULL, " +
                "`code` TEXT NOT NULL, " +
                "`supplier` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`total` INTEGER NOT NULL, " +
                "`paySource` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )

        // اقلام فاکتور خرید
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `purchase_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`invoiceId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`qty` REAL NOT NULL, " +
                "`unitPrice` INTEGER NOT NULL, " +
                "`total` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_purchase_items_invoiceId` " +
                "ON `purchase_items` (`invoiceId`)"
        )
    }
}

/**
 * انبار محصول نهایی + سابقهٔ فروش‌های جزئی. افزوده‌شده و بدون تغییر
 * جدول‌های قبلی؛ داده‌ها حفظ می‌شوند.
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `finished_stock` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`size` TEXT NOT NULL, " +
                "`qty` INTEGER NOT NULL, " +
                "`avgCost` INTEGER NOT NULL DEFAULT 0, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_finished_stock_name_size` " +
                "ON `finished_stock` (`name`, `size`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `finished_sales` (" +
                "`id` TEXT PRIMARY KEY NOT NULL, " +
                "`code` TEXT NOT NULL, " +
                "`productName` TEXT NOT NULL, " +
                "`size` TEXT NOT NULL, " +
                "`qty` INTEGER NOT NULL, " +
                "`unitPrice` INTEGER NOT NULL, " +
                "`total` INTEGER NOT NULL, " +
                "`cost` INTEGER NOT NULL, " +
                "`customerName` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
    }
}

/**
 * ادغام کامل «انبار پارچه» در «انبار مواد»: هر ردیف پارچه با نام
 * «نوع رنگ» و همان واحد به انبار عمومی منتقل می‌شود (با میانگین وزنی
 * قیمت در صورت برخورد) و سپس جدول پارچه حذف می‌گردد.
 *
 * بدون upsert نوشته شده تا در اندروید ۷ (SQLite قدیمی) هم کار کند.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ۱) تجمیع پارچه‌ها بر اساس نام «نوع رنگ» + واحد
        db.execSQL(
            "CREATE TEMP TABLE IF NOT EXISTS `_fab` AS " +
                "SELECT TRIM(fabricType || ' ' || fabricColor) AS name, fabricUnit AS unit, " +
                "SUM(amount) AS amount, " +
                "CASE WHEN SUM(amount) > 0 THEN SUM(amount * avgPrice) / SUM(amount) ELSE 0 END AS avgPrice, " +
                "MAX(minLevel) AS minLevel, MAX(updatedAt) AS updatedAt " +
                "FROM fabric_stock GROUP BY TRIM(fabricType || ' ' || fabricColor), fabricUnit"
        )

        // ۲) اقلامی که در انبار مواد وجود دارند: میانگین وزنی (مقدار قدیمی مبناست)
        db.execSQL(
            "UPDATE material_stock SET " +
                "avgPrice = CASE WHEN (amount + (SELECT f.amount FROM _fab f WHERE f.name = material_stock.name AND f.unit = material_stock.unit)) > 0 " +
                "THEN (amount * avgPrice + (SELECT f.amount * f.avgPrice FROM _fab f WHERE f.name = material_stock.name AND f.unit = material_stock.unit)) " +
                "/ (amount + (SELECT f.amount FROM _fab f WHERE f.name = material_stock.name AND f.unit = material_stock.unit)) ELSE 0 END, " +
                "amount = amount + (SELECT f.amount FROM _fab f WHERE f.name = material_stock.name AND f.unit = material_stock.unit), " +
                "updatedAt = (SELECT f.updatedAt FROM _fab f WHERE f.name = material_stock.name AND f.unit = material_stock.unit) " +
                "WHERE EXISTS (SELECT 1 FROM _fab f WHERE f.name = material_stock.name AND f.unit = material_stock.unit)"
        )

        // ۳) اقلام جدید (بدون برخورد) درج می‌شوند
        db.execSQL(
            "INSERT INTO material_stock (name, unit, amount, avgPrice, minLevel, updatedAt) " +
                "SELECT f.name, f.unit, f.amount, f.avgPrice, f.minLevel, f.updatedAt FROM _fab f " +
                "WHERE NOT EXISTS (SELECT 1 FROM material_stock m WHERE m.name = f.name AND m.unit = f.unit)"
        )

        db.execSQL("DROP TABLE IF EXISTS `_fab`")
        db.execSQL("DROP TABLE IF EXISTS `fabric_stock`")
    }
}

/**
 * دفتر حساب فروشنده برای خرید نسیه (قرض) و تسویهٔ آن.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `supplier_ledger` (" +
                "`id` TEXT PRIMARY KEY NOT NULL, " +
                "`supplier` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_supplier_ledger_supplier` " +
                "ON `supplier_ledger` (`supplier`)"
        )
    }
}

/**
 * اندازه‌های بدنِ مشتری (پروندهٔ خیاطی).
 */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `customer_measurements` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`customerId` INTEGER NOT NULL, " +
                "`label` TEXT NOT NULL, " +
                "`value` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_customer_measurements_customerId` " +
                "ON `customer_measurements` (`customerId`)"
        )
    }
}

/**
 * کاردکس/گردش انبار مواد با دلیل هر تغییر موجودی.
 */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stock_movements` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`delta` REAL NOT NULL, " +
                "`reason` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_movements_name` " +
                "ON `stock_movements` (`name`)"
        )
    }
}

/**
 * رکورد واقعیِ مرحلهٔ برش (مسئول، تعداد دست، ضایعات).
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cutting_records` (" +
                "`id` TEXT PRIMARY KEY NOT NULL, " +
                "`orderId` TEXT NOT NULL, " +
                "`orderCode` TEXT NOT NULL, " +
                "`cutter` TEXT NOT NULL, " +
                "`pieces` INTEGER NOT NULL, " +
                "`waste` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cutting_records_orderId` " +
                "ON `cutting_records` (`orderId`)"
        )
    }
}

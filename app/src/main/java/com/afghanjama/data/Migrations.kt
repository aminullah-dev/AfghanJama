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

/**
 * رکورد کنترل کیفیت (نظارت): نتیجه، مشکل، ناظر و تاریخ.
 */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `qc_records` (" +
                "`id` TEXT PRIMARY KEY NOT NULL, " +
                "`orderId` TEXT NOT NULL, " +
                "`orderCode` TEXT NOT NULL, " +
                "`inspector` TEXT NOT NULL, " +
                "`result` TEXT NOT NULL, " +
                "`problem` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_qc_records_orderId` " +
                "ON `qc_records` (`orderId`)"
        )
    }
}

/**
 * کیفیت کارِ خیاط روی هر تحویلِ دوخت.
 */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sewing_assignments ADD COLUMN quality TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * دستمزد دوخت به‌عنوان بخشی از بهای تمام‌شدهٔ سفارش (اصلاح محاسبهٔ سود).
 */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN sewingCost INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * حضور و غیاب کارمند (ورود/خروج).
 */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `attendance` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`employee` TEXT NOT NULL, " +
                "`checkIn` INTEGER NOT NULL, " +
                "`checkOut` INTEGER, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_attendance_employee` " +
                "ON `attendance` (`employee`)"
        )
    }
}

/**
 * کسرِ موادِ سفارش به مرحلهٔ «برش» منتقل می‌شود (نه هنگام ثبت سفارش).
 * پرچم materialsConsumed مشخص می‌کند مواد واقعاً کسر شده یا نه.
 * سفارش‌های موجود در مدل قدیم قبلاً هنگام ثبت مواد را کسر کرده‌اند،
 * پس همه را «کسرشده=۱» علامت می‌زنیم تا حذفشان مواد را درست برگرداند.
 */
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN materialsConsumed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE orders SET materialsConsumed = 1")
    }
}

/**
 * دفتر کلِ یکپارچه (parties + ledger_entries). این مهاجرت فقط جدول‌های
 * جدید می‌سازد و داده‌های سه سیستمِ فعلی (قرض فروشنده، پرداخت مشتری،
 * کارمزد خیاط) را به دفتر کل کپی می‌کند. جدول‌های قدیمی دست‌نخورده
 * می‌مانند تا فیچرهای فعلی نشکنند؛ دفتر کل یک لایهٔ واحدِ خواندنی است.
 */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `parties` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`phone` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_parties_type_name` " +
                "ON `parties` (`type`, `name`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`partyType` TEXT NOT NULL, `partyName` TEXT NOT NULL, " +
                "`debit` INTEGER NOT NULL, `credit` INTEGER NOT NULL, " +
                "`refType` TEXT NOT NULL, `refId` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, `at` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_partyType_partyName` " +
                "ON `ledger_entries` (`partyType`, `partyName`)"
        )

        // فروشنده: CREDIT(نسیه)→بستانکار، PAYMENT(تسویه)→بدهکار
        db.execSQL(
            "INSERT INTO ledger_entries (partyType, partyName, debit, credit, refType, refId, note, at) " +
                "SELECT 'SUPPLIER', supplier, " +
                "CASE WHEN type='PAYMENT' THEN amount ELSE 0 END, " +
                "CASE WHEN type='CREDIT' THEN amount ELSE 0 END, " +
                "CASE WHEN type='PAYMENT' THEN 'SUPPLIER_PAYMENT' ELSE 'PURCHASE_CREDIT' END, " +
                "'', note, createdAt FROM supplier_ledger"
        )
        // مشتری: پرداختِ مشتری → بستانکار (پول نزد ما)
        db.execSQL(
            "INSERT INTO ledger_entries (partyType, partyName, debit, credit, refType, refId, note, at) " +
                "SELECT 'CUSTOMER', " +
                "CASE WHEN customerName IS NULL OR customerName='' THEN 'مشتری نامشخص' ELSE customerName END, " +
                "0, amount, 'CUSTOMER_' || source, orderId, note, createdAt FROM customer_payments"
        )
        // خیاط: کارمزد → بستانکار
        db.execSQL(
            "INSERT INTO ledger_entries (partyType, partyName, debit, credit, refType, refId, note, at) " +
                "SELECT 'TAILOR', tailorLabel, 0, amount, 'WAGE', orderCode, '', createdAt FROM tailor_wages"
        )
        // کارمزدِ تسویه‌شده → یک بدهکارِ جبرانی تا مانده صفر شود
        db.execSQL(
            "INSERT INTO ledger_entries (partyType, partyName, debit, credit, refType, refId, note, at) " +
                "SELECT 'TAILOR', tailorLabel, amount, 0, 'WAGE_PAID', orderCode, 'تسویه‌شده', " +
                "COALESCE(settledAt, createdAt) FROM tailor_wages WHERE settled = 1"
        )

        // ثبت طرف‌ها در دفترچه از روی گردش‌های واردشده
        db.execSQL(
            "INSERT OR IGNORE INTO parties (name, type, phone, note, createdAt) " +
                "SELECT DISTINCT partyName, partyType, '', '', $now FROM ledger_entries"
        )
    }
}

/**
 * اسنادِ مالی با شمارهٔ یکتا (documents). فقط جدول ساخته می‌شود؛ اسناد
 * از این پس هنگام هر رویدادِ مالی (خرید، فروش، تسویه، دریافت) تولید
 * می‌شوند. داده‌های موجود دست‌نخورده می‌مانند.
 */
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `documents` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`number` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`partyName` TEXT NOT NULL, `amount` INTEGER NOT NULL, " +
                "`refId` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                "`at` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_documents_number` " +
                "ON `documents` (`number`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_documents_type` " +
                "ON `documents` (`type`)"
        )
    }
}

/**
 * تکمیلِ دفتر کل برای حساب مشتری: بدهیِ هر مشتری بابتِ سفارش‌های موجود
 * (قیمت توافقی) به‌عنوان «بدهکار» ثبت می‌شود تا ماندهٔ مشتری در دفتر کل
 * برابرِ «مجموع سفارش‌ها − پرداخت‌ها» شود. فقط درج می‌کند؛ چیزی حذف نمی‌شود.
 */
val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO ledger_entries (partyType, partyName, debit, credit, refType, refId, note, at) " +
                "SELECT 'CUSTOMER', customerName, agreedPrice, 0, 'SALE_BILLING', orderCode, 'بدهی بابت سفارش', createdAt " +
                "FROM orders WHERE customerName IS NOT NULL AND customerName != '' AND agreedPrice > 0"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO parties (name, type, phone, note, createdAt) " +
                "SELECT DISTINCT customerName, 'CUSTOMER', '', '', $now FROM orders " +
                "WHERE customerName IS NOT NULL AND customerName != '' AND agreedPrice > 0"
        )
    }
}

/**
 * ترمیمِ دفتر کل:
 * ۱) پرداخت‌های قدیمیِ بی‌نام که با orderId به سفارش وصل بودند، در backfill
 *    زیر «مشتری نامشخص» رفته بودند — به نامِ واقعیِ مشتریِ سفارش برمی‌گردند.
 * ۲) برگشتی‌های فروش (مبلغ منفی) که به‌صورت بستانکارِ منفی وارد شده بودند،
 *    به بدهکارِ مثبت تبدیل می‌شوند (نمایش و قرارداد دفتر درست شود).
 * فقط UPDATE؛ چیزی حذف نمی‌شود.
 */
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL(
            "UPDATE ledger_entries SET partyName = " +
                "(SELECT o.customerName FROM orders o WHERE o.id = ledger_entries.refId) " +
                "WHERE partyType = 'CUSTOMER' AND partyName = 'مشتری نامشخص' " +
                "AND EXISTS (SELECT 1 FROM orders o WHERE o.id = ledger_entries.refId " +
                "AND o.customerName IS NOT NULL AND o.customerName != '')"
        )
        db.execSQL(
            "UPDATE ledger_entries SET debit = -credit, credit = 0 WHERE credit < 0"
        )
        db.execSQL(
            "UPDATE ledger_entries SET credit = -debit, debit = 0 WHERE debit < 0"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO parties (name, type, phone, note, createdAt) " +
                "SELECT DISTINCT partyName, partyType, '', '', $now FROM ledger_entries"
        )
    }
}

/**
 * کد اختصاصیِ هر طرح (مثل D-003). برای طرح‌های موجود از id ساخته می‌شود؛
 * طرح‌های جدید هنگام ثبت، خودکار کد می‌گیرند.
 */
val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE design_items ADD COLUMN code TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE design_items SET code = printf('D-%03d', id) WHERE code = ''")
    }
}

/**
 * کارکنانِ کارگاه با هر سمتی (آشپز، حسابدار، مدیر، …) برای حضور و غیاب
 * و حسابِ کارکنان در دفتر کل.
 */
val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `staff` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `role` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_staff_name` ON `staff` (`name`)"
        )
    }
}

/**
 * تخلیهٔ مرحلهٔ قدیمیِ «فروش سفارش»: هر سفارشی که هنوز در وضعیت SALES
 * مانده، با همان منطقِ تحویل به انبار محصول (میانگین وزنی بهای تمام‌شده)
 * وارد finished_stock می‌شود، بایگانی (STORED) می‌گردد و بدهیِ
 * ازپیش‌ثبت‌شدهٔ مشتری‌اش خنثی می‌شود — چون از این نسخه، فروش فقط از
 * انبار محصول انجام می‌شود و صفحهٔ «فروش سفارش» حذف شده است.
 */
/**
 * حسابداری دوطرفه: جدول‌های ژورنال + سندِ افتتاحیه از وضعیتِ فعلیِ کارگاه
 * (نقد، بانک، انبارها، دریافتنی/پرداختنی) تا دفترها از روز اول تراز باشند؛
 * مابه‌التفاوت به حساب «سرمایه» می‌نشیند.
 */
/**
 * لاگِ حسابرسی: «چه کسی، چه کاری، کِی» روی عملیاتِ حساس.
 */
/**
 * مهلتِ تحویلِ سفارش. فقط یک ستونِ افزودنی با مقدارِ پیش‌فرضِ ۰
 * (یعنی «مهلتی تعیین نشده») — سفارش‌های موجود دست نمی‌خورند.
 */
val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `dueDate` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * برگشت از فروش: چند عدد از هر فروشِ ثبت‌شده مرجوع شده است.
 * فقط یک ستونِ افزودنی با پیش‌فرضِ ۰ (یعنی «هیچ برگشتی نداشته») —
 * فروش‌های موجود دست نمی‌خورند.
 */
val MIGRATION_46_47 = object : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `finished_sales` ADD COLUMN `returnedQty` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * انتسابِ برگشتِ نظارت به خیاط. رکوردهای قدیمی خالی می‌مانند و رفتارشان
 * دقیقاً مثل قبل است، پس هیچ کارنامه‌ای با این به‌روزرسانی تغییر نمی‌کند.
 */
val MIGRATION_47_48 = object : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `qc_records` ADD COLUMN `tailor` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * دستهٔ طرح — پوشهٔ انبارِ محصول. فقط یک ستونِ افزودنی با پیش‌فرضِ خالی،
 * پس طرح‌های ثبت‌شده دست نمی‌خورند و انبار همان‌طور که بود کار می‌کند
 * (همه زیرِ «دسته‌بندی‌نشده» تا کاربر خودش دسته بدهد).
 */
/**
 * تعدادِ داخلِ بسته روی قلمِ خرید.
 *
 * دکمه و زیپ و نخ بیشتر بسته‌ای خریده می‌شوند ولی عددی مصرف می‌شوند.
 * فاکتور «۵ بسته» را نگه می‌دارد و انبار «۵۰۰ عدد» می‌شود.
 *
 * ستونِ افزودنی با پیش‌فرضِ ۰ (یعنی بسته‌بندی نیست)، پس خریدهای ثبت‌شده
 * دست نمی‌خورند.
 */
val MIGRATION_57_58 = object : Migration(57, 58) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `purchase_items` ADD COLUMN `perPack` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_56_57 = object : Migration(56, 57) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `design_items` ADD COLUMN `category` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * برچسب‌زدنِ انتقال‌های داخلیِ **قبلی** — هیچ ستون یا جدولی عوض نمی‌شود،
 * فقط `category` سطرهایی پر می‌شود که از قبل خالی مانده بود.
 *
 * چرا لازم است: انتقالِ سودِ هر فروش به صندوقِ فایده یک جفت سطرِ IN/OUT
 * می‌نویسد و گزارشِ «جریان نقد» آن را پولِ واقعی می‌شمرد — سودِ کارگاه در
 * فهرستِ هزینه‌ها زیرِ «سایر» ظاهر می‌شد. کدِ تازه برچسب می‌زند، ولی
 * ماه‌های گذشته بی آن می‌ماندند.
 *
 * فقط یادداشت‌هایی که **خودِ کد** ساخته تطبیق داده می‌شوند، پس حدس‌زنی
 * نیست. انتقالِ دستیِ بینِ صندوق‌ها عمداً بیرون است: یادداشتش را کاربر
 * می‌نویسد و هیچ الگوی قابلِ اعتمادی ندارد.
 *
 * `category = ''` در شرط، مهاجرت را idempotent می‌کند و سطری که کاربر
 * خودش دسته‌بندی کرده دست نمی‌خورد.
 */
val MIGRATION_55_56 = object : Migration(55, 56) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE `finance_transactions` SET `category` = 'انتقال داخلی' " +
                "WHERE `category` = '' AND (" +
                "`note` LIKE 'انتقال سود فروش%' OR " +
                "`note` LIKE 'سود فروش %' OR " +
                "`note` LIKE 'برگشت سود فروش%')"
        )
    }
}

/**
 * تخفیفِ ردیفِ فاکتور و عکسِ کالای انبار. هر دو فقط ستونِ افزودنی با
 * پیش‌فرضِ بی‌اثر، پس فروش‌ها و موجودی‌های ثبت‌شده دست نمی‌خورند.
 */
val MIGRATION_54_55 = object : Migration(54, 55) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `finished_sales` ADD COLUMN `discount` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `finished_stock` ADD COLUMN `photoFile` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * عکس‌های سفارش (طرح، پارچه، نمونهٔ مشتری). فقط یک جدولِ تازه است.
 */
val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `order_photos` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`orderId` TEXT NOT NULL, " +
                "`orderCode` TEXT NOT NULL, " +
                "`fileName` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL DEFAULT '', " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_photos_orderId` ON `order_photos` (`orderId`)")
    }
}

/** تحویلِ تکه‌تکه: چند عدد از سفارش تا حالا رفته. */
val MIGRATION_52_53 = object : Migration(52, 53) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `deliveredQty` INTEGER NOT NULL DEFAULT 0")
        // سفارش‌هایی که قبلاً «تحویل شد» گرفته‌اند، کاملاً تحویل شده‌اند
        db.execSQL("UPDATE orders SET deliveredQty = qty WHERE status = 'SENT'")
    }
}

/**
 * صندوقِ درخواست‌های گوشی‌های کارگران. فقط یک جدولِ تازه است.
 */
val MIGRATION_51_52 = object : Migration(51, 52) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_requests` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`deviceName` TEXT NOT NULL, " +
                "`worker` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`summary` TEXT NOT NULL, " +
                "`refId` INTEGER NOT NULL DEFAULT 0, " +
                "`amount` INTEGER NOT NULL DEFAULT 0, " +
                "`note` TEXT NOT NULL DEFAULT '', " +
                "`status` TEXT NOT NULL DEFAULT 'PENDING', " +
                "`createdAt` INTEGER NOT NULL, " +
                "`decidedAt` INTEGER, " +
                "`decidedNote` TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_requests_status` ON `sync_requests` (`status`)")
    }
}

/**
 * وقت‌های نان و چای. فقط یک جدولِ تازه است — هیچ جدولِ موجودی دست نمی‌خورد.
 */
val MIGRATION_50_51 = object : Migration(50, 51) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `break_times` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`hour` INTEGER NOT NULL, " +
                "`minute` INTEGER NOT NULL, " +
                "`enabled` INTEGER NOT NULL DEFAULT 1, " +
                "`createdAt` INTEGER NOT NULL)"
        )
    }
}

/**
 * ارزشِ دقیقِ موجودیِ محصول. مقدارِ اولیه از همان چیزی ساخته می‌شود که
 * تا امروز داشته‌ایم (تعداد × میانگین)؛ از این به بعد دیگر گرد نمی‌شود.
 */
val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `finished_stock` ADD COLUMN `totalValue` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE finished_stock SET totalValue = qty * avgCost")
    }
}

/**
 * اصلاحِ سطرهای «اعمال بیعانه» که فقط بدهکار ثبت شده بودند. بیعانه لحظهٔ
 * گرفتنش بستانکار شده و صورت‌حسابِ فروش هم کلِ مبلغ را بدهکار می‌کند، پس
 * این سطر باید خنثی باشد؛ بدهکارِ تنها یعنی بیعانه دو بار از مشتری گرفته
 * شده و مشتریِ تسویه‌کرده بدهکار مانده است.
 */
val MIGRATION_48_49 = object : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE ledger_entries SET credit = debit " +
                "WHERE refType = 'PREPAY_APPLIED' AND credit = 0"
        )
    }
}

/**
 * حقوقِ ماهانهٔ کارکنان: مبلغِ توافقی روی هر کارمند + جدولِ پرداخت‌ها.
 * فقط افزودنی است — هیچ ستون یا جدولِ موجودی دست نمی‌خورد.
 */
val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `staff` ADD COLUMN `monthlySalary` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `salary_payments` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`employee` TEXT NOT NULL, `amount` INTEGER NOT NULL, " +
                "`periodKey` TEXT NOT NULL, `periodLabel` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                "`at` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_salary_payments_employee` " +
                "ON `salary_payments` (`employee`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_salary_payments_at` " +
                "ON `salary_payments` (`at`)"
        )
    }
}

val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `audit_log` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`user` TEXT NOT NULL, `role` TEXT NOT NULL, " +
                "`action` TEXT NOT NULL, `detail` TEXT NOT NULL, " +
                "`at` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_at` ON `audit_log` (`at`)")
    }
}

val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `journal_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`memo` TEXT NOT NULL, `refType` TEXT NOT NULL, " +
                "`refId` TEXT NOT NULL, `at` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `journal_lines` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entryId` INTEGER NOT NULL, `account` TEXT NOT NULL, " +
                "`debit` INTEGER NOT NULL, `credit` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_lines_entryId` ON `journal_lines` (`entryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_lines_account` ON `journal_lines` (`account`)")

        fun scalarLong(sql: String): Long {
            val c = db.query(sql)
            val v = if (c.moveToFirst()) c.getLong(0) else 0L
            c.close()
            return v
        }

        fun boxBalance(source: String): Long = scalarLong(
            "SELECT COALESCE(SUM(CASE WHEN type='IN' THEN amount ELSE -amount END),0) " +
                "FROM finance_transactions WHERE source='$source'"
        )

        val wallet = boxBalance("WALLET")
        val bank = boxBalance("BANK")
        val profitBox = boxBalance("PROFIT")
        val materials = scalarLong(
            "SELECT COALESCE(CAST(SUM(amount * avgPrice) AS INTEGER),0) FROM material_stock"
        )
        val finished = scalarLong(
            "SELECT COALESCE(SUM(qty * avgCost),0) FROM finished_stock"
        )
        // دریافتنی: مشتریانِ با ماندهٔ بدهکار در دفتر کل
        var receivable = 0L
        var supplierPayable = 0L
        val lc = db.query(
            "SELECT partyType, SUM(debit) - SUM(credit) AS net FROM ledger_entries " +
                "GROUP BY partyType, partyName"
        )
        while (lc.moveToNext()) {
            val type = lc.getString(0)
            val net = lc.getLong(1)
            if (type == "CUSTOMER" && net > 0) receivable += net
            if (type == "SUPPLIER" && net < 0) supplierPayable += -net
        }
        lc.close()
        val wagesPayable = scalarLong(
            "SELECT COALESCE(SUM(amount),0) FROM tailor_wages WHERE settled = 0"
        )

        val assets = wallet + bank + profitBox + materials + finished + receivable
        val liabilities = supplierPayable + wagesPayable
        if (assets == 0L && liabilities == 0L) return
        val equity = assets - liabilities   // مانده به سرمایه

        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO journal_entries (memo, refType, refId, at) " +
                "VALUES ('سند افتتاحیه — انتقال مانده‌های موجود', 'OPENING', '', $now)"
        )
        val entryId = scalarLong("SELECT last_insert_rowid()")

        fun line(account: String, debit: Long, credit: Long) {
            if (debit == 0L && credit == 0L) return
            db.execSQL(
                "INSERT INTO journal_lines (entryId, account, debit, credit) " +
                    "VALUES (?, ?, ?, ?)",
                arrayOf<Any>(entryId, account, debit, credit)
            )
        }
        fun asset(account: String, v: Long) =
            if (v >= 0) line(account, v, 0) else line(account, 0, -v)

        asset("1010", wallet)
        asset("1020", bank)
        asset("1015", profitBox)
        asset("1040", materials)
        asset("1050", finished)
        asset("1030", receivable)
        line("2010", 0, supplierPayable)
        line("2020", 0, wagesPayable)
        if (equity >= 0) line("3010", 0, equity) else line("3010", -equity, 0)
    }
}

val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        val cur = db.query(
            "SELECT id, orderCode, designTitle, size, qty, fabricPrice, workCost, sewingCost, " +
                "agreedPrice, customerName FROM orders WHERE status = 'SALES'"
        )
        while (cur.moveToNext()) {
            val id = cur.getString(0)
            val code = cur.getString(1) ?: ""
            val title = cur.getString(2) ?: ""
            val size = cur.getString(3) ?: ""
            val qty = cur.getInt(4)
            val cost = cur.getLong(5) + cur.getLong(6) + cur.getLong(7)
            val agreed = cur.getLong(8)
            val customer = cur.getString(9) ?: ""

            if (qty > 0) {
                val per = cost / qty
                val f = db.query(
                    "SELECT id, qty, avgCost FROM finished_stock WHERE name = ? AND size = ?",
                    arrayOf<Any>(title, size)
                )
                if (f.moveToFirst()) {
                    val fid = f.getLong(0)
                    val newQty = f.getInt(1) + qty
                    val newAvg =
                        if (newQty > 0) (f.getInt(1) * f.getLong(2) + qty * per) / newQty else 0L
                    db.execSQL(
                        "UPDATE finished_stock SET qty = ?, avgCost = ?, updatedAt = ? WHERE id = ?",
                        arrayOf<Any>(newQty, newAvg, now, fid)
                    )
                } else {
                    db.execSQL(
                        "INSERT INTO finished_stock (name, size, qty, avgCost, updatedAt) " +
                            "VALUES (?, ?, ?, ?, ?)",
                        arrayOf<Any>(title, size, qty, per, now)
                    )
                }
                f.close()
            }

            db.execSQL(
                "UPDATE orders SET status = 'STORED', stageChangedAt = ? WHERE id = ?",
                arrayOf<Any>(now, id)
            )
            if (customer.isNotBlank() && agreed > 0) {
                db.execSQL(
                    "INSERT INTO ledger_entries (partyType, partyName, debit, credit, refType, refId, note, at) " +
                        "VALUES ('CUSTOMER', ?, 0, ?, 'SALE_TO_STOCK', ?, 'انتقال به انبار محصول', ?)",
                    arrayOf<Any>(customer, agreed, code, now)
                )
            }
        }
        cur.close()
    }
}

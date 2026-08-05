// ساخته‌شده — دستی ویرایش نکنید.
//
// SQLِ مهاجرت‌های تاریخیِ اندروید (`app/.../Migrations.kt`) برای نسخهٔ
// ویندوز. آنجا روی `SupportSQLiteDatabase` نوشته شده‌اند که روی JVM
// وجود ندارد؛ اینجا فقط خودِ دستورها هستند و هر سکو با API خودش
// اجراشان می‌کند.
//
// فقط مهاجرت‌های **SQLِ خالص** اینجا هستند. آن‌هایی که کرسر یا مقدارِ
// زمانِ اجرا می‌خواهند بیرون مانده‌اند و ویندوز نمی‌تواند از رویشان رد
// شود — `DESKTOP_BASELINE` همین را می‌گوید.
//
// بازتولید:  python tools/checks/legacysql.py --write
// نگهبان:    tools/checks/legacysql.py (در `run_all` اجرا می‌شود)

package com.afghanjama.data

/**
 * گام‌های تاریخی، استخراج‌شده از `Migrations.kt`.
 *
 * اندروید این‌ها را **استفاده نمی‌کند** — آنجا همان `ALL_MIGRATIONS`ِ
 * دست‌نویس اجرا می‌شود که روی گوشیِ کارفرما آزموده شده. این فهرست فقط
 * برای ویندوز است.
 */
val LEGACY_STEPS: List<SchemaStep> = listOf(
    SchemaStep(
        19, 20,
        listOf(
            "ALTER TABLE orders ADD COLUMN fabricSource TEXT NOT NULL DEFAULT 'NEW'",
            "ALTER TABLE orders ADD COLUMN agreedPrice INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE orders ADD COLUMN stageChangedAt INTEGER NOT NULL DEFAULT 0",
            "UPDATE orders SET stageChangedAt = createdAt WHERE stageChangedAt = 0",
            "ALTER TABLE finance_transactions ADD COLUMN category TEXT NOT NULL DEFAULT ''",
            "CREATE TABLE IF NOT EXISTS `fabric_stock` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fabricType` TEXT NOT NULL, `fabricColor` TEXT NOT NULL, `fabricUnit` TEXT NOT NULL, `amount` REAL NOT NULL, `minLevel` REAL NOT NULL, `updatedAt` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_fabric_stock_fabricType_fabricColor_fabricUnit` ON `fabric_stock` (`fabricType`, `fabricColor`, `fabricUnit`)",
        )
    ),
    SchemaStep(
        20, 21,
        listOf(
            "ALTER TABLE fabric_stock ADD COLUMN avgPrice REAL NOT NULL DEFAULT 0",
            "CREATE TABLE IF NOT EXISTS `order_stage_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderId` TEXT NOT NULL, `orderCode` TEXT NOT NULL, `fromStatus` TEXT NOT NULL, `toStatus` TEXT NOT NULL, `at` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_order_stage_logs_orderId` ON `order_stage_logs` (`orderId`)",
        )
    ),
    SchemaStep(
        21, 22,
        listOf(
            "CREATE TABLE IF NOT EXISTS `order_fabrics` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderId` TEXT NOT NULL, `fabricType` TEXT NOT NULL, `fabricColor` TEXT NOT NULL, `fabricUnit` TEXT NOT NULL, `amount` REAL NOT NULL, `price` INTEGER NOT NULL, `source` TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_order_fabrics_orderId` ON `order_fabrics` (`orderId`)",
            "CREATE TABLE IF NOT EXISTS `sewing_assignments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderId` TEXT NOT NULL, `orderCode` TEXT NOT NULL, `tailorLabel` TEXT NOT NULL, `qty` INTEGER NOT NULL, `unitWage` INTEGER NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `doneAt` INTEGER)",
            "CREATE INDEX IF NOT EXISTS `index_sewing_assignments_orderId` ON `sewing_assignments` (`orderId`)",
            "ALTER TABLE tailor_wages ADD COLUMN assignmentId INTEGER NOT NULL DEFAULT 0",
            "DROP INDEX IF EXISTS `index_tailor_wages_orderId`",
            "CREATE INDEX IF NOT EXISTS `index_tailor_wages_orderId` ON `tailor_wages` (`orderId`)",
        )
    ),
    SchemaStep(
        22, 23,
        listOf(
            "CREATE TABLE IF NOT EXISTS `order_work_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderId` TEXT NOT NULL, `title` TEXT NOT NULL, `price` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_order_work_items_orderId` ON `order_work_items` (`orderId`)",
        )
    ),
    SchemaStep(
        23, 24,
        listOf(
            "CREATE TABLE IF NOT EXISTS `material_stock` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `amount` REAL NOT NULL, `avgPrice` REAL NOT NULL DEFAULT 0, `minLevel` REAL NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_material_stock_name_unit` ON `material_stock` (`name`, `unit`)",
            "CREATE TABLE IF NOT EXISTS `purchase_invoices` (`id` TEXT PRIMARY KEY NOT NULL, `code` TEXT NOT NULL, `supplier` TEXT NOT NULL, `note` TEXT NOT NULL, `total` INTEGER NOT NULL, `paySource` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `purchase_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `invoiceId` TEXT NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `qty` REAL NOT NULL, `unitPrice` INTEGER NOT NULL, `total` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_purchase_items_invoiceId` ON `purchase_items` (`invoiceId`)",
        )
    ),
    SchemaStep(
        24, 25,
        listOf(
            "CREATE TABLE IF NOT EXISTS `finished_stock` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `size` TEXT NOT NULL, `qty` INTEGER NOT NULL, `avgCost` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_finished_stock_name_size` ON `finished_stock` (`name`, `size`)",
            "CREATE TABLE IF NOT EXISTS `finished_sales` (`id` TEXT PRIMARY KEY NOT NULL, `code` TEXT NOT NULL, `productName` TEXT NOT NULL, `size` TEXT NOT NULL, `qty` INTEGER NOT NULL, `unitPrice` INTEGER NOT NULL, `total` INTEGER NOT NULL, `cost` INTEGER NOT NULL, `customerName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
    ),
    SchemaStep(
        25, 26,
        listOf(
            "CREATE TEMP TABLE IF NOT EXISTS `_fab` AS SELECT TRIM(fabricType || ' ' || fabricColor) AS name, fabricUnit AS unit, SUM(amount) AS amount, CASE WHEN SUM(amount) > 0 THEN SUM(amount * avgPrice) / SUM(amount) ELSE 0 END AS avgPrice, MAX(minLevel) AS minLevel, MAX(updatedAt) AS updatedAt FROM fabric_stock GROUP BY TRIM(fabricType || ' ' || fabricColor), fabricUnit",
            "UPDATE material_stock SET avgPrice = CASE WHEN (amount + (SELECT f.amount FROM _fab f WHERE f.name = material_stock.name AND f.unit = material_stock.unit)) > 0 THEN (amount * avgPrice + (SELECT f.amount * f.avgPrice FROM _fab f WHERE f.name = material_stock.name AND f.unit = material_stock.unit)) / (amount + (SELECT f.amount FROM _fab f WHERE f.name = material_stock.name AND f.unit = material_stock.unit)) ELSE 0 END, amount = amount + (SELECT f.amount FROM _fab f WHERE f.name = material_stock.name AND f.unit = material_stock.unit), updatedAt = (SELECT f.updatedAt FROM _fab f WHERE f.name = material_stock.name AND f.unit = material_stock.unit) WHERE EXISTS (SELECT 1 FROM _fab f WHERE f.name = material_stock.name AND f.unit = material_stock.unit)",
            "INSERT INTO material_stock (name, unit, amount, avgPrice, minLevel, updatedAt) SELECT f.name, f.unit, f.amount, f.avgPrice, f.minLevel, f.updatedAt FROM _fab f WHERE NOT EXISTS (SELECT 1 FROM material_stock m WHERE m.name = f.name AND m.unit = f.unit)",
            "DROP TABLE IF EXISTS `_fab`",
            "DROP TABLE IF EXISTS `fabric_stock`",
        )
    ),
    SchemaStep(
        26, 27,
        listOf(
            "CREATE TABLE IF NOT EXISTS `supplier_ledger` (`id` TEXT PRIMARY KEY NOT NULL, `supplier` TEXT NOT NULL, `amount` INTEGER NOT NULL, `type` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_supplier_ledger_supplier` ON `supplier_ledger` (`supplier`)",
        )
    ),
    SchemaStep(
        27, 28,
        listOf(
            "CREATE TABLE IF NOT EXISTS `customer_measurements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerId` INTEGER NOT NULL, `label` TEXT NOT NULL, `value` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_customer_measurements_customerId` ON `customer_measurements` (`customerId`)",
        )
    ),
    SchemaStep(
        28, 29,
        listOf(
            "CREATE TABLE IF NOT EXISTS `stock_movements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `delta` REAL NOT NULL, `reason` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_stock_movements_name` ON `stock_movements` (`name`)",
        )
    ),
    SchemaStep(
        29, 30,
        listOf(
            "CREATE TABLE IF NOT EXISTS `cutting_records` (`id` TEXT PRIMARY KEY NOT NULL, `orderId` TEXT NOT NULL, `orderCode` TEXT NOT NULL, `cutter` TEXT NOT NULL, `pieces` INTEGER NOT NULL, `waste` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_cutting_records_orderId` ON `cutting_records` (`orderId`)",
        )
    ),
    SchemaStep(
        30, 31,
        listOf(
            "CREATE TABLE IF NOT EXISTS `qc_records` (`id` TEXT PRIMARY KEY NOT NULL, `orderId` TEXT NOT NULL, `orderCode` TEXT NOT NULL, `inspector` TEXT NOT NULL, `result` TEXT NOT NULL, `problem` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_qc_records_orderId` ON `qc_records` (`orderId`)",
        )
    ),
    SchemaStep(
        31, 32,
        listOf(
            "ALTER TABLE sewing_assignments ADD COLUMN quality TEXT NOT NULL DEFAULT ''",
        )
    ),
    SchemaStep(
        32, 33,
        listOf(
            "ALTER TABLE orders ADD COLUMN sewingCost INTEGER NOT NULL DEFAULT 0",
        )
    ),
    SchemaStep(
        33, 34,
        listOf(
            "CREATE TABLE IF NOT EXISTS `attendance` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `employee` TEXT NOT NULL, `checkIn` INTEGER NOT NULL, `checkOut` INTEGER, `createdAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_attendance_employee` ON `attendance` (`employee`)",
        )
    ),
    SchemaStep(
        34, 35,
        listOf(
            "ALTER TABLE orders ADD COLUMN materialsConsumed INTEGER NOT NULL DEFAULT 0",
            "UPDATE orders SET materialsConsumed = 1",
        )
    ),
    SchemaStep(
        35, 36,
        listOf(
            "CREATE TABLE IF NOT EXISTS `parties` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `phone` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_parties_type_name` ON `parties` (`type`, `name`)",
            "CREATE TABLE IF NOT EXISTS `ledger_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `partyType` TEXT NOT NULL, `partyName` TEXT NOT NULL, `debit` INTEGER NOT NULL, `credit` INTEGER NOT NULL, `refType` TEXT NOT NULL, `refId` TEXT NOT NULL, `note` TEXT NOT NULL, `at` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_partyType_partyName` ON `ledger_entries` (`partyType`, `partyName`)",
            "INSERT INTO ledger_entries (partyType, partyName, debit, credit, refType, refId, note, at) SELECT 'SUPPLIER', supplier, CASE WHEN type='PAYMENT' THEN amount ELSE 0 END, CASE WHEN type='CREDIT' THEN amount ELSE 0 END, CASE WHEN type='PAYMENT' THEN 'SUPPLIER_PAYMENT' ELSE 'PURCHASE_CREDIT' END, '', note, createdAt FROM supplier_ledger",
            "INSERT INTO ledger_entries (partyType, partyName, debit, credit, refType, refId, note, at) SELECT 'CUSTOMER', CASE WHEN customerName IS NULL OR customerName='' THEN 'مشتری نامشخص' ELSE customerName END, 0, amount, 'CUSTOMER_' || source, orderId, note, createdAt FROM customer_payments",
            "INSERT INTO ledger_entries (partyType, partyName, debit, credit, refType, refId, note, at) SELECT 'TAILOR', tailorLabel, 0, amount, 'WAGE', orderCode, '', createdAt FROM tailor_wages",
            "INSERT INTO ledger_entries (partyType, partyName, debit, credit, refType, refId, note, at) SELECT 'TAILOR', tailorLabel, amount, 0, 'WAGE_PAID', orderCode, 'تسویه‌شده', COALESCE(settledAt, createdAt) FROM tailor_wages WHERE settled = 1",
            "INSERT OR IGNORE INTO parties (name, type, phone, note, createdAt) SELECT DISTINCT partyName, partyType, '', '', {now} FROM ledger_entries",
        )
    ),
    SchemaStep(
        36, 37,
        listOf(
            "CREATE TABLE IF NOT EXISTS `documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `number` TEXT NOT NULL, `type` TEXT NOT NULL, `partyName` TEXT NOT NULL, `amount` INTEGER NOT NULL, `refId` TEXT NOT NULL, `note` TEXT NOT NULL, `at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_documents_number` ON `documents` (`number`)",
            "CREATE INDEX IF NOT EXISTS `index_documents_type` ON `documents` (`type`)",
        )
    ),
    SchemaStep(
        37, 38,
        listOf(
            "INSERT INTO ledger_entries (partyType, partyName, debit, credit, refType, refId, note, at) SELECT 'CUSTOMER', customerName, agreedPrice, 0, 'SALE_BILLING', orderCode, 'بدهی بابت سفارش', createdAt FROM orders WHERE customerName IS NOT NULL AND customerName != '' AND agreedPrice > 0",
            "INSERT OR IGNORE INTO parties (name, type, phone, note, createdAt) SELECT DISTINCT customerName, 'CUSTOMER', '', '', {now} FROM orders WHERE customerName IS NOT NULL AND customerName != '' AND agreedPrice > 0",
        )
    ),
    SchemaStep(
        38, 39,
        listOf(
            "UPDATE ledger_entries SET partyName = (SELECT o.customerName FROM orders o WHERE o.id = ledger_entries.refId) WHERE partyType = 'CUSTOMER' AND partyName = 'مشتری نامشخص' AND EXISTS (SELECT 1 FROM orders o WHERE o.id = ledger_entries.refId AND o.customerName IS NOT NULL AND o.customerName != '')",
            "UPDATE ledger_entries SET debit = -credit, credit = 0 WHERE credit < 0",
            "UPDATE ledger_entries SET credit = -debit, debit = 0 WHERE debit < 0",
            "INSERT OR IGNORE INTO parties (name, type, phone, note, createdAt) SELECT DISTINCT partyName, partyType, '', '', {now} FROM ledger_entries",
        )
    ),
    SchemaStep(
        39, 40,
        listOf(
            "ALTER TABLE design_items ADD COLUMN code TEXT NOT NULL DEFAULT ''",
            "UPDATE design_items SET code = printf('D-%03d', id) WHERE code = ''",
        )
    ),
    SchemaStep(
        40, 41,
        listOf(
            "CREATE TABLE IF NOT EXISTS `staff` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_staff_name` ON `staff` (`name`)",
        )
    ),
    SchemaStep(
        45, 46,
        listOf(
            "ALTER TABLE `orders` ADD COLUMN `dueDate` INTEGER NOT NULL DEFAULT 0",
        )
    ),
    SchemaStep(
        46, 47,
        listOf(
            "ALTER TABLE `finished_sales` ADD COLUMN `returnedQty` INTEGER NOT NULL DEFAULT 0",
        )
    ),
    SchemaStep(
        47, 48,
        listOf(
            "ALTER TABLE `qc_records` ADD COLUMN `tailor` TEXT NOT NULL DEFAULT ''",
        )
    ),
    SchemaStep(
        60, 61,
        listOf(
            "ALTER TABLE `orders` ADD COLUMN `workCostSource` TEXT NOT NULL DEFAULT 'CREDIT'",
            "ALTER TABLE `orders` ADD COLUMN `workCostPayee` TEXT NOT NULL DEFAULT ''",
        )
    ),
    SchemaStep(
        59, 60,
        listOf(
            "ALTER TABLE `orders` ADD COLUMN `reviewQty` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `orders` ADD COLUMN `storedQty` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `orders` ADD COLUMN `storedCost` INTEGER NOT NULL DEFAULT 0",
            "UPDATE orders SET reviewQty = qty WHERE status = 'REVIEW'",
            "UPDATE orders SET storedQty = qty, storedCost = fabricPrice + workCost + sewingCost WHERE status IN ('STORED', 'SENT')",
        )
    ),
    SchemaStep(
        58, 59,
        listOf(
            "ALTER TABLE `fabric_types` ADD COLUMN `season` TEXT NOT NULL DEFAULT ''",
        )
    ),
    SchemaStep(
        57, 58,
        listOf(
            "ALTER TABLE `purchase_items` ADD COLUMN `perPack` INTEGER NOT NULL DEFAULT 0",
        )
    ),
    SchemaStep(
        56, 57,
        listOf(
            "ALTER TABLE `design_items` ADD COLUMN `category` TEXT NOT NULL DEFAULT ''",
        )
    ),
    SchemaStep(
        55, 56,
        listOf(
            "UPDATE `finance_transactions` SET `category` = 'انتقال داخلی' WHERE `category` = '' AND (`note` LIKE 'انتقال سود فروش%' OR `note` LIKE 'سود فروش %' OR `note` LIKE 'برگشت سود فروش%')",
        )
    ),
    SchemaStep(
        54, 55,
        listOf(
            "ALTER TABLE `finished_sales` ADD COLUMN `discount` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `finished_stock` ADD COLUMN `photoFile` TEXT NOT NULL DEFAULT ''",
        )
    ),
    SchemaStep(
        53, 54,
        listOf(
            "CREATE TABLE IF NOT EXISTS `order_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderId` TEXT NOT NULL, `orderCode` TEXT NOT NULL, `fileName` TEXT NOT NULL, `note` TEXT NOT NULL DEFAULT '', `createdAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_order_photos_orderId` ON `order_photos` (`orderId`)",
        )
    ),
    SchemaStep(
        52, 53,
        listOf(
            "ALTER TABLE `orders` ADD COLUMN `deliveredQty` INTEGER NOT NULL DEFAULT 0",
            "UPDATE orders SET deliveredQty = qty WHERE status = 'SENT'",
        )
    ),
    SchemaStep(
        51, 52,
        listOf(
            "CREATE TABLE IF NOT EXISTS `sync_requests` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deviceName` TEXT NOT NULL, `worker` TEXT NOT NULL, `type` TEXT NOT NULL, `summary` TEXT NOT NULL, `refId` INTEGER NOT NULL DEFAULT 0, `amount` INTEGER NOT NULL DEFAULT 0, `note` TEXT NOT NULL DEFAULT '', `status` TEXT NOT NULL DEFAULT 'PENDING', `createdAt` INTEGER NOT NULL, `decidedAt` INTEGER, `decidedNote` TEXT NOT NULL DEFAULT '')",
            "CREATE INDEX IF NOT EXISTS `index_sync_requests_status` ON `sync_requests` (`status`)",
        )
    ),
    SchemaStep(
        50, 51,
        listOf(
            "CREATE TABLE IF NOT EXISTS `break_times` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `enabled` INTEGER NOT NULL DEFAULT 1, `createdAt` INTEGER NOT NULL)",
        )
    ),
    SchemaStep(
        49, 50,
        listOf(
            "ALTER TABLE `finished_stock` ADD COLUMN `totalValue` INTEGER NOT NULL DEFAULT 0",
            "UPDATE finished_stock SET totalValue = qty * avgCost",
        )
    ),
    SchemaStep(
        48, 49,
        listOf(
            "UPDATE ledger_entries SET credit = debit WHERE refType = 'PREPAY_APPLIED' AND credit = 0",
        )
    ),
    SchemaStep(
        44, 45,
        listOf(
            "ALTER TABLE `staff` ADD COLUMN `monthlySalary` INTEGER NOT NULL DEFAULT 0",
            "CREATE TABLE IF NOT EXISTS `salary_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `employee` TEXT NOT NULL, `amount` INTEGER NOT NULL, `periodKey` TEXT NOT NULL, `periodLabel` TEXT NOT NULL, `source` TEXT NOT NULL, `note` TEXT NOT NULL, `at` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_salary_payments_employee` ON `salary_payments` (`employee`)",
            "CREATE INDEX IF NOT EXISTS `index_salary_payments_at` ON `salary_payments` (`at`)",
        )
    ),
    SchemaStep(
        43, 44,
        listOf(
            "CREATE TABLE IF NOT EXISTS `audit_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user` TEXT NOT NULL, `role` TEXT NOT NULL, `action` TEXT NOT NULL, `detail` TEXT NOT NULL, `at` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_audit_log_at` ON `audit_log` (`at`)",
        )
    ),
)

/*
 * کنار گذاشته‌شده‌ها — SQLِ خالص نیستند:
 *   42 → 43: execSQL با پارامترِ bind
 *   41 → 42: execSQL با پارامترِ bind
 */

// app/src/main/java/com/afghanjama/data/AppDatabase.kt
package com.afghanjama.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.afghanjama.data.dao.AttendanceDao
import com.afghanjama.data.dao.AuditDao
import com.afghanjama.data.dao.BreakTimeDao
import com.afghanjama.data.dao.CatalogDao
import com.afghanjama.data.dao.CustomerMeasurementDao
import com.afghanjama.data.dao.CustomerPaymentDao
import com.afghanjama.data.dao.CuttingRecordDao
import com.afghanjama.data.dao.DocumentDao
import com.afghanjama.data.dao.FinanceDao
import com.afghanjama.data.dao.FinishedStockDao
import com.afghanjama.data.dao.JournalDao
import com.afghanjama.data.dao.LedgerDao
import com.afghanjama.data.dao.MasterDataDao
import com.afghanjama.data.dao.MaterialStockDao
import com.afghanjama.data.dao.OrderCounterDao
import com.afghanjama.data.dao.StockMovementDao
import com.afghanjama.data.dao.SupplierDao
import com.afghanjama.data.dao.OrderDao
import com.afghanjama.data.dao.OrderFabricDao
import com.afghanjama.data.dao.OrderPhotoDao
import com.afghanjama.data.dao.OrderStageLogDao
import com.afghanjama.data.dao.OrderWorkItemDao
import com.afghanjama.data.dao.ProcurementDao
import com.afghanjama.data.dao.QcRecordDao
import com.afghanjama.data.dao.SalaryDao
import com.afghanjama.data.dao.SewingAssignmentDao
import com.afghanjama.data.dao.SyncRequestDao
import com.afghanjama.data.dao.TailorWageDao
import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.entities.AuditLog
import com.afghanjama.data.entities.BreakTime
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.CustomerMeasurement
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.CuttingRecord
import com.afghanjama.data.entities.DesignItem
import com.afghanjama.data.entities.Document
import com.afghanjama.data.entities.FabricColor
import com.afghanjama.data.entities.FabricType
import com.afghanjama.data.entities.FinishedSale
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.data.entities.GarmentDesign
import com.afghanjama.data.entities.Inspector
import com.afghanjama.data.entities.JournalEntry
import com.afghanjama.data.entities.JournalLine
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.data.entities.MaterialStock
import com.afghanjama.data.entities.Party
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderCounter
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderPhoto
import com.afghanjama.data.entities.OrderStageLog
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.data.entities.PurchaseInvoice
import com.afghanjama.data.entities.QcRecord
import com.afghanjama.data.entities.PurchaseItem
import com.afghanjama.data.entities.SalaryPayment
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.entities.SizeItem
import com.afghanjama.data.entities.Staff
import com.afghanjama.data.entities.StockMovement
import com.afghanjama.data.entities.SupplierLedger
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.entities.Tailor
import com.afghanjama.data.entities.TailorWage
import com.afghanjama.data.entities.Transaction
import com.afghanjama.data.entities.WorkCost

@Database(
    entities = [
        Order::class,
        OrderCounter::class,
        Transaction::class,
        FabricType::class,
        FabricColor::class,
        SizeItem::class,
        Tailor::class,
        Inspector::class,
        DesignItem::class,
        Customer::class,
        WorkCost::class,
        GarmentDesign::class,
        CustomerPayment::class,
        TailorWage::class,
        OrderStageLog::class,
        OrderFabric::class,
        SewingAssignment::class,
        OrderWorkItem::class,
        MaterialStock::class,
        PurchaseInvoice::class,
        PurchaseItem::class,
        FinishedStock::class,
        FinishedSale::class,
        SupplierLedger::class,
        CustomerMeasurement::class,
        StockMovement::class,
        CuttingRecord::class,
        QcRecord::class,
        AttendanceRecord::class,
        Party::class,
        LedgerEntry::class,
        Document::class,
        Staff::class,
        JournalEntry::class,
        JournalLine::class,
        AuditLog::class,
        SalaryPayment::class,
        BreakTime::class,
        SyncRequest::class,
        OrderPhoto::class
    ],
    version = DB_VERSION,
    exportSchema = false
)
@TypeConverters(Converters::class)
/*
 * `Db` را پیاده می‌کند تا منطقِ کارگاه به موتورِ Room گره نخورد؛
 * توضیحِ کامل در Db.kt.
 */
abstract class AppDatabase : RoomDatabase(), Db {
    abstract override fun orderDao(): OrderDao
    abstract override fun orderCounterDao(): OrderCounterDao
    abstract override fun financeDao(): FinanceDao
    abstract override fun masterDataDao(): MasterDataDao
    abstract override fun catalogDao(): CatalogDao
    abstract override fun tailorWageDao(): TailorWageDao
    abstract override fun customerPaymentDao(): CustomerPaymentDao
    abstract override fun orderStageLogDao(): OrderStageLogDao
    abstract override fun orderFabricDao(): OrderFabricDao
    abstract override fun sewingAssignmentDao(): SewingAssignmentDao
    abstract override fun orderWorkItemDao(): OrderWorkItemDao
    abstract override fun materialStockDao(): MaterialStockDao
    abstract override fun procurementDao(): ProcurementDao
    abstract override fun finishedStockDao(): FinishedStockDao
    abstract override fun supplierDao(): SupplierDao
    abstract override fun customerMeasurementDao(): CustomerMeasurementDao
    abstract override fun stockMovementDao(): StockMovementDao
    abstract override fun cuttingRecordDao(): CuttingRecordDao
    abstract override fun qcRecordDao(): QcRecordDao
    abstract override fun attendanceDao(): AttendanceDao
    abstract override fun ledgerDao(): LedgerDao
    abstract override fun documentDao(): DocumentDao
    abstract override fun journalDao(): JournalDao
    abstract override fun auditDao(): AuditDao
    abstract override fun salaryDao(): SalaryDao
    abstract override fun breakTimeDao(): BreakTimeDao
    abstract override fun syncRequestDao(): SyncRequestDao
    abstract override fun orderPhotoDao(): OrderPhotoDao

    // ------------------------------------------------------------
    // پیاده‌سازیِ اندرویدیِ عملیاتِ سطحِ فایلِ `Db`.
    //
    // اینها تنها جایی‌اند که موتورِ Room لازم می‌شود. روی ویندوز همین
    // چهار کار با موتورِ آنجا نوشته می‌شود و منطقِ کارگاه دست نمی‌خورد.
    // ------------------------------------------------------------

    override fun checkpoint() {
        openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)")
            .use { it.moveToFirst() }
    }

    override fun closeConnection() {
        // Room پس از بستن، در اولین دسترسیِ بعدی خودش دوباره باز می‌کند.
        if (isOpen) close()
    }

    override fun tableNames(): List<String> {
        val names = mutableListOf<String>()
        openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_metadata' " +
                "AND name NOT LIKE 'room_master_table'"
        ).use { c ->
            while (c.moveToNext()) names += c.getString(0)
        }
        return names
    }

    override fun clearTables(tables: List<String>): Int {
        var cleared = 0
        // شکلِ Runnable عمداً صریح نوشته شده: `runInTransaction` دو نسخه
        // دارد (Runnable و Callable) و لامبدای برهنه می‌تواند مبهم باشد.
        runInTransaction(
            Runnable {
                val w = openHelper.writableDatabase
                tables.forEach { table ->
                    w.execSQL("DELETE FROM `$table`")
                    cleared++
                }
                // شمارنده‌های AUTOINCREMENT هم از اول شروع کنند، وگرنه
                // شناسهٔ اولین سندِ تازه از وسطِ راه ادامه پیدا می‌کند.
                // اگر جدولی AUTOINCREMENT نداشته باشد این جدول اصلاً
                // وجود ندارد، پس شکستش بی‌اهمیت است.
                runCatching {
                    w.execSQL(
                        "DELETE FROM sqlite_sequence WHERE name IN (" +
                            tables.joinToString(",") { "'$it'" } + ")"
                    )
                }
            }
        )
        return cleared
    }
}

/** همهٔ Migrationها یک‌جا تا Workerها و اپ هرگز از هم جدا نیفتند. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
    MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27,
    MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31,
    MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35,
    MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39,
    MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43,
    MIGRATION_43_44, MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47,
    MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50,
    MIGRATION_50_51, MIGRATION_51_52, MIGRATION_52_53,
    MIGRATION_53_54, MIGRATION_54_55, MIGRATION_55_56,
    MIGRATION_56_57, MIGRATION_57_58, MIGRATION_58_59,
    MIGRATION_59_60, MIGRATION_60_61
)

/*
 * `DB_NAME` و `DB_VERSION` به `:core` رفتند.
 *
 * از وقتی نسخهٔ ویندوز `@Database`ِ خودش را دارد، این عدد باید بینِ دو
 * سکو یکی باشد؛ وگرنه فایلی که از گوشی به پی‌سی می‌رود باز نمی‌شود.
 * بستهٔ هر دو یکی است (`com.afghanjama.data`)، پس هیچ ایمپورتی عوض نشد.
 */

/**
 * ساخت متمرکز دیتابیس. همهٔ نقاط (اپ و Workerها) باید از این استفاده
 * کنند تا لیست Migration هرگز ناقص نماند (جلوگیری از پاک‌شدن ناخواستهٔ داده).
 */
fun buildAppDatabase(context: Context): AppDatabase =
    Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
        /*
         * `ALL_MIGRATIONS` همان ۴۲ مهاجرتِ تاریخی است (۱۹ تا ۶۱) و
         * دست‌نخورده می‌ماند.
         *
         * `sharedMigrations()` گام‌های **آینده** است که در `:core`
         * نوشته می‌شوند تا ویندوز هم همان‌ها را اجرا کند. امروز
         * `SCHEMA_STEPS` خالی است، پس این خط هیچ رفتاری را عوض نمی‌کند —
         * یک آرایهٔ خالی به `addMigrations` اضافه می‌شود و بس.
         */
        .addMigrations(*ALL_MIGRATIONS, *sharedMigrations())
        .fallbackToDestructiveMigration()
        .build()

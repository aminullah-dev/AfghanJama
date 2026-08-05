package com.afghanjama.desktop.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.room.TypeConverters
import androidx.room.PooledConnection
import androidx.room.Transactor
import com.afghanjama.data.Converters
import com.afghanjama.data.DB_VERSION
import com.afghanjama.data.SQL_WORKSHOP_TABLES
import com.afghanjama.data.Db
import com.afghanjama.data.dao.AttendanceDao
import com.afghanjama.data.dao.AuditDao
import com.afghanjama.data.dao.DomainEventDao
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
import com.afghanjama.data.dao.OrderDao
import com.afghanjama.data.dao.OrderFabricDao
import com.afghanjama.data.dao.OrderPhotoDao
import com.afghanjama.data.dao.OrderStageLogDao
import com.afghanjama.data.dao.OrderWorkItemDao
import com.afghanjama.data.dao.ProcurementDao
import com.afghanjama.data.dao.QcRecordDao
import com.afghanjama.data.dao.SalaryDao
import com.afghanjama.data.dao.SewingAssignmentDao
import com.afghanjama.data.dao.StockMovementDao
import com.afghanjama.data.dao.SupplierDao
import com.afghanjama.data.dao.SyncRequestDao
import com.afghanjama.data.dao.TailorWageDao
import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.entities.AuditLog
import com.afghanjama.data.entities.DomainEvent
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
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderCounter
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderPhoto
import com.afghanjama.data.entities.OrderStageLog
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.data.entities.Party
import com.afghanjama.data.entities.PurchaseInvoice
import com.afghanjama.data.entities.PurchaseItem
import com.afghanjama.data.entities.QcRecord
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
import kotlinx.coroutines.runBlocking

/**
 * دفترِ کارگاه روی ویندوز.
 *
 * **چرا `@Database`ِ دوم و نه همان `AppDatabase`:** مهاجرت‌ها.
 * `Migrations.kt` هزار خط است و هر ۴۲ مهاجرتش روی
 * `SupportSQLiteDatabase` نوشته شده — API فقط-اندرویدیِ Room. بردنِ
 * `@Database` به `:core` یعنی بازنویسیِ همهٔ آن‌ها با API درایورِ جدید؛
 * یعنی دست بردن در کدی که دادهٔ واقعیِ کارگاه را ارتقا می‌دهد. آن ریسک
 * ارزشش را نداشت.
 *
 * **پس چه چیزی مشترک است؟** همه چیزِ مهم: هر ۴۰ موجودیت، تبدیل‌گرها و
 * عددِ نسخه از `:core` می‌آیند. یعنی **اسکیما از یک جا توصیف می‌شود** و
 * فایلِ دیتابیس بینِ گوشی و پی‌سی قابلِ جابه‌جایی می‌ماند. آنچه دوتاست
 * فقط اعلانِ `@Database` است، و بررسیِ `dbtwin` نمی‌گذارد آن دو از هم
 * جدا بیفتند.
 *
 * **مهاجرت روی ویندوز هنوز نیست.** این دیتابیس تازه ساخته می‌شود، پس
 * مستقیم روی نسخهٔ [DB_VERSION] می‌نشیند. اولین باری که اسکیما عوض شود،
 * اینجا هم باید مهاجرت اضافه شود — وگرنه فایلِ قدیمیِ روی پی‌سی باز
 * نمی‌شود.
 */
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
        DomainEvent::class,
        SalaryPayment::class,
        BreakTime::class,
        SyncRequest::class,
        OrderPhoto::class
    ],
    version = DB_VERSION,
    // **طرحِ دیتابیس صادر می‌شود و در گیت می‌ماند.**
    //
    // تا امروز `false` بود، یعنی طرحِ ۴۳ جدول هیچ‌جا نسخه‌برداری نمی‌شد.
    // با ۴۲ مهاجرتِ دستی، تنها چیزی که تضمین می‌کرد مهاجرت‌ها به همان
    // طرحی برسند که موجودیت‌ها توصیف می‌کنند، **آدم** بود.
    //
    // با `true`، Room برای هر نسخه یک JSON می‌نویسد (`schemas/`) که
    // دو کار می‌کند: خودش سرِ ساخت می‌گوید اگر مهاجرتی طرح را جایی
    // نبرد که موجودیت‌ها می‌گویند، و از نسخهٔ بعد به بعد آزمونِ واقعیِ
    // مهاجرت (`MigrationTestHelper`) ممکن می‌شود — که بدونِ فایلِ
    // نسخهٔ قبلی اصلاً ممکن نیست.
    //
    // **صادقانه:** نسخه‌های ۱۹ تا ۶۰ هرگز صادر نشدند و دیگر قابلِ
    // بازسازی نیستند. پس آن ۴۲ مهاجرتِ گذشته را نمی‌شود اجرایی آزمود؛
    // فقط ایستا (`migrationgap`, `legacysql`). این تغییر جلوی تکرارِ
    // همان وضعیت را برای آینده می‌گیرد.
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class DesktopDatabase : RoomDatabase(), Db {
    /*
     * مرزِ تراکنش برای این سکو.
     *
     * **چرا اینجا با اندروید فرق دارد:** `withTransaction` در
     * `room-ktx` است و آن بسته فقط اندرویدی است — CI همین را گرفت:
     * «Unresolved reference 'withTransaction'». نسخهٔ چندسکوییِ Room
     * همین کار را با `useWriterConnection` + `immediateTransaction`
     * انجام می‌دهد.
     *
     * تراکنش روی کوروتین است و **تودرتو-امن**:
     * اگر عملیاتی داخلِ عملیاتِ دیگری صدا زده شود، در همان تراکنشِ
     * بیرونی ادغام می‌شود و دو بار commit نمی‌کند. `sellInvoice` که
     * `addFinishedStock` را صدا می‌زند دقیقاً همین حالت است.
     *
     * متدِ **پیاده‌شده** است نه abstract، چون Room فقط برای متدهای
     * abstract کد تولید می‌کند.
     */
    override suspend fun <T> atomic(block: suspend () -> T): T =
        useWriterConnection { it.immediateTransaction { block() } }

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

    abstract override fun domainEventDao(): DomainEventDao
    abstract override fun salaryDao(): SalaryDao
    abstract override fun breakTimeDao(): BreakTimeDao
    abstract override fun syncRequestDao(): SyncRequestDao
    abstract override fun orderPhotoDao(): OrderPhotoDao

    // ------------------------------------------------------------
    // عملیاتِ سطحِ فایل — همان چهارتایی که `Db` نام می‌برد.
    //
    // روی اندروید اینها با `openHelper` انجام می‌شوند. اینجا موتور
    // درایوری است، پس از راهِ اتصالِ نویسنده می‌روند.
    // ------------------------------------------------------------

    /**
     * اجرای یک دستور روی اتصالِ استخر.
     *
     * `execSQL` فقط روی `SQLiteConnection` هست، ولی چیزی که Room اینجا
     * می‌دهد `Transactor`/`TransactionScope` است و آن‌ها
     * `PooledConnection`اند نه `SQLiteConnection`. راهِ درست
     * `usePrepared` است.
     */
    private suspend fun PooledConnection.exec(sql: String) {
        usePrepared(sql) { it.step() }
    }

    override fun checkpoint() {
        runBlocking {
            useWriterConnection { conn: Transactor ->
                conn.exec("PRAGMA wal_checkpoint(TRUNCATE)")
            }
        }
    }

    override fun closeConnection() = close()

    override fun tableNames(): List<String> = runBlocking {
        useWriterConnection { conn: Transactor ->
            // پرس‌وجو از `:core` می‌آید — پیش‌تر اینجا نسخهٔ خودش را
            // داشت و `room_master_table` را کنار نمی‌گذاشت، یعنی
            // «پاک کردنِ داده» مهرِ هویتِ Room را هم پاک می‌کرد.
            conn.usePrepared(SQL_WORKSHOP_TABLES) { stmt ->
                buildList { while (stmt.step()) add(stmt.getText(0)) }
            }
        }
    }

    /**
     * خالی کردنِ جدول‌ها، همه در یک تراکنش.
     *
     * **چرا تراکنش با SQL خام و نه API تراکنشِ Room:** دو بار پشتِ هم
     * نامِ آن API را اشتباه حدس زدم و هر بار یک رفت‌وبرگشتِ CI سوخت —
     * اینجا Gradle اجرا نمی‌شود، پس امضای کتابخانه قابلِ دیدن نیست.
     * `BEGIN IMMEDIATE`/`COMMIT`/`ROLLBACK` خودِ SQLite است، رفتارش
     * قطعی است، و همان اتمی‌بودنی را می‌دهد که `Db` می‌خواهد.
     *
     * ریستِ نصفه بدترین حالت است: سفارش‌ها رفته ولی دفتر مانده و به
     * سفارشی ارجاع می‌دهد که دیگر نیست.
     */
    override fun clearTables(tables: List<String>): Int = runBlocking {
        useWriterConnection { conn: Transactor ->
            var cleared = 0
            conn.exec("BEGIN IMMEDIATE")
            try {
                tables.forEach { table ->
                    conn.exec("DELETE FROM `$table`")
                    cleared++
                }
                // شمارنده‌های AUTOINCREMENT هم از اول شروع کنند، وگرنه
                // شناسهٔ اولین سندِ تازه از وسطِ راه ادامه پیدا می‌کند.
                // اگر جدولِ sqlite_sequence نباشد، شکستش بی‌اهمیت است.
                runCatching {
                    conn.exec(
                        "DELETE FROM sqlite_sequence WHERE name IN (" +
                            tables.joinToString(",") { "'$it'" } + ")"
                    )
                }
                conn.exec("COMMIT")
            } catch (t: Throwable) {
                runCatching { conn.exec("ROLLBACK") }
                throw t
            }
            cleared
        }
    }
}

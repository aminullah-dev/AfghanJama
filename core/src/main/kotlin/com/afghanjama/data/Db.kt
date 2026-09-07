package com.afghanjama.data

import com.afghanjama.data.dao.AttendanceDao
import com.afghanjama.data.dao.AuditDao
import com.afghanjama.data.dao.DomainEventDao
import com.afghanjama.data.dao.BreakTimeDao
import com.afghanjama.data.dao.CatalogDao
import com.afghanjama.data.dao.CustomerMeasurementDao
import com.afghanjama.data.dao.CustomerInstallmentDao
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

/**
 * دفترِ داده‌ها، از دیدِ منطقِ کارگاه.
 *
 * `Repo` تا امروز مستقیم به `AppDatabase` وصل بود — و آن یک
 * `RoomDatabase` است، یعنی موتورِ اندرویدیِ Room. نتیجه: ۲٬۹۷۲ خط
 * حسابداری که هیچ ایمپورتِ اندرویدی نداشت، فقط به خاطرِ همین یک تایپ
 * نمی‌توانست از اندروید جدا شود.
 *
 * این رابط همان ۲۸ در را نام می‌برد، بی آنکه بگوید پشتشان چه موتوری
 * است. روی گوشی `AppDatabase` پیاده‌اش می‌کند (Room روی SQLite
 * اندروید)، و روی ویندوز هر چیزِ دیگری که همین درها را باز کند.
 *
 * **قاعده:** هیچ‌وقت متدی از `RoomDatabase` (مثلِ `clearAllTables` یا
 * `runInTransaction`) به این رابط اضافه نشود؛ همان لحظه دوباره به
 * اندروید گره می‌خورد. اگر منطق به چنین چیزی نیاز داشت، باید به‌صورتِ
 * یک عملیاتِ نام‌دار تعریف شود نه نشتیِ موتور.
 */
interface Db : Tx {
    fun orderDao(): OrderDao
    fun orderCounterDao(): OrderCounterDao
    fun financeDao(): FinanceDao
    fun masterDataDao(): MasterDataDao
    fun catalogDao(): CatalogDao
    fun tailorWageDao(): TailorWageDao
    fun customerPaymentDao(): CustomerPaymentDao
    fun customerInstallmentDao(): CustomerInstallmentDao
    fun orderStageLogDao(): OrderStageLogDao
    fun orderFabricDao(): OrderFabricDao
    fun sewingAssignmentDao(): SewingAssignmentDao
    fun orderWorkItemDao(): OrderWorkItemDao
    fun materialStockDao(): MaterialStockDao
    fun procurementDao(): ProcurementDao
    fun finishedStockDao(): FinishedStockDao
    fun supplierDao(): SupplierDao
    fun customerMeasurementDao(): CustomerMeasurementDao
    fun stockMovementDao(): StockMovementDao
    fun cuttingRecordDao(): CuttingRecordDao
    fun qcRecordDao(): QcRecordDao
    fun attendanceDao(): AttendanceDao
    fun ledgerDao(): LedgerDao
    fun documentDao(): DocumentDao
    fun journalDao(): JournalDao
    fun auditDao(): AuditDao

    /**
     * صندوقِ خروجیِ رویدادها.
     *
     * اینجا در رابطِ مشترک است نه در یکی از دو سکو: رویداد در همان
     * تراکنشی نوشته می‌شود که دادهٔ اصلی، و `Repo` — که مشترک است —
     * باید بتواند صدایش بزند.
     */
    fun domainEventDao(): DomainEventDao
    fun salaryDao(): SalaryDao
    fun breakTimeDao(): BreakTimeDao
    fun syncRequestDao(): SyncRequestDao
    fun orderPhotoDao(): OrderPhotoDao

    // ------------------------------------------------------------
    // عملیاتِ سطحِ فایل
    //
    // این چهارتا واقعاً به موتور کار دارند، ولی هر کدام یک **کارِ
    // نام‌دار** است نه یک درِ باز به موتور. `Repo` تا دیروز مستقیم
    // `openHelper` و `runInTransaction` را صدا می‌زد و همان بود که
    // نگذاشت از اندروید جدا شود.
    // ------------------------------------------------------------

    /**
     * یکپارچه‌سازیِ WAL پیش از پشتیبان‌گیری.
     *
     * بدونِ این، نوشته‌های اخیر هنوز در فایلِ جانبی‌اند و پشتیبانی که
     * گرفته می‌شود ناقص است.
     */
    fun checkpoint()

    /**
     * بستنِ اتصال — فقط پیش از بازنویسیِ خودِ فایل (بازیابیِ پشتیبان).
     *
     * اگر فایل زیرِ پای یک اتصالِ باز عوض شود، صفحه‌های کش‌شدهٔ همان
     * اتصال با محتوای تازه نمی‌خوانَد و دیتابیس خراب می‌شود.
     */
    fun closeConnection()

    /** نامِ جدول‌های واقعیِ دیتابیس — برای وارسیِ فهرستِ ریست. */
    fun tableNames(): List<String>

    /**
     * خالی کردنِ جدول‌های داده‌شده، **همه در یک تراکنش**.
     *
     * ریستِ نصفه بدترین حالت است: سفارش‌ها رفته ولی دفتر مانده و به
     * سفارشی ارجاع می‌دهد که دیگر نیست.
     *
     * @return تعدادِ جدولی که خالی شد
     */
    fun clearTables(tables: List<String>): Int
}

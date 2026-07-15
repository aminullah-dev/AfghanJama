// app/src/main/java/com/afghanjama/data/AppDatabase.kt
package com.afghanjama.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.afghanjama.data.dao.AttendanceDao
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
import com.afghanjama.data.dao.OrderStageLogDao
import com.afghanjama.data.dao.OrderWorkItemDao
import com.afghanjama.data.dao.ProcurementDao
import com.afghanjama.data.dao.QcRecordDao
import com.afghanjama.data.dao.SewingAssignmentDao
import com.afghanjama.data.dao.TailorWageDao
import com.afghanjama.data.entities.AttendanceRecord
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
import com.afghanjama.data.entities.OrderStageLog
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.data.entities.PurchaseInvoice
import com.afghanjama.data.entities.QcRecord
import com.afghanjama.data.entities.PurchaseItem
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.entities.SizeItem
import com.afghanjama.data.entities.Staff
import com.afghanjama.data.entities.StockMovement
import com.afghanjama.data.entities.SupplierLedger
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
        JournalLine::class
    ],
    version = 43,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun orderCounterDao(): OrderCounterDao
    abstract fun financeDao(): FinanceDao
    abstract fun masterDataDao(): MasterDataDao
    abstract fun catalogDao(): CatalogDao
    abstract fun tailorWageDao(): TailorWageDao
    abstract fun customerPaymentDao(): CustomerPaymentDao
    abstract fun orderStageLogDao(): OrderStageLogDao
    abstract fun orderFabricDao(): OrderFabricDao
    abstract fun sewingAssignmentDao(): SewingAssignmentDao
    abstract fun orderWorkItemDao(): OrderWorkItemDao
    abstract fun materialStockDao(): MaterialStockDao
    abstract fun procurementDao(): ProcurementDao
    abstract fun finishedStockDao(): FinishedStockDao
    abstract fun supplierDao(): SupplierDao
    abstract fun customerMeasurementDao(): CustomerMeasurementDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun cuttingRecordDao(): CuttingRecordDao
    abstract fun qcRecordDao(): QcRecordDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun documentDao(): DocumentDao
    abstract fun journalDao(): JournalDao
}

/** همهٔ Migrationها یک‌جا تا Workerها و اپ هرگز از هم جدا نیفتند. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
    MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27,
    MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31,
    MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35,
    MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39,
    MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43
)

const val DB_NAME = "afghanjama.db"

/**
 * ساخت متمرکز دیتابیس. همهٔ نقاط (اپ و Workerها) باید از این استفاده
 * کنند تا لیست Migration هرگز ناقص نماند (جلوگیری از پاک‌شدن ناخواستهٔ داده).
 */
fun buildAppDatabase(context: Context): AppDatabase =
    Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
        .addMigrations(*ALL_MIGRATIONS)
        .fallbackToDestructiveMigration()
        .build()

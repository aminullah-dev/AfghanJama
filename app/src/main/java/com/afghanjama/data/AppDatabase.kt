// app/src/main/java/com/afghanjama/data/AppDatabase.kt
package com.afghanjama.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.afghanjama.data.dao.CatalogDao
import com.afghanjama.data.dao.CustomerPaymentDao
import com.afghanjama.data.dao.FabricStockDao
import com.afghanjama.data.dao.FinanceDao
import com.afghanjama.data.dao.FinishedStockDao
import com.afghanjama.data.dao.MasterDataDao
import com.afghanjama.data.dao.MaterialStockDao
import com.afghanjama.data.dao.OrderCounterDao
import com.afghanjama.data.dao.OrderDao
import com.afghanjama.data.dao.OrderFabricDao
import com.afghanjama.data.dao.OrderStageLogDao
import com.afghanjama.data.dao.OrderWorkItemDao
import com.afghanjama.data.dao.ProcurementDao
import com.afghanjama.data.dao.SewingAssignmentDao
import com.afghanjama.data.dao.TailorWageDao
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.DesignItem
import com.afghanjama.data.entities.FabricColor
import com.afghanjama.data.entities.FabricStock
import com.afghanjama.data.entities.FabricType
import com.afghanjama.data.entities.FinishedSale
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.data.entities.GarmentDesign
import com.afghanjama.data.entities.Inspector
import com.afghanjama.data.entities.MaterialStock
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderCounter
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderStageLog
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.data.entities.PurchaseInvoice
import com.afghanjama.data.entities.PurchaseItem
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.entities.SizeItem
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
        FabricStock::class,
        OrderStageLog::class,
        OrderFabric::class,
        SewingAssignment::class,
        OrderWorkItem::class,
        MaterialStock::class,
        PurchaseInvoice::class,
        PurchaseItem::class,
        FinishedStock::class,
        FinishedSale::class
    ],
    version = 25,
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
    abstract fun fabricStockDao(): FabricStockDao
    abstract fun orderStageLogDao(): OrderStageLogDao
    abstract fun orderFabricDao(): OrderFabricDao
    abstract fun sewingAssignmentDao(): SewingAssignmentDao
    abstract fun orderWorkItemDao(): OrderWorkItemDao
    abstract fun materialStockDao(): MaterialStockDao
    abstract fun procurementDao(): ProcurementDao
    abstract fun finishedStockDao(): FinishedStockDao
}

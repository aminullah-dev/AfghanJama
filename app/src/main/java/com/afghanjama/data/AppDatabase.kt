// app/src/main/java/com/afghanjama/data/AppDatabase.kt
package com.afghanjama.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.afghanjama.data.dao.CatalogDao
import com.afghanjama.data.dao.FinanceDao
import com.afghanjama.data.dao.MasterDataDao
import com.afghanjama.data.dao.OrderCounterDao
import com.afghanjama.data.dao.OrderDao
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.DesignItem
import com.afghanjama.data.entities.FabricColor
import com.afghanjama.data.entities.FabricType
import com.afghanjama.data.entities.GarmentDesign
import com.afghanjama.data.entities.Inspector
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderCounter
import com.afghanjama.data.entities.SizeItem
import com.afghanjama.data.entities.Tailor
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
        CustomerPayment::class
    ],
    version = 17,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun orderCounterDao(): OrderCounterDao
    abstract fun financeDao(): FinanceDao
    abstract fun masterDataDao(): MasterDataDao
    abstract fun catalogDao(): CatalogDao
}

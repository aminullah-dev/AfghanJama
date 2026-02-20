package com.afghanjama.data.repo

import com.afghanjama.data.AppDatabase
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.DesignItem
import com.afghanjama.data.entities.FabricColor
import com.afghanjama.data.entities.FabricType
import com.afghanjama.data.entities.Inspector
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.SizeItem
import com.afghanjama.data.entities.Tailor
import com.afghanjama.data.entities.Transaction
import com.afghanjama.data.entities.WorkCost
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class Repo(private val db: AppDatabase) {

    // =========================
    // Orders
    // =========================

    fun observeOrdersByStatus(status: String): Flow<List<Order>> =
        db.orderDao().observeByStatus(status)

    suspend fun getOrder(id: UUID): Order? =
        db.orderDao().getById(id)

    suspend fun updateOrder(order: Order) =
        db.orderDao().update(order)

    suspend fun createOrder(order: Order) =
        db.orderDao().insert(order)

    // ✅ NEW: delete order (برای حذف سفارش)
    suspend fun deleteOrder(order: Order) =
        db.orderDao().delete(order)

    // =========================
    // Finance
    // =========================

    fun observeWalletBalance(): Flow<Long> =
        db.financeDao().observeWalletBalance()

    fun observeProfitBalance(): Flow<Long> =
        db.financeDao().observeProfitBalance()

    fun observeTx(): Flow<List<Transaction>> =
        db.financeDao().observeTx()

    suspend fun income(source: String, amount: Long, note: String) {
        db.financeDao().insertTx(
            Transaction(
                type = "IN",
                source = source,
                amount = amount.coerceAtLeast(0),
                note = note
            )
        )
    }

    suspend fun spend(source: String, amount: Long, note: String) {
        db.financeDao().insertTx(
            Transaction(
                type = "OUT",
                source = source,
                amount = amount.coerceAtLeast(0),
                note = note
            )
        )
    }

    // =========================
    // Master Data
    // =========================

    fun observeFabricTypes(): Flow<List<FabricType>> =
        db.masterDataDao().observeFabricTypes()

    fun observeFabricColors(): Flow<List<FabricColor>> =
        db.masterDataDao().observeFabricColors()

    fun observeSizes(): Flow<List<SizeItem>> =
        db.masterDataDao().observeSizes()

    fun observeTailors(): Flow<List<Tailor>> =
        db.masterDataDao().observeTailors()

    fun observeInspectors(): Flow<List<Inspector>> =
        db.masterDataDao().observeInspectors()

    fun observeDesignItems(): Flow<List<DesignItem>> =
        db.masterDataDao().observeDesignItems()

    fun observeCustomers(): Flow<List<Customer>> =
        db.masterDataDao().observeCustomers()

    suspend fun addFabricType(item: FabricType) =
        db.masterDataDao().insertFabricType(item)

    suspend fun addFabricColor(item: FabricColor) =
        db.masterDataDao().insertFabricColor(item)

    suspend fun addSize(item: SizeItem) =
        db.masterDataDao().insertSize(item)

    suspend fun addTailor(item: Tailor) =
        db.masterDataDao().insertTailor(item)

    suspend fun addInspector(item: Inspector) =
        db.masterDataDao().insertInspector(item)

    suspend fun addDesign(item: DesignItem) =
        db.masterDataDao().insertDesign(item)

    suspend fun addCustomer(item: Customer) =
        db.masterDataDao().insertCustomer(item)

    // =========================
    // Work Costs (CatalogDao)
    // =========================

    fun observeWorkCosts(): Flow<List<WorkCost>> =
        db.catalogDao().observeWorkCosts()

    suspend fun insertWorkCost(item: WorkCost) =
        db.catalogDao().insertWorkCosts(listOf(item))

    // ✅ NEW: update work cost
    suspend fun updateWorkCost(item: WorkCost) =
        db.catalogDao().updateWorkCost(item)

    // ✅ NEW: delete work cost
    suspend fun deleteWorkCostById(id: Long) =
        db.catalogDao().deleteWorkCostById(id)
}

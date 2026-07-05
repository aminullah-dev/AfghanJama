package com.afghanjama.data.repo

import com.afghanjama.data.AppDatabase
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.DesignItem
import com.afghanjama.data.entities.FabricColor
import com.afghanjama.data.entities.FabricStock
import com.afghanjama.data.entities.FabricType
import com.afghanjama.data.entities.Inspector
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderCounter
import com.afghanjama.data.entities.OrderStageLog
import com.afghanjama.data.entities.SizeItem
import com.afghanjama.data.entities.Tailor
import com.afghanjama.data.entities.TailorWage
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

    fun observeAllOrders(): Flow<List<Order>> =
        db.orderDao().observeAll()

    suspend fun getOrder(id: UUID): Order? =
        db.orderDao().getById(id)

    fun observeOrderById(id: UUID): Flow<Order?> =
        db.orderDao().observeById(id)

    suspend fun updateOrder(order: Order) =
        db.orderDao().update(order)

    suspend fun createOrder(order: Order) {
        db.orderDao().insert(order)
        // ثبت اولین رکورد تایم‌لاین سفارش
        db.orderStageLogDao().insert(
            OrderStageLog(
                orderId = order.id.toString(),
                orderCode = order.orderCode,
                fromStatus = "NEW",
                toStatus = order.status
            )
        )
    }

    /**
     * تغییر مرحله سفارش از یک نقطه مرکزی:
     * زمان مرحله به‌روز و در تاریخچه مراحل ثبت می‌شود.
     */
    suspend fun changeOrderStatus(
        order: Order,
        newStatus: String,
        mutate: (Order) -> Order = { it }
    ) {
        val now = System.currentTimeMillis()
        db.orderDao().update(
            mutate(order).copy(status = newStatus, stageChangedAt = now)
        )
        db.orderStageLogDao().insert(
            OrderStageLog(
                orderId = order.id.toString(),
                orderCode = order.orderCode,
                fromStatus = order.status,
                toStatus = newStatus,
                at = now
            )
        )
    }

    fun observeStageLogs(orderId: String): Flow<List<OrderStageLog>> =
        db.orderStageLogDao().observeForOrder(orderId)

    // ✅ NEW: delete order (برای حذف سفارش)
    suspend fun deleteOrder(order: Order) =
        db.orderDao().delete(order)

    // شماره ترتیبی سفارش (برای جلوگیری از تکراری شدن کد سفارش)
    suspend fun nextOrderNumber(): Int {
        val current = db.orderCounterDao().get() ?: OrderCounter()
        db.orderCounterDao().upsert(current.copy(nextNumber = current.nextNumber + 1))
        return current.nextNumber
    }

    // =========================
    // Finance
    // =========================

    fun observeWalletBalance(): Flow<Long> =
        db.financeDao().observeWalletBalance()

    fun observeProfitBalance(): Flow<Long> =
        db.financeDao().observeProfitBalance()

    fun observeBankBalance(): Flow<Long> =
        db.financeDao().observeBankBalance()

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

    suspend fun spend(source: String, amount: Long, note: String, category: String = "") {
        db.financeDao().insertTx(
            Transaction(
                type = "OUT",
                source = source,
                amount = amount.coerceAtLeast(0),
                note = note,
                category = category
            )
        )
    }

    /** انتقال بین صندوق‌ها (کیف پول / بانک / فایده). */
    suspend fun transfer(from: String, to: String, amount: Long, note: String) {
        if (from == to || amount <= 0) return
        spend(from, amount, note)
        income(to, amount, note)
    }

    /** یکپارچه‌سازی WAL قبل از پشتیبان‌گیری فایل دیتابیس. */
    fun checkpoint() {
        db.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)")
            .use { it.moveToFirst() }
    }

    // =========================
    // Tailor Wages (کارمزد خیاط)
    // =========================

    fun observePendingWages(): Flow<List<TailorWage>> =
        db.tailorWageDao().observePending()

    fun observeAllWages(): Flow<List<TailorWage>> =
        db.tailorWageDao().observeAll()

    suspend fun addTailorWage(wage: TailorWage) =
        db.tailorWageDao().insert(wage)

    /**
     * تسویه هفتگی: همه کارمزدهای باز خیاط بسته می‌شود و مبلغ به عنوان
     * پرداخت از کیف پول در بخش مالی عمومی ثبت می‌شود.
     */
    suspend fun settleTailorWages(tailorLabel: String, total: Long) {
        db.tailorWageDao().settleForTailor(tailorLabel, System.currentTimeMillis())
        spend("WALLET", total, "تسویه کارمزد خیاط $tailorLabel")
    }

    // =========================
    // Customer Payments (حساب فروشگاه/مشتری)
    // =========================

    fun observeCustomerPayments(): Flow<List<CustomerPayment>> =
        db.customerPaymentDao().observeAll()

    suspend fun addCustomerPayment(payment: CustomerPayment) =
        db.customerPaymentDao().insert(payment)

    // =========================
    // Fabric Stock (موجودی پارچه)
    // =========================

    fun observeFabricStock(): Flow<List<FabricStock>> =
        db.fabricStockDao().observeAll()

    suspend fun getFabricStock(type: String, color: String, unit: String): FabricStock? =
        db.fabricStockDao().find(type.trim(), color.trim(), unit.trim())

    /**
     * خرید پارچه: موجودی زیاد و قیمت میانگین هر واحد به‌روزرسانی می‌شود
     * (میانگین وزنی برای بهای تمام‌شده سفارش‌های «از موجودی»).
     */
    suspend fun addFabricPurchase(
        type: String,
        color: String,
        unit: String,
        amount: Double,
        totalPrice: Long
    ) {
        if (amount <= 0.0) return
        val now = System.currentTimeMillis()
        val cur = db.fabricStockDao().find(type.trim(), color.trim(), unit.trim())
            ?: FabricStock(
                fabricType = type.trim(),
                fabricColor = color.trim(),
                fabricUnit = unit.trim(),
                amount = 0.0,
                minLevel = 0.0,
                updatedAt = now
            )
        val newAmount = cur.amount + amount
        val newAvg =
            if (newAmount > 0.0) ((cur.amount * cur.avgPrice) + totalPrice) / newAmount
            else 0.0
        db.fabricStockDao().upsert(
            cur.copy(amount = newAmount, avgPrice = newAvg, updatedAt = now)
        )
    }

    /** افزایش/کاهش موجودی؛ delta منفی برای مصرف. موجودی زیر صفر نمی‌رود. */
    suspend fun changeFabricStock(type: String, color: String, unit: String, delta: Double) {
        val now = System.currentTimeMillis()
        val cur = db.fabricStockDao().find(type.trim(), color.trim(), unit.trim())
            ?: FabricStock(
                fabricType = type.trim(),
                fabricColor = color.trim(),
                fabricUnit = unit.trim(),
                amount = 0.0,
                minLevel = 0.0,
                updatedAt = now
            )
        db.fabricStockDao().upsert(
            cur.copy(amount = (cur.amount + delta).coerceAtLeast(0.0), updatedAt = now)
        )
    }

    suspend fun setFabricMinLevel(type: String, color: String, unit: String, minLevel: Double) {
        val now = System.currentTimeMillis()
        val cur = db.fabricStockDao().find(type.trim(), color.trim(), unit.trim())
            ?: FabricStock(
                fabricType = type.trim(),
                fabricColor = color.trim(),
                fabricUnit = unit.trim(),
                amount = 0.0,
                minLevel = 0.0,
                updatedAt = now
            )
        db.fabricStockDao().upsert(
            cur.copy(minLevel = minLevel.coerceAtLeast(0.0), updatedAt = now)
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

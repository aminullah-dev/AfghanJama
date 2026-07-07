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
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderStageLog
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.data.entities.SewingAssignment
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

    suspend fun createOrder(
        order: Order,
        fabrics: List<OrderFabric> = emptyList(),
        workItems: List<OrderWorkItem> = emptyList()
    ) {
        db.orderDao().insert(order)
        // پارچه‌های چندگانه سفارش (اگر داده شده باشد)
        if (fabrics.isNotEmpty()) {
            db.orderFabricDao().insertAll(fabrics.map { it.copy(orderId = order.id.toString()) })
        }
        // خرج‌کارهای چندگانه سفارش
        if (workItems.isNotEmpty()) {
            db.orderWorkItemDao().insertAll(workItems.map { it.copy(orderId = order.id.toString()) })
        }
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

    fun observeOrderFabrics(orderId: String): Flow<List<OrderFabric>> =
        db.orderFabricDao().observeForOrder(orderId)

    fun observeOrderWorkItems(orderId: String): Flow<List<OrderWorkItem>> =
        db.orderWorkItemDao().observeForOrder(orderId)

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

    /**
     * حذف امن سفارشِ داخل انبار: پارچه‌های «از موجودی» (چندپارچه‌ای)
     * به انبار برگردانده می‌شود و بعد سفارش با جدول‌های فرزند پاک می‌شود.
     */
    suspend fun deleteOrderWithStockReturn(order: Order) {
        if (order.status == "IN_STOCK") {
            val rows = db.orderFabricDao().listForOrder(order.id.toString())
                .filter { it.source == "STOCK" }
            if (rows.isNotEmpty()) {
                rows.forEach {
                    changeFabricStock(it.fabricType, it.fabricColor, it.fabricUnit, it.amount)
                }
            } else if (order.fabricSource == "STOCK") {
                // سفارش‌های قدیمی که ردیف پارچه ندارند
                changeFabricStock(order.fabricType, order.fabricColor, order.fabricUnit, order.fabricAmount)
            }
        }
        deleteOrder(order)
    }

    // ✅ NEW: delete order (برای حذف سفارش) + پاک‌کردن جدول‌های فرزند
    suspend fun deleteOrder(order: Order) {
        db.orderFabricDao().deleteForOrder(order.id.toString())
        db.orderWorkItemDao().deleteForOrder(order.id.toString())
        db.sewingAssignmentDao().deleteForOrder(order.id.toString())
        db.orderDao().delete(order)
    }

    // =========================
    // Sewing Assignments (تحویل بخشی به خیاط)
    // =========================

    fun observeAssignmentsForOrder(orderId: String): Flow<List<SewingAssignment>> =
        db.sewingAssignmentDao().observeForOrder(orderId)

    fun observeAssignmentsInProgress(): Flow<List<SewingAssignment>> =
        db.sewingAssignmentDao().observeInProgress()

    fun observeAllAssignments(): Flow<List<SewingAssignment>> =
        db.sewingAssignmentDao().observeAll()

    /**
     * تحویل بخشی از سفارش به یک خیاط. اگر مجموع تحویل‌شده‌ها به تعداد
     * کل سفارش برسد، وضعیت سفارش به «دوخت» می‌رود.
     */
    suspend fun handoutToTailor(order: Order, tailorLabel: String, qty: Int, unitWage: Long) {
        db.sewingAssignmentDao().insert(
            SewingAssignment(
                orderId = order.id.toString(),
                orderCode = order.orderCode,
                tailorLabel = tailorLabel,
                qty = qty,
                unitWage = unitWage,
                status = "SEWING"
            )
        )
        val handed = db.sewingAssignmentDao().listForOrder(order.id.toString()).sumOf { it.qty }
        val label = summarizeTailors(order.id.toString())
        if (handed >= order.qty && order.status == "CUT_DONE") {
            changeOrderStatus(order, "SEWING") { it.copy(assignedTailor = label) }
        } else {
            db.orderDao().update(order.copy(assignedTailor = label))
        }
    }

    /** لغو یک تحویل (اصلاح اشتباه) — فقط تا وقتی دوخت تمام نشده. */
    suspend fun cancelAssignment(assignmentId: Long) {
        val a = db.sewingAssignmentDao().getById(assignmentId) ?: return
        if (a.status != "SEWING") return
        db.sewingAssignmentDao().deleteById(assignmentId)
        val order = db.orderDao().getById(java.util.UUID.fromString(a.orderId)) ?: return
        // اگر سفارش به دوخت رفته بود ولی حالا تحویل ناقص شد، به آماده‌دوخت برگردد
        val handed = db.sewingAssignmentDao().listForOrder(a.orderId).sumOf { it.qty }
        val label = summarizeTailors(a.orderId)
        if (handed < order.qty && order.status == "SEWING") {
            changeOrderStatus(order, "CUT_DONE") { it.copy(assignedTailor = label) }
        } else {
            db.orderDao().update(order.copy(assignedTailor = label))
        }
    }

    /**
     * دوخت یک تحویل تمام شد: کارمزد آن خیاط ثبت و تحویل بسته می‌شود.
     * اگر همه تحویل‌ها تمام و سفارش کامل تحویل شده باشد، به «نظارت» می‌رود.
     */
    suspend fun completeAssignment(assignmentId: Long) {
        val a = db.sewingAssignmentDao().getById(assignmentId) ?: return
        if (a.status != "SEWING") return
        db.sewingAssignmentDao().update(a.copy(status = "DONE", doneAt = System.currentTimeMillis()))

        if (a.totalWage > 0) {
            db.tailorWageDao().insert(
                TailorWage(
                    orderId = a.orderId,
                    orderCode = a.orderCode,
                    tailorLabel = a.tailorLabel,
                    amount = a.totalWage,
                    assignmentId = a.id
                )
            )
        }

        val order = db.orderDao().getById(java.util.UUID.fromString(a.orderId)) ?: return
        val all = db.sewingAssignmentDao().listForOrder(a.orderId)
        val handed = all.sumOf { it.qty }
        val allDone = all.isNotEmpty() && all.all { it.status == "DONE" }
        if (allDone && handed >= order.qty && order.status == "SEWING") {
            changeOrderStatus(order, "REVIEW")
        }
    }

    /** خلاصهٔ خیاط‌های یک سفارش برای نمایش در فیلد assignedTailor. */
    private suspend fun summarizeTailors(orderId: String): String {
        val list = db.sewingAssignmentDao().listForOrder(orderId)
        return when {
            list.isEmpty() -> ""
            list.size == 1 -> list.first().tailorLabel
            else -> "${list.size} خیاط"
        }
    }

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

    /** حذف تراکنش مالی (اصلاح اشتباه) — موجودی صندوق‌ها بازمحاسبه می‌شود. */
    suspend fun deleteTx(id: UUID) =
        db.financeDao().deleteTx(id)

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

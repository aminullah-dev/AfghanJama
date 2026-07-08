package com.afghanjama.data.repo

import com.afghanjama.data.AppDatabase
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.CustomerMeasurement
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.DesignItem
import com.afghanjama.data.entities.FabricColor
import com.afghanjama.data.CodeGen
import com.afghanjama.data.entities.FabricType
import com.afghanjama.data.entities.FinishedSale
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.data.entities.Inspector
import com.afghanjama.data.entities.MaterialStock
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderCounter
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderStageLog
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.data.entities.PurchaseInvoice
import com.afghanjama.data.entities.PurchaseItem
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.entities.SizeItem
import com.afghanjama.data.entities.StockMovement
import com.afghanjama.data.entities.SupplierLedger
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
            val allRows = db.orderFabricDao().listForOrder(order.id.toString())
            // پارچه‌های «از موجودی» با نام «نوع رنگ» به انبار مواد برمی‌گردند
            val stockRows = allRows.filter { it.source == "STOCK" }
            // موادِ مصرفی «از انبار عمومی» با نام خودشان به انبار مواد برمی‌گردند
            val materialRows = allRows.filter { it.source == "MATERIAL" }
            when {
                stockRows.isNotEmpty() || materialRows.isNotEmpty() -> {
                    stockRows.forEach {
                        changeMaterialStock(fabricMaterialName(it.fabricType, it.fabricColor), it.fabricUnit, it.amount,
                            reason = "برگشت به انبار", note = "حذف سفارش ${order.orderCode}")
                    }
                    materialRows.forEach {
                        changeMaterialStock(it.fabricType, it.fabricUnit, it.amount,
                            reason = "برگشت به انبار", note = "حذف سفارش ${order.orderCode}")
                    }
                }
                order.fabricSource == "STOCK" -> {
                    // سفارش‌های قدیمی که ردیف پارچه ندارند
                    changeMaterialStock(fabricMaterialName(order.fabricType, order.fabricColor), order.fabricUnit, order.fabricAmount,
                        reason = "برگشت به انبار", note = "حذف سفارش ${order.orderCode}")
                }
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
    // Fabric as Material (پارچه به‌عنوان یک نوع ماده در انبار عمومی)
    // =========================

    /**
     * نام یکتای پارچه در انبار مواد از «نوع + رنگ» ساخته می‌شود؛ پارچه
     * دیگر انبار جدا ندارد و مثل هر مادهٔ دیگر در انبار عمومی است.
     */
    fun fabricMaterialName(type: String, color: String): String =
        listOf(type.trim(), color.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    // =========================
    // Material Stock (انبار عمومی مواد خام)
    // =========================

    fun observeMaterialStock(): Flow<List<MaterialStock>> =
        db.materialStockDao().observeAll()

    fun observeStockMovements(): Flow<List<StockMovement>> =
        db.stockMovementDao().observeRecent()

    suspend fun getMaterialStock(name: String, unit: String): MaterialStock? =
        db.materialStockDao().find(name.trim(), unit.trim())

    /** ثبت یک ردیفِ گردش انبار (کاردکس). */
    private suspend fun logMovement(name: String, unit: String, delta: Double, reason: String, note: String) {
        db.stockMovementDao().insert(
            StockMovement(name = name.trim(), unit = unit.trim(), delta = delta, reason = reason, note = note)
        )
    }

    /** افزودن خرید یک قلم به انبار با میانگین وزنی قیمت. */
    suspend fun addMaterialPurchase(name: String, unit: String, amount: Double, totalPrice: Long, note: String = "") {
        if (amount <= 0.0) return
        val now = System.currentTimeMillis()
        val cur = db.materialStockDao().find(name.trim(), unit.trim())
            ?: MaterialStock(name = name.trim(), unit = unit.trim(), amount = 0.0, updatedAt = now)
        val newAmount = cur.amount + amount
        val newAvg =
            if (newAmount > 0.0) ((cur.amount * cur.avgPrice) + totalPrice) / newAmount
            else 0.0
        db.materialStockDao().upsert(cur.copy(amount = newAmount, avgPrice = newAvg, updatedAt = now))
        logMovement(name, unit, amount, "خرید", note)
    }

    /**
     * افزایش/کاهش موجودی یک قلم؛ delta منفی برای مصرف. زیر صفر نمی‌رود.
     * هر تغییر با «دلیل» در کاردکس ثبت می‌شود (رد حسابرسی).
     */
    suspend fun changeMaterialStock(
        name: String,
        unit: String,
        delta: Double,
        reason: String = "اصلاح",
        note: String = ""
    ) {
        if (delta == 0.0) return
        val now = System.currentTimeMillis()
        val cur = db.materialStockDao().find(name.trim(), unit.trim())
            ?: MaterialStock(name = name.trim(), unit = unit.trim(), amount = 0.0, updatedAt = now)
        db.materialStockDao().upsert(
            cur.copy(amount = (cur.amount + delta).coerceAtLeast(0.0), updatedAt = now)
        )
        logMovement(name, unit, delta, reason, note)
    }

    suspend fun setMaterialMinLevel(name: String, unit: String, minLevel: Double) {
        val now = System.currentTimeMillis()
        val cur = db.materialStockDao().find(name.trim(), unit.trim())
            ?: MaterialStock(name = name.trim(), unit = unit.trim(), amount = 0.0, updatedAt = now)
        db.materialStockDao().upsert(cur.copy(minLevel = minLevel.coerceAtLeast(0.0), updatedAt = now))
    }

    // =========================
    // Procurement (خرید مواد خام — فاکتور چند قلمی)
    // =========================

    fun observePurchaseInvoices(): Flow<List<PurchaseInvoice>> =
        db.procurementDao().observeInvoices()

    fun observePurchaseItems(invoiceId: String): Flow<List<PurchaseItem>> =
        db.procurementDao().observeItems(invoiceId)

    /**
     * ثبت یک فاکتور خرید: فاکتور و اقلامش ذخیره، هر قلم وارد انبار و
     * مبلغ کل از منبع انتخابی پرداخت می‌شود. صندوق پرداخت‌کننده باید از
     * قبل موجودی کافی داشته باشد (کنترل در ViewModel).
     */
    suspend fun recordPurchaseInvoice(invoice: PurchaseInvoice, items: List<PurchaseItem>) {
        db.procurementDao().insertInvoice(invoice)
        db.procurementDao().insertItems(items)
        items.forEach { addMaterialPurchase(it.name, it.unit, it.qty, it.total) }
        if (invoice.total > 0) {
            when (invoice.paySource) {
                "CUSTOMER" -> { /* بعداً */ }
                // نسیه: پول کم نمی‌شود؛ به‌عنوان بدهی فروشنده ثبت می‌شود
                "CREDIT" -> recordSupplierCredit(
                    invoice.supplier.ifBlank { "نامشخص" },
                    invoice.total,
                    "خرید نسیه ${invoice.code}"
                )
                else -> spend(invoice.paySource, invoice.total, "خرید مواد ${invoice.code}", category = "خرید مواد")
            }
        }
    }

    // =========================
    // Supplier credit (قرض/نسیهٔ فروشنده)
    // =========================

    fun observeSupplierLedger(): Flow<List<SupplierLedger>> =
        db.supplierDao().observeAll()

    /** ثبت بدهی جدید به فروشنده (خرید نسیه). */
    suspend fun recordSupplierCredit(supplier: String, amount: Long, note: String) {
        if (amount <= 0) return
        db.supplierDao().insert(
            SupplierLedger(supplier = supplier.trim(), amount = amount, type = "CREDIT", note = note)
        )
    }

    /** تسویهٔ (بخشی از) بدهی فروشنده: پول از صندوق کم و بدهی کاهش می‌یابد. */
    suspend fun settleSupplier(supplier: String, amount: Long, paySource: String, note: String) {
        if (amount <= 0) return
        spend(paySource, amount, note.ifBlank { "تسویه قرض $supplier" }, category = "تسویه قرض")
        db.supplierDao().insert(
            SupplierLedger(supplier = supplier.trim(), amount = amount, type = "PAYMENT", note = note)
        )
    }

    // =========================
    // Finished Goods (انبار محصول نهایی + فروش جزئی)
    // =========================

    fun observeFinishedStock(): Flow<List<FinishedStock>> =
        db.finishedStockDao().observeAvailable()

    fun observeFinishedSales(): Flow<List<FinishedSale>> =
        db.finishedStockDao().observeSales()

    /** افزودن محصول تولیدشده به انبار با میانگین وزنی بهای تمام‌شده. */
    suspend fun addFinishedStock(name: String, size: String, qty: Int, avgCostPerPiece: Long) {
        if (qty <= 0) return
        val now = System.currentTimeMillis()
        val cur = db.finishedStockDao().find(name.trim(), size.trim())
            ?: FinishedStock(name = name.trim(), size = size.trim(), qty = 0, updatedAt = now)
        val newQty = cur.qty + qty
        val newAvg =
            if (newQty > 0) ((cur.qty * cur.avgCost) + (qty * avgCostPerPiece)) / newQty
            else 0L
        db.finishedStockDao().upsert(cur.copy(qty = newQty, avgCost = newAvg, updatedAt = now))
    }

    /**
     * تحویل یک سفارشِ آمادهٔ فروش به انبار محصول نهایی:
     * تعداد سفارش با بهای تمام‌شدهٔ هر عدد وارد انبار می‌شود و وضعیت
     * سفارش به STORED (بایگانی تولید) تغییر می‌کند.
     */
    suspend fun depositOrderToFinished(order: Order) {
        val perPieceCost = if (order.qty > 0)
            (order.fabricPrice + order.workCost) / order.qty else 0L
        addFinishedStock(order.designTitle, order.size, order.qty, perPieceCost)
        changeOrderStatus(order, OrderStatus.STORED.name)
    }

    /**
     * فروش جزئی از انبار محصول نهایی: موجودی کم، درآمد وارد کیف پول و
     * سود به فایده منتقل و سابقهٔ فروش ثبت می‌شود.
     */
    suspend fun sellFinished(
        item: FinishedStock,
        qty: Int,
        unitPrice: Long,
        customerName: String
    ): Boolean {
        if (qty <= 0 || qty > item.qty || unitPrice <= 0) return false
        val now = System.currentTimeMillis()
        val revenue = qty * unitPrice
        val cost = qty * item.avgCost
        val profit = revenue - cost

        db.finishedStockDao().upsert(item.copy(qty = item.qty - qty, updatedAt = now))

        val code = CodeGen.makePurchaseCode().replaceFirst("KH", "FR")
        db.finishedStockDao().insertSale(
            FinishedSale(
                code = code,
                productName = item.name,
                size = item.size,
                qty = qty,
                unitPrice = unitPrice,
                total = revenue,
                cost = cost,
                customerName = customerName.trim()
            )
        )

        income("WALLET", revenue, "فروش $qty عدد «${item.name}» ($code)")
        addCustomerPayment(
            CustomerPayment(
                orderId = "FINISHED",
                customerName = customerName.trim(),
                amount = revenue,
                source = "SALE",
                note = "فروش $qty عدد «${item.name}» از انبار محصول"
            )
        )
        if (profit > 0L) {
            spend("WALLET", profit, "انتقال سود فروش «${item.name}» به فایده")
            income("PROFIT", profit, "سود فروش «${item.name}»")
        }
        return true
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
    // Customer measurements (اندازه‌های مشتری)
    // =========================

    fun observeMeasurements(customerId: Long): Flow<List<CustomerMeasurement>> =
        db.customerMeasurementDao().observeForCustomer(customerId)

    suspend fun upsertMeasurement(row: CustomerMeasurement) =
        db.customerMeasurementDao().upsert(row.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteMeasurement(id: Long) =
        db.customerMeasurementDao().deleteById(id)

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

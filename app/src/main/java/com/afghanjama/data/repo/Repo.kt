package com.afghanjama.data.repo

import com.afghanjama.data.AppDatabase
import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.CustomerMeasurement
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.CuttingRecord
import com.afghanjama.data.entities.DesignItem
import com.afghanjama.data.entities.Document
import com.afghanjama.data.entities.FabricColor
import com.afghanjama.data.CodeGen
import com.afghanjama.data.entities.FabricType
import com.afghanjama.data.entities.FinishedSale
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.data.entities.Accounts
import com.afghanjama.data.entities.AuditLog
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
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.data.entities.PurchaseInvoice
import com.afghanjama.data.entities.PurchaseItem
import com.afghanjama.data.entities.QcRecord
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.entities.SizeItem
import com.afghanjama.data.entities.StockMovement
import com.afghanjama.data.entities.SupplierLedger
import com.afghanjama.data.entities.Tailor
import com.afghanjama.data.entities.TailorWage
import com.afghanjama.data.entities.Transaction
import com.afghanjama.data.entities.WorkCost
import com.afghanjama.util.CurrentUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        audit("ثبت سفارش تولید", order.orderCode)
        // بدهیِ مشتری بابتِ این سفارش → بدهکارِ حساب مشتری در دفتر کل
        // (پرداخت‌های او بستانکار می‌شوند؛ مانده = طلبِ ما از مشتری).
        if (order.customerName.isNotBlank() && order.agreedPrice > 0) {
            postLedger(
                "CUSTOMER", order.customerName, order.agreedPrice, 0,
                "SALE_BILLING", order.orderCode, "بدهی بابت سفارش"
            )
        }
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

    // =========================
    // Cutting record (رکورد برش)
    // =========================

    fun observeCuttingForOrder(orderId: String): Flow<List<CuttingRecord>> =
        db.cuttingRecordDao().observeForOrder(orderId)

    // =========================
    // Quality control (کنترل کیفیت / نظارت)
    // =========================

    fun observeQcForOrder(orderId: String): Flow<List<QcRecord>> =
        db.qcRecordDao().observeForOrder(orderId)

    /** همهٔ رکوردهای نظارت — برای کارنامهٔ کارکنانِ تولید. */
    fun observeAllQc(): Flow<List<QcRecord>> =
        db.qcRecordDao().observeAll()

    /**
     * تأیید کیفیت: رکورد QC ثبت و سفارش مستقیم واردِ انبار محصول می‌شود.
     * تصمیمِ فروش (مشتری/قیمت/تخفیف) در انبار محصول توسط بخش فروش گرفته
     * می‌شود — نظارت فقط کیفیت را تأیید می‌کند.
     */
    suspend fun approveQc(order: Order, inspector: String, note: String) {
        db.qcRecordDao().insert(
            QcRecord(
                orderId = order.id.toString(),
                orderCode = order.orderCode,
                inspector = inspector.trim(),
                result = "APPROVED",
                note = note.trim()
            )
        )
        depositOrderToFinished(
            order.copy(
                assignedInspector = inspector.trim().ifBlank { order.assignedInspector },
                reviewed = true
            )
        )
        audit("تأیید نظارت", "${order.orderCode} — $inspector")
    }

    /** برگشت برای اصلاح: مشکل ثبت و سفارش به مرحلهٔ دوخت برمی‌گردد. */
    suspend fun rejectQc(order: Order, inspector: String, problem: String) {
        db.qcRecordDao().insert(
            QcRecord(
                orderId = order.id.toString(),
                orderCode = order.orderCode,
                inspector = inspector.trim(),
                result = "REJECTED",
                problem = problem.trim()
            )
        )
        changeOrderStatus(order, OrderStatus.SEWING.name) {
            it.copy(assignedInspector = inspector.trim().ifBlank { it.assignedInspector }, reviewed = false)
        }
        audit("رد نظارت (برگشت به دوخت)", "${order.orderCode} — $problem")
    }

    /**
     * ثبت رکورد برش و انتقال سفارش به «برش تمام». کسرِ موادِ سفارش دقیقاً
     * همین‌جا (لحظهٔ برش) از انبار انجام می‌شود — چون مصرفِ واقعی در برش
     * اتفاق می‌افتد، نه هنگام ثبت سفارش. اگر مواد قبلاً کسر شده باشند
     * (سفارش‌های مدل قدیم) دوباره کسر نمی‌شود.
     */
    suspend fun completeCutting(order: Order, cutter: String, pieces: Int, waste: String, note: String) {
        db.cuttingRecordDao().insert(
            CuttingRecord(
                orderId = order.id.toString(),
                orderCode = order.orderCode,
                cutter = cutter.trim(),
                pieces = pieces,
                waste = waste.trim(),
                note = note.trim()
            )
        )
        if (!order.materialsConsumed) {
            db.orderFabricDao().listForOrder(order.id.toString())
                .filter { it.source == "MATERIAL" || it.source == "STOCK" }
                .forEach { f ->
                    val matName =
                        if (f.source == "STOCK") fabricMaterialName(f.fabricType, f.fabricColor)
                        else f.fabricType
                    changeMaterialStock(
                        matName, f.fabricUnit, -f.amount,
                        reason = "مصرف برش", note = "سفارش ${order.orderCode}"
                    )
                }
            // ژورنال: ارزشِ موادِ مصرفی از انبار به «کار در جریان» می‌رود
            if (order.fabricPrice > 0) {
                postJournal(
                    "مصرف مواد در برش ${order.orderCode}", "CUTTING", order.orderCode,
                    listOf(
                        jl(Accounts.WIP, debit = order.fabricPrice),
                        jl(Accounts.MATERIALS, credit = order.fabricPrice)
                    )
                )
            }
        }
        changeOrderStatus(order, OrderStatus.CUT_DONE.name) { it.copy(materialsConsumed = true) }
        audit("ثبت برش", "${order.orderCode} — ${pieces} دست")
    }

    /**
     * حذف امن سفارش: اگر موادِ سفارش واقعاً از انبار کسر شده باشد
     * (materialsConsumed) پارچه/مواد به انبار برگردانده می‌شود و بعد
     * سفارش با جدول‌های فرزند پاک می‌شود. اگر هنوز کسر نشده (سفارشِ
     * پیش از برش) چیزی برنمی‌گردد چون چیزی از انبار کم نشده بود.
     */
    suspend fun deleteOrderWithStockReturn(order: Order) {
        if (order.materialsConsumed) {
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
        audit("حذف سفارش", order.orderCode)
        // بدهیِ مشتری بابتِ این سفارش (SALE_BILLING هنگام ثبت) خنثی می‌شود
        // تا با حذفِ سفارش، ماندهٔ مشتری در دفتر کل متورم نماند.
        if (order.customerName.isNotBlank() && order.agreedPrice > 0) {
            postLedger(
                "CUSTOMER", order.customerName, 0, order.agreedPrice,
                "SALE_CANCEL", order.orderCode, "حذف سفارش"
            )
        }
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
        audit("تحویل به خیاط", "${order.orderCode} → $tailorLabel (${qty} عدد)")
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
    suspend fun completeAssignment(assignmentId: Long, quality: String = "", deliveredQty: Int? = null) {
        val a = db.sewingAssignmentDao().getById(assignmentId) ?: return
        if (a.status != "SEWING") return

        // تحویل جزئی: هر مقدار که دوخته شده تحویل می‌شود؛ باقی‌مانده «در حال دوخت» می‌ماند
        val delivered = (deliveredQty ?: a.qty).coerceIn(1, a.qty)
        if (delivered < a.qty) {
            db.sewingAssignmentDao().insert(
                a.copy(
                    id = 0,
                    qty = a.qty - delivered,
                    status = "SEWING",
                    doneAt = null,
                    quality = "",
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        val done = a.copy(qty = delivered, status = "DONE", doneAt = System.currentTimeMillis(), quality = quality.trim())
        db.sewingAssignmentDao().update(done)

        if (done.totalWage > 0) {
            db.tailorWageDao().insert(
                TailorWage(
                    orderId = a.orderId,
                    orderCode = a.orderCode,
                    tailorLabel = a.tailorLabel,
                    amount = done.totalWage,
                    assignmentId = a.id
                )
            )
            // آینه در دفتر کل: کارمزدِ کسب‌شده → بستانکارِ حساب خیاط
            postLedger("TAILOR", a.tailorLabel, 0, done.totalWage, "WAGE", a.orderCode)
            // ژورنال: دستمزدِ دوخت واردِ بهای «کار در جریان»؛ بدهی به خیاط
            postJournal(
                "کارمزد دوخت ${a.orderCode} — ${a.tailorLabel}", "WAGE", a.orderCode,
                listOf(
                    jl(Accounts.WIP, debit = done.totalWage),
                    jl(Accounts.WAGES_PAYABLE, credit = done.totalWage)
                )
            )
        }

        val orderFetched = db.orderDao().getById(java.util.UUID.fromString(a.orderId)) ?: return
        // دستمزد این تحویل به بهای تمام‌شدهٔ سفارش اضافه می‌شود
        val order = orderFetched.copy(sewingCost = orderFetched.sewingCost + done.totalWage.coerceAtLeast(0))
        db.orderDao().update(order)

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
    suspend fun deleteTx(id: UUID) {
        db.financeDao().deleteTx(id)
        audit("حذف تراکنش مالی", id.toString().take(8))
    }

    /** انتقال بین صندوق‌ها (کیف پول / بانک / فایده). */
    suspend fun transfer(from: String, to: String, amount: Long, note: String) {
        if (from == to || amount <= 0) return
        spend(from, amount, note)
        income(to, amount, note)
        audit("انتقال بین صندوق‌ها", "$from → $to — $amount ؋")
        postJournal(
            note, "TRANSFER", "",
            listOf(
                jl(Accounts.box(to), debit = amount),
                jl(Accounts.box(from), credit = amount)
            )
        )
    }

    /** دریافتیِ دستی از مشتری: نقد + حساب مشتری + دفتر کل + ژورنال، یک‌جا. */
    suspend fun recordCustomerReceipt(name: String, amount: Long, note: String = "") {
        if (name.isBlank() || amount <= 0) return
        val n = note.ifBlank { "دریافتی از $name" }
        income("WALLET", amount, n)
        audit("دریافت از مشتری", "$name — $amount ؋")
        addCustomerPayment(
            CustomerPayment(orderId = "", customerName = name, amount = amount, source = "MANUAL", note = n)
        )
        postJournal(
            n, "CUSTOMER_RECEIPT", "",
            listOf(
                jl(Accounts.CASH, debit = amount),
                jl(Accounts.RECEIVABLE, credit = amount)
            )
        )
    }

    /** برگشتیِ فروش: خروجِ نقدِ مسترد + ژورنالِ برگشتِ درآمد. */
    suspend fun recordSaleRefund(orderCode: String, refund: Long) {
        if (refund <= 0) return
        spend("WALLET", refund, "برگشتی فروش سفارش $orderCode", category = "برگشتی فروش")
        audit("برگشتی فروش", "$orderCode — $refund ؋")
        postJournal(
            "برگشتی فروش $orderCode", "SALE_RETURN", orderCode,
            listOf(
                jl(Accounts.SALES, debit = refund),
                jl(Accounts.CASH, credit = refund)
            )
        )
    }

    /** هزینهٔ عمومی (کرایه، برق، معاش...): خروجِ نقد + ثبتِ ژورنالِ هزینه. */
    suspend fun recordExpense(source: String, category: String, amount: Long, note: String) {
        if (amount <= 0) return
        spend(source, amount, note, category = category)
        audit("ثبت هزینه", "$category — $amount ؋")
        postJournal(
            note.ifBlank { "هزینه: $category" }, "EXPENSE", "",
            listOf(
                jl(Accounts.EXPENSES, debit = amount),
                jl(Accounts.box(source), credit = amount)
            )
        )
    }

    /** ورود/خروجِ دستیِ نقد (اصلاحِ صندوق): در برابرِ سایر درآمد/هزینه. */
    suspend fun recordManualCash(source: String, amount: Long, isIn: Boolean, note: String) {
        if (amount <= 0) return
        if (isIn) {
            income(source, amount, note)
            postJournal(
                note.ifBlank { "دریافت دستی" }, "MANUAL_CASH", "",
                listOf(
                    jl(Accounts.box(source), debit = amount),
                    jl(Accounts.OTHER_INCOME, credit = amount)
                )
            )
        } else {
            spend(source, amount, note)
            postJournal(
                note.ifBlank { "پرداخت دستی" }, "MANUAL_CASH", "",
                listOf(
                    jl(Accounts.EXPENSES, debit = amount),
                    jl(Accounts.box(source), credit = amount)
                )
            )
        }
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

    suspend fun addTailorWage(wage: TailorWage) {
        db.tailorWageDao().insert(wage)
        postLedger("TAILOR", wage.tailorLabel, 0, wage.amount, "WAGE", wage.orderCode)
    }

    /**
     * تسویه هفتگی: همه کارمزدهای باز خیاط بسته می‌شود و مبلغ به عنوان
     * پرداخت از کیف پول در بخش مالی عمومی ثبت می‌شود.
     */
    suspend fun settleTailorWages(tailorLabel: String, total: Long) {
        db.tailorWageDao().settleForTailor(tailorLabel, System.currentTimeMillis())
        spend("WALLET", total, "تسویه کارمزد خیاط $tailorLabel")
        // آینه در دفتر کل: پرداختِ کارمزد → بدهکارِ حساب خیاط (کاهش طلب او)
        postLedger("TAILOR", tailorLabel, total, 0, "WAGE_PAID", note = "تسویه کارمزد")
        // رسیدِ تسویهٔ کارمزد
        createDocument("WAGE_RECEIPT", tailorLabel, total, note = "تسویه کارمزد دوخت")
        audit("تسویه کارمزد خیاط", "$tailorLabel — $total ؋")
        // ژورنال: کاهشِ بدهیِ کارمزد در برابر خروجِ نقد
        postJournal(
            "تسویه کارمزد $tailorLabel", "WAGE_PAID", "",
            listOf(
                jl(Accounts.WAGES_PAYABLE, debit = total),
                jl(Accounts.CASH, credit = total)
            )
        )
    }

    // =========================
    // Customer Payments (حساب فروشگاه/مشتری)
    // =========================

    fun observeCustomerPayments(): Flow<List<CustomerPayment>> =
        db.customerPaymentDao().observeAll()

    suspend fun addCustomerPayment(payment: CustomerPayment) {
        db.customerPaymentDao().insert(payment)
        // آینه در دفتر کل: پرداختِ مشتری → بستانکارِ حساب مشتری.
        // اگر نامِ مشتری خالی است، از سفارشِ مرتبط پیدا می‌شود؛ پرداختِ
        // بی‌نامِ بدونِ سفارش آینه نمی‌شود (طرفِ حساب ندارد). مبلغِ منفی
        // (برگشتی) به‌صورت بدهکار ثبت می‌شود تا سطرِ منفی در دفتر نیاید.
        val name = payment.customerName.ifBlank {
            runCatching {
                db.orderDao().getById(java.util.UUID.fromString(payment.orderId))?.customerName
            }.getOrNull().orEmpty()
        }
        if (name.isBlank()) return
        if (payment.amount >= 0) {
            postLedger("CUSTOMER", name, 0, payment.amount,
                "CUSTOMER_" + payment.source, payment.orderId, payment.note)
        } else {
            postLedger("CUSTOMER", name, -payment.amount, 0,
                "CUSTOMER_" + payment.source, payment.orderId, payment.note)
        }
    }

    // =========================
    // Universal Ledger (دفتر کلِ یکپارچه)
    // =========================

    fun observeParties(): Flow<List<Party>> =
        db.ledgerDao().observeParties()

    fun observeLedgerBalances(): Flow<List<com.afghanjama.data.dao.PartyBalance>> =
        db.ledgerDao().observeBalances()

    fun observeLedgerEntries(type: String, name: String): Flow<List<LedgerEntry>> =
        db.ledgerDao().observeEntriesForParty(type, name)

    fun observeAllLedgerEntries(): Flow<List<LedgerEntry>> =
        db.ledgerDao().observeAllEntries()

    // =========================
    // Audit log (لاگ حسابرسی: چه کسی، چه کاری، کِی)
    // =========================

    fun observeAudit(): Flow<List<AuditLog>> = db.auditDao().observeRecent()

    private suspend fun audit(action: String, detail: String = "") {
        db.auditDao().insert(
            AuditLog(
                user = CurrentUser.name,
                role = CurrentUser.role,
                action = action,
                detail = detail
            )
        )
    }

    // =========================
    // Double-entry journal (ژورنالِ حسابداری دوطرفه)
    // =========================

    fun observeAccountBalances(): Flow<List<com.afghanjama.data.dao.AccountBalance>> =
        db.journalDao().observeAccountBalances()

    /** ماندهٔ حساب‌ها در یک بازه — برای صورتِ سود و زیانِ دوره. */
    fun observeAccountBalancesBetween(from: Long, to: Long): Flow<List<com.afghanjama.data.dao.AccountBalance>> =
        db.journalDao().observeAccountBalancesBetween(from, to)

    fun observeJournalEntries(): Flow<List<JournalEntry>> =
        db.journalDao().observeRecentEntries()

    /** یک سطرِ سند (کمکی برای خوانایی). */
    private fun jl(account: String, debit: Long = 0, credit: Long = 0) =
        JournalLine(entryId = 0, account = account, debit = debit, credit = credit)

    /**
     * ثبتِ سندِ دوطرفه. فقط وقتی می‌نویسد که تراز باشد (جمع بدهکار =
     * جمع بستانکار و بزرگ‌تر از صفر)؛ سندِ نامتراز بی‌صدا رد می‌شود تا
     * عملیاتِ کارگاه هرگز به‌خاطر حسابداری متوقف نشود.
     */
    private suspend fun postJournal(
        memo: String,
        refType: String,
        refId: String,
        lines: List<JournalLine>
    ) {
        val rows = lines.filter { it.debit > 0 || it.credit > 0 }
        val dr = rows.sumOf { it.debit }
        val cr = rows.sumOf { it.credit }
        if (dr <= 0 || dr != cr) return
        val entryId = db.journalDao().insertEntry(
            JournalEntry(memo = memo, refType = refType, refId = refId)
        )
        db.journalDao().insertLines(rows.map { it.copy(entryId = entryId) })
    }

    // =========================
    // Documents (اسنادِ مالی با شمارهٔ یکتا)
    // =========================

    fun observeDocuments(): Flow<List<Document>> =
        db.documentDao().observeAll()

    private fun docPrefix(type: String): String = when (type) {
        "PURCHASE" -> "KH"           // خرید
        "SALE" -> "FR"               // فروش
        "SUPPLIER_PAYMENT" -> "PF"   // پرداخت به فروشنده
        "WAGE_RECEIPT" -> "KM"       // کارمزد
        "CUSTOMER_RECEIPT" -> "DR"   // دریافت از مشتری
        "RETURN" -> "BR"             // برگشت
        "PROFORMA" -> "PP"           // پیش‌فاکتور
        "PAYMENT" -> "PY"            // پرداختِ دستی
        "RECEIPT" -> "RC"            // دریافتِ دستی
        else -> "SND"
    }

    /**
     * تولید یک سندِ مالی با شمارهٔ یکتا (پیشوندِ نوع + شمارهٔ سراسری).
     * شماره از idِ خودافزای سطر ساخته می‌شود تا دو ثبتِ هم‌زمان هرگز به
     * شمارهٔ تکراری (و کرشِ ایندکس یکتا) نخورند.
     */
    suspend fun createDocument(
        type: String,
        partyName: String,
        amount: Long,
        refId: String = "",
        note: String = ""
    ) {
        if (amount <= 0) return
        val id = db.documentDao().insert(
            Document(
                number = "TMP-" + java.util.UUID.randomUUID(),
                type = type,
                partyName = partyName.trim(), amount = amount,
                refId = refId, note = note
            )
        )
        db.documentDao().setNumber(id, docPrefix(type) + "-" + id.toString().padStart(5, '0'))
    }

    /**
     * ثبتِ دستیِ پرداخت/دریافتِ نقدی روی حسابِ یک طرف: هم دفتر کل، هم
     * صندوق، هم یک سند به‌روز می‌شود. isPayment=true یعنی ما پرداخت کردیم
     * (خروجِ نقد → بدهکارِ حساب)، false یعنی دریافت کردیم (ورودِ نقد →
     * بستانکارِ حساب).
     */
    /** موجودیِ فعلیِ یک صندوق (برای کنترل قبل از پرداخت). */
    private suspend fun balanceOf(paySource: String): Long = when (paySource) {
        "BANK" -> observeBankBalance().first()
        "PROFIT" -> observeProfitBalance().first()
        else -> observeWalletBalance().first()
    }

    /** @return false اگر موجودیِ صندوق برای پرداخت کافی نبود (چیزی ثبت نمی‌شود). */
    suspend fun recordManualLedger(
        type: String,
        name: String,
        amount: Long,
        isPayment: Boolean,
        paySource: String = "WALLET",
        note: String = ""
    ): Boolean {
        if (amount <= 0 || name.isBlank()) return false
        if (isPayment) {
            // مثل صفحهٔ قدیمِ تسویه: پرداختِ بیش از موجودیِ صندوق ممنوع.
            if (balanceOf(paySource) < amount) return false
            // پرداخت به فروشنده از مسیرِ رسمیِ تسویه می‌رود تا دفترِ قرضِ فروشنده
            // (و در نتیجه دفتر کل و صندوق و سند) یک‌جا و سازگار به‌روز شود.
            if (type == "SUPPLIER") {
                settleSupplier(name, amount, paySource, note.ifBlank { "تسویه قرض $name" })
                return true
            }
            // اگر مبلغ، کلِ کارمزدِ بازِ خیاط را بپوشاند، رکوردها تسویه و یادآوری
            // قطع می‌شود؛ پرداختِ کمتر، پرداختِ جزئیِ عادی است (رکوردها باز می‌مانند).
            if (type == "TAILOR") {
                val pending = db.tailorWageDao().pendingList()
                    .filter { it.tailorLabel == name.trim() }
                    .sumOf { it.amount }
                if (pending in 1..amount) {
                    settleTailorWages(name.trim(), amount)
                    return true
                }
            }
            spend(paySource, amount, note.ifBlank { "پرداخت به $name" }, category = "پرداخت دستی")
            postLedger(type, name, amount, 0, "MANUAL", note = note)
            createDocument("PAYMENT", name, amount, note = note.ifBlank { "پرداخت نقدی" })
            postJournal(
                "پرداخت به $name", "MANUAL", "",
                listOf(
                    jl(
                        when (type) {
                            "TAILOR" -> Accounts.WAGES_PAYABLE
                            "CUSTOMER" -> Accounts.RECEIVABLE
                            else -> Accounts.EXPENSES
                        },
                        debit = amount
                    ),
                    jl(Accounts.box(paySource), credit = amount)
                )
            )
        } else {
            income(paySource, amount, note.ifBlank { "دریافت از $name" })
            postLedger(type, name, 0, amount, "MANUAL", note = note)
            createDocument("RECEIPT", name, amount, note = note.ifBlank { "دریافت نقدی" })
            postJournal(
                "دریافت از $name", "MANUAL", "",
                listOf(
                    jl(Accounts.box(paySource), debit = amount),
                    jl(
                        if (type == "CUSTOMER") Accounts.RECEIVABLE else Accounts.OTHER_INCOME,
                        credit = amount
                    )
                )
            )
        }
        return true
    }

    /**
     * ثبت یک سند در دفتر کل و اطمینان از وجودِ طرف در دفترچه. debit/credit
     * از دیدِ دفترِ ما (بدهکار/بستانکارِ حسابِ طرف). یکی از دو مقدار صفر است.
     */
    suspend fun postLedger(
        type: String,
        name: String,
        debit: Long,
        credit: Long,
        refType: String,
        refId: String = "",
        note: String = ""
    ) {
        if (debit == 0L && credit == 0L) return
        val nm = name.trim().ifBlank { "نامشخص" }
        db.ledgerDao().insertParty(Party(name = nm, type = type))
        db.ledgerDao().insertEntry(
            LedgerEntry(
                partyType = type, partyName = nm,
                debit = debit, credit = credit,
                refType = refType, refId = refId, note = note
            )
        )
    }

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

    /** گردشِ انبار از یک تاریخ به بعد — برای محاسبهٔ نرخِ مصرف. */
    fun observeStockMovementsSince(since: Long): Flow<List<StockMovement>> =
        db.stockMovementDao().observeSince(since)

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
        if (reason == "اصلاح") audit("اصلاح دستی موجودی", "$name: $delta $unit")
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
        // سندِ فاکتور خرید
        createDocument(
            "PURCHASE", invoice.supplier.ifBlank { "نامشخص" }, invoice.total,
            invoice.code, "خرید مواد"
        )
        audit("خرید مواد", "${invoice.code} — ${invoice.total} ؋")
        // ژورنال: موادِ خریداری‌شده وارد دارایی؛ در برابرِ نقد یا بدهی
        if (invoice.total > 0) {
            val creditAccount =
                if (invoice.paySource == "CREDIT") Accounts.PAYABLE
                else Accounts.box(invoice.paySource)
            postJournal(
                "خرید مواد ${invoice.code}", "PURCHASE", invoice.code,
                listOf(
                    jl(Accounts.MATERIALS, debit = invoice.total),
                    jl(creditAccount, credit = invoice.total)
                )
            )
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
        // آینه در دفتر کل: قرضِ جدید → بستانکارِ حساب فروشنده (بدهیِ ما به او)
        postLedger("SUPPLIER", supplier, 0, amount, "PURCHASE_CREDIT", note = note)
    }

    /** تسویهٔ (بخشی از) بدهی فروشنده: پول از صندوق کم و بدهی کاهش می‌یابد. */
    suspend fun settleSupplier(supplier: String, amount: Long, paySource: String, note: String) {
        if (amount <= 0) return
        spend(paySource, amount, note.ifBlank { "تسویه قرض $supplier" }, category = "تسویه قرض")
        db.supplierDao().insert(
            SupplierLedger(supplier = supplier.trim(), amount = amount, type = "PAYMENT", note = note)
        )
        // آینه در دفتر کل: پرداخت به فروشنده → بدهکارِ حساب او (کاهش بدهیِ ما)
        postLedger("SUPPLIER", supplier, amount, 0, "SUPPLIER_PAYMENT", note = note)
        // رسیدِ پرداخت به فروشنده
        createDocument("SUPPLIER_PAYMENT", supplier, amount, note = note.ifBlank { "تسویه قرض" })
        audit("تسویه فروشنده", "$supplier — $amount ؋")
        // ژورنال: کاهشِ بدهی در برابرِ خروجِ نقد
        postJournal(
            "تسویه قرض $supplier", "SUPPLIER_PAYMENT", "",
            listOf(
                jl(Accounts.PAYABLE, debit = amount),
                jl(Accounts.box(paySource), credit = amount)
            )
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
        val totalCost = order.fabricPrice + order.workCost + order.sewingCost
        val perPieceCost = if (order.qty > 0) totalCost / order.qty else 0L
        addFinishedStock(order.designTitle, order.size, order.qty, perPieceCost)
        changeOrderStatus(order, OrderStatus.STORED.name)
        // ژورنال: بهای تمام‌شده از «کار در جریان» به «موجودی محصول» می‌رود؛
        // اگر برگشتی از فروش باشد (SENT)، از «بهای تمام‌شدهٔ فروش» برمی‌گردد.
        if (totalCost > 0) {
            val from = if (order.status == OrderStatus.SENT.name) Accounts.COGS else Accounts.WIP
            postJournal(
                "ورود به انبار محصول ${order.orderCode}", "TO_FINISHED", order.orderCode,
                listOf(
                    jl(Accounts.FINISHED, debit = totalCost),
                    jl(from, credit = totalCost)
                )
            )
        }
        // سفارش تبدیل به موجودیِ بی‌نامِ انبار شد؛ بدهیِ ازپیش‌ثبت‌شدهٔ مشتری
        // (SALE_BILLING هنگام ثبت سفارش) خنثی می‌شود — فروشِ واقعی هنگام
        // فروش از انبار محصول حساب و صورت‌حساب می‌شود.
        if (order.customerName.isNotBlank() && order.agreedPrice > 0) {
            postLedger(
                "CUSTOMER", order.customerName, 0, order.agreedPrice,
                "SALE_TO_STOCK", order.orderCode, "انتقال به انبار محصول"
            )
        }
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
        // بدهکارِ فروش در دفتر کل (متقابلِ دریافتی) تا حساب مشتری تراز بماند؛
        // فروشِ بی‌نام طرفِ حساب ندارد و آینه نمی‌شود (دریافتی‌اش هم نمی‌شود).
        if (customerName.isNotBlank()) {
            postLedger("CUSTOMER", customerName, revenue, 0, "SALE_BILLING", code, "فروش از انبار")
        }
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
        // فاکتور فروش
        createDocument(
            "SALE", customerName, revenue, code,
            "فروش $qty عدد «${item.name}»"
        )
        audit("فروش از انبار", "$code — $revenue ؋")
        // ژورنال: درآمدِ فروش + خروجِ بهای تمام‌شده + انتقالِ سود به صندوق فایده
        postJournal(
            "فروش «${item.name}» ($code)", "SALE", code,
            listOf(
                jl(Accounts.CASH, debit = revenue),
                jl(Accounts.SALES, credit = revenue),
                jl(Accounts.COGS, debit = cost),
                jl(Accounts.FINISHED, credit = cost)
            )
        )
        if (profit > 0L) {
            postJournal(
                "انتقال سود فروش ($code) به صندوق فایده", "PROFIT_MOVE", code,
                listOf(
                    jl(Accounts.PROFIT_BOX, debit = profit),
                    jl(Accounts.CASH, credit = profit)
                )
            )
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

    /**
     * ثبت طرح با کدِ اختصاصیِ کاربر (تاریخچهٔ کارگاه کدهای خودش را دارد).
     * اگر کد خالی بماند، کدِ خودکار (D-003) ساخته می‌شود. ثبتِ دوبارهٔ
     * همان نام با کدِ جدید = اصلاحِ کدِ طرحِ موجود.
     */
    suspend fun addDesign(item: DesignItem) {
        val rowId = db.masterDataDao().insertDesign(item)
        when {
            rowId > 0 && item.code.isBlank() ->
                db.masterDataDao().setDesignCode(rowId, "D-" + rowId.toString().padStart(3, '0'))
            rowId <= 0 && item.code.isNotBlank() ->
                db.masterDataDao().setDesignCodeByTitle(item.title, item.code.trim())
        }
    }

    fun observeStaff(): Flow<List<com.afghanjama.data.entities.Staff>> =
        db.masterDataDao().observeStaff()

    /**
     * افزودن/به‌روزرسانی کارمند. چون نام یکتاست، افزودنِ دوبارهٔ همان نام
     * به‌جای اینکه بی‌صدا رد شود، اطلاعاتش را به‌روز می‌کند.
     *
     * [monthlySalary] عمداً nullable است: صفحهٔ «کارکنان» حقوق نمی‌فرستد،
     * و نباید حقوقِ ثبت‌شده در صفحهٔ «حقوق کارکنان» را پاک کند. سمتِ خالی
     * هم مقدارِ قبلی را از بین نمی‌برد.
     */
    suspend fun addStaff(name: String, role: String, monthlySalary: Long? = null) {
        val n = name.trim()
        if (n.isEmpty()) return
        db.masterDataDao().insertStaff(
            com.afghanjama.data.entities.Staff(
                name = n, role = role.trim(),
                monthlySalary = (monthlySalary ?: 0).coerceAtLeast(0)
            )
        )
        if (monthlySalary != null) {
            db.masterDataDao().updateStaffTerms(n, role.trim(), monthlySalary.coerceAtLeast(0))
        } else {
            db.masterDataDao().updateStaffRole(n, role.trim())
        }
    }

    // =========================
    // Payroll (حقوقِ ماهانهٔ کارکنان)
    // =========================

    fun observeSalaryPayments(): Flow<List<com.afghanjama.data.entities.SalaryPayment>> =
        db.salaryDao().observeAll()

    /**
     * پرداختِ حقوقِ ماهانه. مثلِ هر رویدادِ مالیِ دیگر از همین قیف عبور
     * می‌کند: نقد → دفتر کل → ژورنالِ دوطرفه → رسید → لاگِ حسابرسی.
     *
     * در دفتر کل دو سطر ثبت می‌شود (تعهدِ حقوق و پرداختِ آن) تا صورت‌حسابِ
     * کارمند خوانا بماند و ماندهٔ حسابش پس از پرداخت صفر شود.
     *
     * @return false اگر مبلغ نامعتبر باشد یا موجودیِ صندوق کفایت نکند.
     */
    suspend fun paySalary(
        employee: String,
        amount: Long,
        periodKey: String,
        periodLabel: String,
        source: String = "WALLET",
        note: String = ""
    ): Boolean {
        val emp = employee.trim()
        if (emp.isEmpty() || amount <= 0) return false
        if (balanceOf(source) < amount) return false

        val memo = "حقوق $periodLabel — $emp"
        db.salaryDao().insert(
            com.afghanjama.data.entities.SalaryPayment(
                employee = emp, amount = amount,
                periodKey = periodKey, periodLabel = periodLabel,
                source = source, note = note.trim()
            )
        )
        spend(source, amount, note.ifBlank { memo }, category = "حقوق کارکنان")

        // دفتر کل: تعهدِ حقوق (بستانکار) و پرداختِ آن (بدهکار) → ماندهٔ صفر
        postLedger("EMPLOYEE", emp, 0, amount, "SALARY", periodKey, memo)
        postLedger("EMPLOYEE", emp, amount, 0, "SALARY_PAID", periodKey, note.ifBlank { memo })

        createDocument("SALARY_RECEIPT", emp, amount, refId = periodKey, note = memo)
        audit("پرداخت حقوق", "$emp — $periodLabel — $amount ؋")

        // ژورنال: هزینهٔ حقوق در برابرِ خروجِ نقد
        postJournal(
            memo, "SALARY", periodKey,
            listOf(
                jl(Accounts.EXPENSES, debit = amount),
                jl(Accounts.box(source), credit = amount)
            )
        )
        return true
    }

    /** شمارندهٔ محصولِ هر طرح تا این لحظه (مجموع تعداد سفارش‌های آن طرح). */
    fun observeDesignProduction(): Flow<List<com.afghanjama.data.dao.DesignProduction>> =
        db.orderDao().observeDesignProduction()

    suspend fun addCustomer(item: Customer) =
        db.masterDataDao().insertCustomer(item)

    // =========================
    // Attendance (حضور و غیاب کارمند)
    // =========================

    fun observeAttendance(): Flow<List<AttendanceRecord>> =
        db.attendanceDao().observeRecent()

    /** بازه‌های حضور از یک تاریخ به بعد (برای گزارش کارکرد ماهانه). */
    fun observeAttendanceSince(since: Long): Flow<List<AttendanceRecord>> =
        db.attendanceDao().observeSince(since)

    /** ثبت ورود؛ اگر کارمند از قبل «داخل» باشد کاری نمی‌کند. */
    suspend fun checkIn(employee: String) {
        val emp = employee.trim()
        if (emp.isEmpty()) return
        if (db.attendanceDao().findOpen(emp) != null) return
        db.attendanceDao().insert(AttendanceRecord(employee = emp, checkIn = System.currentTimeMillis()))
    }

    /** ثبت خروج برای بازهٔ بازِ کارمند. */
    suspend fun checkOut(employee: String) {
        val open = db.attendanceDao().findOpen(employee.trim()) ?: return
        db.attendanceDao().update(open.copy(checkOut = System.currentTimeMillis()))
    }

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

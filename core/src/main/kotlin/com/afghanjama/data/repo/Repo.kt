package com.afghanjama.data.repo

import com.afghanjama.data.Db
import com.afghanjama.data.CashPolicy
import com.afghanjama.data.PartialFlow
import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.dao.NamedMeasurement
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
import com.afghanjama.data.entities.DomainEvent
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
import com.afghanjama.data.ResetPlan
import com.afghanjama.data.StockFolders
import com.afghanjama.data.SalePolicy
import com.afghanjama.ui.format.bareWorkerName
import com.afghanjama.util.CurrentUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class Repo(private val db: Db) {

    private companion object {
        /**
         * چند ماه رویدادِ پردازش‌شده نگه داشته شود.
         *
         * ۲۴ — تصمیمِ کارگاه. دو سالِ مالی کامل برای هر بازبینی یا
         * اختلافِ حساب کافی است، و جدول بی‌مرز رشد نمی‌کند.
         */
        const val RETENTION_MONTHS = 24
    }

    // =========================
    // Orders
    // =========================

    fun observeOrdersByStatus(status: String): Flow<List<Order>> =
        db.orderDao().observeByStatus(status)

    fun observeAllOrders(): Flow<List<Order>> =
        db.orderDao().observeAll()

    /**
     * کدِ اختصاصیِ طرحِ هر سفارش (مثلِ DIP-12)، کلید: کدِ سفارش.
     *
     * دو کد در اپ هست و کارِ متفاوتی می‌کنند:
     *
     * - `orderCode` (AJ-2026-000001) را خودِ اپ می‌سازد. یکتاست، اسکن
     *   می‌شود و مبنای پیگیری است.
     * - کدِ طرح را کارگاه در اطلاعات پایه ثبت کرده و طرح را با همان
     *   می‌شناسد.
     *
     * روی هر صفحه‌ای که کار را به کاربر نشان می‌دهد باید کدِ طرح جلو
     * باشد. این نگاشت یک‌جا اینجاست تا ده ViewModel هرکدام برای خودشان
     * یک نسخه نسازند و فردا از هم جدا نیفتند.
     *
     * پیوند از راهِ **نامِ طرح** است، چون سفارش نامِ طرح را نگه می‌دارد نه
     * شناسه‌اش. اگر نامِ طرح در اطلاعات پایه عوض شود پیوند قطع می‌شود و
     * مقدار خالی برمی‌گردد — که صفحه‌ها آن را به «فقط کدِ سفارش» ترجمه
     * می‌کنند، نه به کارتِ بی‌نشانه.
     */
    fun observeDesignCodeByOrder(): Flow<Map<String, String>> =
        combine(observeAllOrders(), observeDesignItems()) { orders, designs ->
            val byTitle = designs
                .filter { it.code.isNotBlank() }
                .associate { it.title.trim() to it.code.trim() }
            orders.associate { o -> o.orderCode to byTitle[o.designTitle.trim()].orEmpty() }
        }

    suspend fun getOrder(id: UUID): Order? =
        db.orderDao().getById(id)

    fun observeOrderById(id: UUID): Flow<Order?> =
        db.orderDao().observeById(id)

    suspend fun updateOrder(order: Order) =
        db.orderDao().update(order)

    /**
     * @return false اگر خرج‌کار نقدی باشد و صندوق کافی نباشد — و آن‌وقت
     * **هیچ چیز ثبت نمی‌شود**. کنترل پیش از اولین نوشتن انجام می‌شود تا
     * سفارشِ نیمه‌ثبت جا نمانَد؛ همان درسی که مسیرهای دیگرِ خروجِ نقد هم
     * از آن پیروی می‌کنند.
     */
    suspend fun createOrder(
        order: Order,
        fabrics: List<OrderFabric> = emptyList(),
        workItems: List<OrderWorkItem> = emptyList()
    ): Boolean = db.atomic {
        createOrderTx(order, fabrics, workItems)
    }

    private suspend fun createOrderTx(
        order: Order,
        fabrics: List<OrderFabric>,
        workItems: List<OrderWorkItem>
    ): Boolean {
        val src = order.workCostSource.trim().uppercase().ifBlank { "CREDIT" }
        if (order.workCost > 0 && src != "CREDIT" && !hasFunds(src, order.workCost)) return false

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

        // خرج‌کار (دکمه، زیپ، لایی…) بخشی از بهای تمام‌شده است و هنگام
        // ورودِ محصول به انبار از «کار در جریان» بستانکار می‌شود. پس باید
        // همین‌جا بدهکارش شود، وگرنه «کار در جریان» به اندازهٔ خرج‌کار
        // منفی می‌ماند و «موجودی محصول» همان‌قدر بیش‌ازواقع می‌شود.
        // پرداختش بعداً از «پرداخت به فروشنده» تسویه می‌شود.
        if (order.workCost > 0) {
            /*
             * خرج‌کار یا همان لحظه پرداخت می‌شود یا بدهی می‌مانَد.
             *
             * تا امروز همیشه بدهی ثبت می‌شد و هیچ طرفِ حسابی نداشت، پس
             * راهی برای تسویه‌اش نبود: PAYABLE فقط با settleSupplier کم
             * می‌شود و آن نامِ فروشنده می‌خواهد. نتیجه این بود که بدهی
             * برای همیشه می‌مانْد و پول هرگز از صندوق بیرون نمی‌رفت —
             * بهای تمام‌شده روی کاغذ درست بود ولی صندوق واقعیت را نشان
             * نمی‌داد.
             *
             * حالا اگر نقد باشد همان‌جا از صندوق کم می‌شود، و اگر نسیه
             * باشد به نامِ طرفِ حساب ثبت می‌گردد تا بشود تسویه‌اش کرد.
             */
            val src = order.workCostSource.trim().uppercase().ifBlank { "CREDIT" }
            val payee = order.workCostPayee.trim()
            val cash = src != "CREDIT"

            postJournal(
                "خرج‌کارِ سفارش ${order.orderCode}", "WORK_ITEMS", order.orderCode,
                listOf(
                    jl(Accounts.WIP, debit = order.workCost),
                    if (cash) jl(Accounts.box(src), credit = order.workCost)
                    else jl(Accounts.PAYABLE, credit = order.workCost)
                )
            )

            if (cash) {
                // صندوق هم باید واقعاً کم شود، نه فقط ژورنال
                spend(
                    source = src,
                    amount = order.workCost,
                    note = "خرج‌کارِ سفارش ${order.orderCode}",
                    category = "خرج کار"
                )
            } else if (payee.isNotBlank()) {
                // بدهیِ بی‌نام تسویه‌نشدنی است؛ با نام در دفترِ طرف می‌نشیند
                postLedger(
                    type = "SUPPLIER",
                    name = payee,
                    debit = 0,
                    credit = order.workCost,
                    refType = "WORK_ITEMS",
                    refId = order.orderCode,
                    note = "خرج‌کارِ سفارش ${order.orderCode}"
                )
            }
        }
        // بدهیِ مشتری بابتِ این سفارش → بدهکارِ حساب مشتری در دفتر کل
        // (پرداخت‌های او بستانکار می‌شوند؛ مانده = طلبِ ما از مشتری).
        if (order.customerName.isNotBlank() && order.agreedPrice > 0) {
            postLedger(
                "CUSTOMER", order.customerName, order.agreedPrice, 0,
                "SALE_BILLING", order.orderCode, "بدهی بابت سفارش"
            )
        }
        return true
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
     * تأیید کیفیت: رکورد QC ثبت و **همان تعدادی که دستِ ناظر بود** واردِ
     * انبار محصول می‌شود — نه کلِ سفارش. تصمیمِ فروش (مشتری/قیمت/تخفیف)
     * در انبار محصول توسط بخش فروش گرفته می‌شود؛ نظارت فقط کیفیت را
     * تأیید می‌کند.
     *
     * اگر باقیِ سفارش هنوز در دوخت باشد، سفارش به «دوخت» برمی‌گردد و
     * منتظرِ دستهٔ بعدی می‌ماند. فقط وقتی همهٔ عددها از این مسیر گذشتند
     * سفارش بسته می‌شود.
     */
    /**
     * @return false اگر سفارش در مرحلهٔ نظارت نباشد یا چیزی دستِ ناظر
     * نباشد؛ هیچ چیزی ثبت نمی‌شود.
     *
     * وضعیت و تعداد از **دیتابیس** خوانده می‌شوند، نه از شیءِ ورودی. بی
     * این کنترل، دو ضربهٔ سریع روی «تأیید» دو بار کالا را وارد انبار
     * می‌کرد: موجودیِ محصول دو برابر، «کار در جریان» به اندازهٔ بهای
     * تمام‌شده منفی، و حسابِ مشتری دو بار بستانکار. تپِ دوم وضعیتِ کهنه را
     * می‌دید، پس حتی متوجه نمی‌شد کالا از قبل وارد انبار شده.
     */
    suspend fun approveQc(order: Order, inspector: String, note: String): Boolean {
        val fresh = db.orderDao().getById(order.id) ?: return false
        if (fresh.status != OrderStatus.REVIEW.name) return false
        // سقفِ باقی‌ماندهٔ سفارش هم اعمال می‌شود: سفارش هرگز بیش از عددِ
        // خودش کالا وارد انبار نمی‌کند، حتی اگر شمارشِ نظارت خراب شده باشد.
        val batch = minOf(fresh.reviewQty, fresh.qty - fresh.storedQty)
        if (batch <= 0) return false
        db.qcRecordDao().insert(
            QcRecord(
                orderId = fresh.id.toString(),
                orderCode = fresh.orderCode,
                inspector = inspector.trim(),
                result = "APPROVED",
                note = note.trim()
            )
        )
        depositBatchToFinished(
            fresh.copy(
                assignedInspector = inspector.trim().ifBlank { fresh.assignedInspector },
                reviewed = true
            ),
            batch
        )
        audit("تأیید نظارت", "${fresh.orderCode} — $inspector — ${batch} عدد")
        return true
    }

    /**
     * فرستادنِ آنچه تا این لحظه دوخته شده به نظارت — هر تعداد که باشد.
     *
     * @return پیغام برای کاربر، یا null اگر انجام شد.
     *
     * سفارش از دیتابیس تازه خوانده می‌شود چون شمارش بر خودِ سفارش تکیه
     * دارد و شیءِ کهنه همان عددها را دوباره می‌فرستد.
     */
    suspend fun sendOrderToReview(order: Order): String? {
        val fresh = db.orderDao().getById(order.id) ?: return "این سفارش پیدا نشد."
        if (fresh.status != OrderStatus.CUT_DONE.name && fresh.status != OrderStatus.SEWING.name)
            return "این سفارش در مرحلهٔ دوخت نیست."
        if (!markReadyForReview(fresh)) {
            val waiting = fresh.reviewQty
            return if (waiting > 0)
                "چیزِ تازه‌ای دوخته نشده؛ ${waiting} عدد از قبل دستِ نظارت است."
            else
                "هنوز عددی دوخته نشده که به نظارت برود."
        }
        return null
    }

    /**
     * عددهای تازه‌دوخته‌شده را به نظارت می‌سپارد و سفارش را به مرحلهٔ
     * نظارت می‌برد. @return false وقتی چیزِ تازه‌ای نیست.
     *
     * هم دکمهٔ صفحهٔ دوخت از اینجا می‌گذرد و هم تکمیلِ خودکارِ آخرین
     * تحویل — یک قاعده، دو در.
     */
    private suspend fun markReadyForReview(order: Order): Boolean {
        val newly = PartialFlow.readyToSend(
            qty = order.qty,
            sewn = sewnQtyOfOrder(order.id.toString()),
            inReview = order.reviewQty,
            stored = order.storedQty
        )
        if (newly <= 0) return false
        changeOrderStatus(order, OrderStatus.REVIEW.name) {
            it.copy(reviewQty = it.reviewQty + newly)
        }
        audit("ارسال به نظارت", "${order.orderCode} — ${newly} عدد")
        return true
    }

    /**
     * خیاطانی که روی یک سفارش کار کرده‌اند — برای اینکه ناظر هنگامِ برگشت
     * بتواند بگوید کارِ کدام‌شان برگشت خورده.
     */
    /**
     * چند عدد از یک سفارش واقعاً دوخته و تحویل شده.
     *
     * پایهٔ خودِ جریان است، نه یک نگهبان: هر بار که سفارش به نظارت
     * می‌رود، تفاوتِ این عدد با آنچه قبلاً رفته همان دستهٔ تازه است.
     */
    suspend fun sewnQtyOfOrder(orderId: String): Int =
        db.sewingAssignmentDao().listForOrder(orderId)
            .filter { it.status == "DONE" }
            .sumOf { it.qty }

    /** تحویل‌های دوخته‌شدهٔ یک سفارش، برای تقسیمِ دستمزد بینِ دسته‌ها. */
    private suspend fun sewnBatchesOfOrder(orderId: String): List<PartialFlow.Sewn> =
        db.sewingAssignmentDao().listForOrder(orderId)
            .filter { it.status == "DONE" }
            .map { PartialFlow.Sewn(it.qty, it.unitWage, it.doneAt ?: it.createdAt) }

    suspend fun tailorsOfOrder(orderId: String): List<String> =
        db.sewingAssignmentDao().listForOrder(orderId)
            .map { it.tailorLabel.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    /**
     * برگشت برای اصلاح: مشکل ثبت و سفارش به مرحلهٔ دوخت برمی‌گردد.
     * [tailor] اختیاری است؛ اگر ناظر بگوید کارِ کدام خیاط برگشت خورده،
     * همان‌جا ثبت می‌شود تا کارنامه لازم نباشد حدس بزند.
     *
     * عددهایی که دستِ ناظر بودند به دوخت برمی‌گردند (`reviewQty` صفر
     * می‌شود). چون تحویل‌هایشان همچنان «دوخته‌شده» ثبت است، پس از اصلاح
     * با همان دکمه دوباره به نظارت می‌روند.
     */
    suspend fun rejectQc(order: Order, inspector: String, problem: String, tailor: String = "") {
        db.qcRecordDao().insert(
            QcRecord(
                orderId = order.id.toString(),
                orderCode = order.orderCode,
                inspector = inspector.trim(),
                result = "REJECTED",
                problem = problem.trim(),
                tailor = tailor.trim()
            )
        )
        changeOrderStatus(order, OrderStatus.SEWING.name) {
            it.copy(
                assignedInspector = inspector.trim().ifBlank { it.assignedInspector },
                reviewed = false,
                reviewQty = 0
            )
        }
        audit(
            "رد نظارت (برگشت به دوخت)",
            "${order.orderCode} — $problem" + tailor.trim().let { if (it.isBlank()) "" else " — خیاط: $it" }
        )
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
        // وضعیتِ مصرفِ مواد از **دیتابیس** خوانده می‌شود، نه از شیءِ ورودی.
        // دو ضربهٔ سریع روی «ثبت برش» هر دو یک نسخهٔ کهنه در دست دارند که
        // `materialsConsumed = false` است، پس هر دو مواد را کسر می‌کردند و
        // هر دو سند می‌زدند: انبار دو بار خالی، «کار در جریان» دو برابر.
        val alreadyConsumed =
            db.orderDao().getById(order.id)?.materialsConsumed ?: order.materialsConsumed
        if (!alreadyConsumed) {
            // ارزشِ واقعیِ برداشته‌شده جمع می‌شود، نه برآوردِ ثبتِ سفارش.
            // این دو با هر خریدِ تازه از هم فاصله می‌گیرند، چون میانگینِ
            // وزنیِ انبار عوض می‌شود ولی `fabricPrice` همان عددِ روزِ ثبت
            // می‌ماند. اگر برآورد ژورنال شود، حسابِ مواد از انبار جدا
            // می‌افتد و هرگز خودش را جبران نمی‌کند.
            var takenValue = 0L
            db.orderFabricDao().listForOrder(order.id.toString())
                .filter { it.source == "MATERIAL" || it.source == "STOCK" }
                .forEach { f ->
                    /*
                     * نامِ انبار باید دقیقاً همانی باشد که هنگامِ خرید
                     * ساخته شده، وگرنه برداشت به ردیفِ دیگری می‌خورد و
                     * موجودی هرگز کم نمی‌شود.
                     *
                     * منبعِ STOCK پارچه است و نامش «نوع + رنگ». منبعِ
                     * MATERIAL یک مادهٔ عمومی است و نامش همان نوع؛ ولی اگر
                     * رنگ هم داشته باشد یعنی در واقع پارچه است، پس همان
                     * قاعدهٔ پارچه برایش به کار می‌رود.
                     */
                    val matName =
                        if (f.source == "STOCK" || f.fabricColor.isNotBlank())
                            fabricMaterialName(f.fabricType, f.fabricColor)
                        else f.fabricType
                    takenValue += changeMaterialStock(
                        matName, f.fabricUnit, -f.amount,
                        reason = "مصرف برش", note = "سفارش ${order.orderCode}"
                    )
                }
            // ژورنال: ارزشِ موادِ مصرفی از انبار به «کار در جریان» می‌رود
            if (takenValue > 0) {
                postJournal(
                    "مصرف مواد در برش ${order.orderCode}", "CUTTING", order.orderCode,
                    listOf(
                        jl(Accounts.WIP, debit = takenValue),
                        jl(Accounts.MATERIALS, credit = takenValue)
                    )
                )
            }
            // بهای تمام‌شدهٔ سفارش بر `fabricPrice` بنا شده و هنگام ورود به
            // انبار محصول همان از «کار در جریان» بستانکار می‌شود. تفاوتِ
            // برآورد با واقعیت باید همین‌جا تسویه شود، وگرنه «کار در جریان»
            // به صفر برنمی‌گردد.
            val estimateGap = order.fabricPrice - takenValue
            if (estimateGap != 0L) {
                postJournal(
                    "اصلاح برآوردِ موادِ ${order.orderCode}", "CUTTING_ADJUST", order.orderCode,
                    if (estimateGap > 0) listOf(
                        jl(Accounts.WIP, debit = estimateGap),
                        jl(Accounts.EXPENSES, credit = estimateGap)
                    ) else listOf(
                        jl(Accounts.EXPENSES, debit = -estimateGap),
                        jl(Accounts.WIP, credit = -estimateGap)
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
        db.orderPhotoDao().deleteForOrder(order.id.toString())
        if (order.materialsConsumed) {
            val allRows = db.orderFabricDao().listForOrder(order.id.toString())
            // پارچه‌های «از موجودی» با نام «نوع رنگ» به انبار مواد برمی‌گردند
            val stockRows = allRows.filter { it.source == "STOCK" }
            // موادِ مصرفی «از انبار عمومی» با نام خودشان به انبار مواد برمی‌گردند
            val materialRows = allRows.filter { it.source == "MATERIAL" }
            var returnedValue = 0L
            when {
                stockRows.isNotEmpty() || materialRows.isNotEmpty() -> {
                    stockRows.forEach {
                        returnedValue += changeMaterialStock(
                            fabricMaterialName(it.fabricType, it.fabricColor), it.fabricUnit, it.amount,
                            reason = "برگشت به انبار", note = "حذف سفارش ${order.orderCode}")
                    }
                    materialRows.forEach {
                        returnedValue += changeMaterialStock(it.fabricType, it.fabricUnit, it.amount,
                            reason = "برگشت به انبار", note = "حذف سفارش ${order.orderCode}")
                    }
                }
                order.fabricSource == "STOCK" -> {
                    // سفارش‌های قدیمی که ردیف پارچه ندارند
                    returnedValue += changeMaterialStock(
                        fabricMaterialName(order.fabricType, order.fabricColor), order.fabricUnit, order.fabricAmount,
                        reason = "برگشت به انبار", note = "حذف سفارش ${order.orderCode}")
                }
            }
            // تا امروز جنس به انبار برمی‌گشت ولی سندش خنثی نمی‌شد: ارزشِ
            // انبار بالا می‌رفت، حسابِ مواد نه، و «کار در جریان» برای همیشه
            // بادکرده می‌ماند. مسیرش هم دور از دسترس نبود — برش، بعد
            // «برگشت به انبار»، بعد حذف.
            // «کار در جریان» باید دقیقاً به صفر برگردد، پس همان مبلغی که
            // هنگام برش داخلش رفت (`fabricPrice`) بستانکار می‌شود — نه
            // ارزشِ برگشتی. مواد به ارزشِ واقعیِ امروز بدهکار می‌شود و
            // تفاوتِ این دو به هزینه‌ها می‌رود، همان‌جا که تفاوتِ برآورد
            // هنگام برش هم رفته بود.
            val wipBack = order.fabricPrice
            if (returnedValue > 0 || wipBack > 0) {
                val gap = wipBack - returnedValue
                postJournal(
                    "برگشت موادِ سفارشِ حذف‌شده ${order.orderCode}",
                    "ORDER_DELETE", order.orderCode,
                    buildList {
                        if (returnedValue > 0) add(jl(Accounts.MATERIALS, debit = returnedValue))
                        if (gap > 0) add(jl(Accounts.EXPENSES, debit = gap))
                        if (gap < 0) add(jl(Accounts.EXPENSES, credit = -gap))
                        if (wipBack > 0) add(jl(Accounts.WIP, credit = wipBack))
                    }
                )
            }
        }
        deleteOrder(order)
    }

    // ✅ NEW: delete order (برای حذف سفارش) + پاک‌کردن جدول‌های فرزند
    suspend fun deleteOrder(order: Order) = db.atomic {
        deleteOrderTx(order)
    }

    private suspend fun deleteOrderTx(
        order: Order
    ) {
        db.orderFabricDao().deleteForOrder(order.id.toString())
        db.orderWorkItemDao().deleteForOrder(order.id.toString())
        db.sewingAssignmentDao().deleteForOrder(order.id.toString())
        db.orderDao().delete(order)
        audit("حذف سفارش", order.orderCode)
        // خرج‌کار هنگام ثبتِ سفارش بدهکارِ «کار در جریان» و بستانکارِ
        // «پرداختنی» شد. با حذفِ سفارش هر دو باید برگردند، وگرنه «کار در
        // جریان» به اندازهٔ خرج‌کار بادکرده می‌ماند و بدهیِ فروشنده‌ای ثبت
        // می‌شود که هیچ‌وقت کسی از او چیزی نگرفته.
        //
        // برگشتِ موادِ سفارش شرطِ `materialsConsumed` دارد چون کسرِ مواد
        // هنگامِ برش انجام می‌شود؛ خرج‌کار ولی همان لحظهٔ ثبت وارد سند
        // می‌شود، پس برگشتش بی‌شرط است.
        if (order.workCost > 0) {
            postJournal(
                "برگشتِ خرج‌کارِ سفارشِ حذف‌شده ${order.orderCode}",
                "ORDER_DELETE", order.orderCode,
                listOf(
                    jl(Accounts.PAYABLE, debit = order.workCost),
                    jl(Accounts.WIP, credit = order.workCost)
                )
            )
        }
        // دستمزدِ دوخت هم هنگامِ تحویلِ خیاط بدهکارِ «کار در جریان» شده بود
        // و با حذفِ سفارش باید از آن بیرون بیاید، وگرنه همان‌جا بادکرده
        // می‌مانَد.
        //
        // برخلافِ خرج‌کار، بدهیِ خیاط **برنمی‌گردد**: او واقعاً کار کرده و
        // کارمزدش را باید گرفت؛ سطرش در `tailor_wages` هم دست‌نخورده
        // می‌مانَد تا در تسویه بیاید. پس مبلغ زیانِ سفارشِ لغوشده است و به
        // هزینه‌ها می‌رود، نه به بستانکارِ خیاط.
        if (order.sewingCost > 0) {
            postJournal(
                "دستمزدِ دوختِ سفارشِ حذف‌شده ${order.orderCode}",
                "ORDER_DELETE", order.orderCode,
                listOf(
                    jl(Accounts.EXPENSES, debit = order.sewingCost),
                    jl(Accounts.WIP, credit = order.sewingCost)
                )
            )
        }
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
        // بیش از باقی‌ماندهٔ سفارش سپرده نمی‌شود. صفحه هم همین را می‌گوید،
        // ولی قاعده‌ای که فقط در صفحه زندگی کند با صفحهٔ تازه یا مسیرِ
        // همگام‌سازی دور زده می‌شود — و آن‌وقت «دوخته‌شده» از تعدادِ سفارش
        // جلو می‌زند و عددی وارد انبار می‌شود که هرگز بریده نشده.
        val alreadyHanded = db.sewingAssignmentDao().listForOrder(order.id.toString())
            .sumOf { it.qty }
        val room = (order.qty - alreadyHanded).coerceAtLeast(0)
        val give = qty.coerceIn(0, room)
        if (give <= 0) return
        db.sewingAssignmentDao().insert(
            SewingAssignment(
                orderId = order.id.toString(),
                orderCode = order.orderCode,
                tailorLabel = tailorLabel,
                qty = give,
                unitWage = unitWage,
                status = "SEWING"
            )
        )
        audit("تحویل به خیاط", "${order.orderCode} → $tailorLabel (${give} عدد)")
        val handed = db.sewingAssignmentDao().listForOrder(order.id.toString()).sumOf { it.qty }
        val label = summarizeTailors(order.id.toString())
        if (handed >= order.qty && order.status == "CUT_DONE") {
            changeOrderStatus(order, "SEWING") { it.copy(assignedTailor = label) }
        } else {
            db.orderDao().update(order.copy(assignedTailor = label))
        }
    }

    /**
     * برگشتِ سفارش از «آمادهٔ دوخت» به «برش» (اصلاح اشتباه).
     *
     * @return false اگر تحویلی به خیاط ثبت شده باشد؛ چیزی عوض نمی‌شود.
     *
     * تا امروز این شرط فقط در صفحهٔ دوخت بود (دکمه وقتی تحویلی هست نشان
     * داده نمی‌شد). ولی قاعده‌ای که در صفحه زندگی می‌کند با صفحهٔ تازه یا
     * مسیرِ همگام‌سازی دور زده می‌شود، و آن‌وقت سفارشی که دستمزدش ثبت شده
     * می‌تواند به انبار برگردد و حذف شود.
     */
    suspend fun sendOrderBackToCutting(order: Order): Boolean {
        val handouts = db.sewingAssignmentDao().listForOrder(order.id.toString())
        if (handouts.isNotEmpty()) return false
        changeOrderStatus(order, OrderStatus.CUTTING.name)
        return true
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
    suspend fun completeAssignment(assignmentId: Long, quality: String = "", deliveredQty: Int? = null) = db.atomic {
        completeAssignmentTx(assignmentId, quality, deliveredQty)
    }

    private suspend fun completeAssignmentTx(
        assignmentId: Long,
        quality: String,
        deliveredQty: Int?
    ) {
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
            // آخرین تحویل که بسته شد، باقی‌ماندهٔ نرفته خودش به نظارت
            // می‌رود — نه کلِ سفارش، چون شاید بخشی‌اش از قبل رفته باشد.
            markReadyForReview(order)
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

    /**
     * [category] فقط توصیفی است و ماندهٔ صندوق را عوض نمی‌کند؛ خالی یعنی
     * ورودیِ عادی. برای جابه‌جاییِ بینِ صندوق‌های خودمان
     * [CashPolicy.INTERNAL_MOVE] داده می‌شود تا گزارشِ نقد آن را با پولِ
     * واقعی قاطی نکند.
     */
    suspend fun income(source: String, amount: Long, note: String, category: String = "") {
        db.financeDao().insertTx(
            Transaction(
                type = "IN",
                source = source,
                amount = amount.coerceAtLeast(0),
                note = note,
                category = category
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

    /**
     * آیا صندوق این مبلغ را دارد؟
     *
     * `spend` خودش هیچ سقفی ندارد و بی‌سروصدا ثبت می‌کند، پس هر مسیرِ
     * خروجِ نقد باید پیش از پرداخت از اینجا بگذرد. چهار مسیر این کنترل را
     * داشتند و چهار مسیر نه — همان ناسازگاری بود که صندوق را منفی می‌کرد.
     */
    private suspend fun hasFunds(source: String, amount: Long): Boolean =
        CashPolicy.canSpend(balanceOf(source), amount)

    /**
     * انتقال بین صندوق‌ها (کیف پول / بانک / فایده).
     *
     * @return false اگر صندوقِ مبدأ کافی نباشد؛ چیزی ثبت نمی‌شود. انتقال
     * پول را از بین نمی‌برد ولی مبدأ می‌تواند منفی شود، پس همان کنترل
     * لازم است.
     */
    suspend fun transfer(from: String, to: String, amount: Long, note: String): Boolean = db.atomic {
        transferTx(from, to, amount, note)
    }

    private suspend fun transferTx(
        from: String,
        to: String,
        amount: Long,
        note: String
    ): Boolean {
        if (from == to || amount <= 0) return false
        if (!hasFunds(from, amount)) return false
        // جابه‌جاییِ داخلی: پولی وارد یا خارجِ کارگاه نمی‌شود، پس هر دو سطر
        // برچسب می‌خورند تا گزارشِ نقد آن را درآمد یا هزینه نشمارد.
        spend(from, amount, note, category = CashPolicy.INTERNAL_MOVE)
        income(to, amount, note, category = CashPolicy.INTERNAL_MOVE)
        audit("انتقال بین صندوق‌ها", "$from → $to — $amount ؋")
        emit(
            type = "انتقال بین صندوق‌ها",
            aggregate = "transaction",
            aggregateId = "$from→$to",
            payload = "از=$from؛به=$to؛مبلغ=$amount"
        )
        postJournal(
            note, "TRANSFER", "",
            listOf(
                jl(Accounts.box(to), debit = amount),
                jl(Accounts.box(from), credit = amount)
            )
        )
        return true
    }

    /** دریافتیِ دستی از مشتری: نقد + حساب مشتری + دفتر کل + ژورنال، یک‌جا. */
    suspend fun recordCustomerReceipt(name: String, amount: Long, note: String = "") = db.atomic {
        recordCustomerReceiptTx(name, amount, note)
    }

    private suspend fun recordCustomerReceiptTx(
        name: String,
        amount: Long,
        note: String
    ) {
        if (name.isBlank() || amount <= 0) return
        val n = note.ifBlank { "دریافتی از $name" }
        income("WALLET", amount, n)
        audit("دریافت از مشتری", "$name — $amount ؋")
        emit(
            type = "دریافت از مشتری",
            aggregate = "party",
            aggregateId = name,
            payload = "مشتری=$name؛مبلغ=$amount"
        )
        addCustomerPayment(
            CustomerPayment(orderId = "", customerName = name, amount = amount, source = "MANUAL", note = n)
        )
        postJournal(
            n, "CUSTOMER_RECEIPT", "",
            listOf(
                jl(Accounts.CASH, debit = amount),
                // اگر طلبی از او داریم این دریافت آن را می‌بندد؛ وگرنه
                // پیش‌دریافت است و بدهیِ ما می‌شود.
                jl(customerCreditAccount(name, amount), credit = amount)
            )
        )
    }

    /**
     * بیعانه/پیش‌پرداختِ مشتری هنگامِ ثبتِ سفارش.
     *
     * ثبتِ سفارش، کلِ قیمتِ توافقی را بدهکارِ حسابِ مشتری می‌کند
     * (SALE_BILLING)؛ این دریافت آن را بستانکار می‌کند، پس ماندهٔ حساب
     * دقیقاً «باقی‌ماندهٔ بدهیِ مشتری» می‌ماند.
     */
    suspend fun recordOrderDeposit(order: Order, amount: Long, source: String = "WALLET") = db.atomic {
        recordOrderDepositTx(order, amount, source)
    }

    private suspend fun recordOrderDepositTx(
        order: Order,
        amount: Long,
        source: String
    ) {
        if (amount <= 0) return
        val customer = order.customerName.trim()
        val note = "بیعانهٔ سفارش ${order.orderCode}"

        income(source, amount, note)
        addCustomerPayment(
            CustomerPayment(
                orderId = order.id.toString(),
                customerName = customer,
                amount = amount,
                source = "ADVANCE",
                note = note
            )
        )
        if (customer.isNotBlank()) {
            createDocument(
                "CUSTOMER_RECEIPT", customer, amount,
                refId = order.orderCode, note = note
            )
        }
        audit("دریافت بیعانه", "${order.orderCode} — $amount ؋")
        postJournal(
            note, "CUSTOMER_ADVANCE", order.orderCode,
            listOf(
                jl(Accounts.box(source), debit = amount),
                // پولِ پیش از تحویل نه درآمد است نه کاهشِ طلب — ژورنال هنوز
                // هیچ طلبی برای این سفارش ثبت نکرده، پس این بدهیِ ماست.
                jl(Accounts.CUSTOMER_PREPAY, credit = amount)
            )
        )
    }

    /**
     * دریافت از مشتری کدام حساب را می‌بندد؟ اگر در دفتر کل به ما بدهکار
     * است، این دریافت طلب را کم می‌کند؛ وگرنه پولِ پیش از تحویل است و
     * بدهیِ ما می‌شود. بدونِ این تفکیک، دریافت‌ها یکی از دو حساب را به
     * سمتِ اشتباه می‌بردند.
     */
    private suspend fun customerCreditAccount(name: String, amount: Long): String {
        val rows = db.ledgerDao().observeEntriesForParty("CUSTOMER", name.trim()).first()
        val owes = rows.sumOf { it.debit } - rows.sumOf { it.credit }
        return if (owes >= amount) Accounts.RECEIVABLE else Accounts.CUSTOMER_PREPAY
    }

    /**
     * هزینهٔ عمومی (کرایه، برق، معاش...): خروجِ نقد + ثبتِ ژورنالِ هزینه.
     * @return false اگر موجودیِ صندوق کافی نباشد؛ چیزی ثبت نمی‌شود.
     */
    suspend fun recordExpense(
        source: String,
        category: String,
        amount: Long,
        note: String
    ): Boolean = db.atomic {
        recordExpenseTx(source, category, amount, note)
    }

    private suspend fun recordExpenseTx(
        source: String,
        category: String,
        amount: Long,
        note: String
    ): Boolean {
        if (amount <= 0) return false
        if (!hasFunds(source, amount)) return false
        spend(source, amount, note, category = category)
        audit("ثبت هزینه", "$category — $amount ؋")
        emit(
            type = "ثبت هزینه",
            aggregate = "transaction",
            aggregateId = category,
            payload = "دسته=$category؛مبلغ=$amount"
        )
        postJournal(
            note.ifBlank { "هزینه: $category" }, "EXPENSE", "",
            listOf(
                jl(Accounts.EXPENSES, debit = amount),
                jl(Accounts.box(source), credit = amount)
            )
        )
        return true
    }

    /**
     * تعمیرِ یک‌بارهٔ سندهای دستیِ قدیمی.
     *
     * سندهای `MANUAL_CASH`ی که پیش از اصلاحِ طبقه‌بندی ثبت شده‌اند،
     * ورودشان روی `4090` (درآمد) و خروجشان روی `5020` (هزینه) نشسته.
     * هر دو به `3010` (سرمایه) می‌روند.
     *
     * **چرا مهاجرتِ اسکیما نشد.** این تغییرِ **داده** است نه ساختار،
     * و بردنش به `SchemaStep` یعنی `DB_VERSION` یک پله دیگر جلو
     * برود و یک طرحِ صادرشدهٔ دیگر لازم شود — در حالی که طرحِ ۶۲
     * هنوز کامیت نشده. اینجا کارِ یکسانی می‌کند بی آنکه چیزی به آن
     * زنجیره اضافه کند.
     *
     * **بی‌خطر در برابرِ اجرای دوباره:** بعد از یک بار، دیگر هیچ
     * سطرِ `MANUAL_CASH`ی روی آن دو حساب نمی‌ماند و شمارش صفر
     * می‌شود. پس اگر دو بار صدا زده شود، بارِ دوم هیچ نمی‌کند.
     *
     * @return تعداد سطرهایی که جابه‌جا شدند.
     */
    suspend fun repairManualCashClassification(): Int = db.atomic {
        val dao = db.journalDao()
        val stale = dao.countLines("MANUAL_CASH", Accounts.OTHER_INCOME) +
            dao.countLines("MANUAL_CASH", Accounts.EXPENSES)
        if (stale == 0) return@atomic 0

        val moved = dao.moveAccount("MANUAL_CASH", Accounts.OTHER_INCOME, Accounts.EQUITY) +
            dao.moveAccount("MANUAL_CASH", Accounts.EXPENSES, Accounts.EQUITY)

        audit(
            "اصلاح طبقه‌بندی نقدِ دستی",
            "$moved سطر از درآمد/هزینه به سرمایه رفت"
        )
        emit(
            type = "اصلاح طبقه‌بندی نقدِ دستی",
            aggregate = "journal",
            aggregateId = "MANUAL_CASH",
            payload = "سطر=$moved"
        )
        moved
    }

    /**
     * ورود/خروجِ دستیِ نقد — در برابرِ **سرمایه**، نه درآمد/هزینه.
     *
     * **اشکالی که این را عوض کرد.** تا امروز ورودِ دستی `4090`
     * (سایر درآمد) را بستانکار می‌کرد. هر کدِ حسابی که با `4` شروع
     * شود در صورتِ سود و زیان «درآمد» شمرده می‌شود، پس پولی که مالک
     * از جیبِ خودش در صندوق می‌گذاشت **فروش حساب می‌شد**.
     *
     * روی دفترِ واقعیِ کارگاه این ۵۰٬۰۰۰ بود روی ۱۶۶٬۵۱۰ فروش:
     * اپ سودِ ۴۴٬۱۳۱ نشان می‌داد در حالی که کارگاه ۵٬۸۶۹ **زیان**
     * داشت. علامت برعکس شده بود، نه فقط اندازه.
     *
     * پولی که مالک می‌گذارد آورده است و پولی که برمی‌دارد برداشت —
     * هر دو `3010`. تراز هم به‌هم نمی‌خورد: قبلاً از راهِ سود به
     * سرمایه می‌رسید، حالا مستقیم.
     *
     * **کسریِ واقعیِ صندوق اینجا نیست.** اگر روزی لازم شد، کارِ
     * جداگانه‌ای می‌خواهد با نامِ خودش؛ این دکمه در رابطِ کاربری
     * «افزایش کیف پول» است نه «اصلاحِ صندوق».
     *
     * @return false اگر پرداخت باشد و موجودیِ صندوق کافی نباشد.
     */
    suspend fun recordManualCash(
        source: String,
        amount: Long,
        isIn: Boolean,
        note: String
    ): Boolean = db.atomic {
        recordManualCashTx(source, amount, isIn, note)
    }

    private suspend fun recordManualCashTx(
        source: String,
        amount: Long,
        isIn: Boolean,
        note: String
    ): Boolean {
        if (amount <= 0) return false
        if (!isIn && !hasFunds(source, amount)) return false
        if (isIn) {
            income(source, amount, note)
            postJournal(
                note.ifBlank { "دریافت دستی" }, "MANUAL_CASH", "",
                listOf(
                    jl(Accounts.box(source), debit = amount),
                    jl(Accounts.EQUITY, credit = amount)
                )
            )
        } else {
            spend(source, amount, note)
            postJournal(
                note.ifBlank { "پرداخت دستی" }, "MANUAL_CASH", "",
                listOf(
                    jl(Accounts.EQUITY, debit = amount),
                    jl(Accounts.box(source), credit = amount)
                )
            )
        }
        return true
    }

    /** یکپارچه‌سازی WAL قبل از پشتیبان‌گیری فایل دیتابیس. */
    fun checkpoint() = db.checkpoint()

    /** بستنِ اتصالِ دیتابیس — فقط پیش از بازنویسیِ خودِ فایل. */
    fun closeDatabase() = db.closeConnection()

    /** نامِ همهٔ جدول‌های واقعیِ دیتابیس — برای وارسیِ فهرستِ ریست. */
    fun tableNames(): List<String> = db.tableNames()

    /**
     * پاک‌کردنِ کارها و حساب‌ها. اطلاعات پایه و مشتریان دست نمی‌خورند —
     * فهرستِ دقیقش در `ResetPlan` است.
     *
     * @return تعدادِ جدولی که خالی شد.
     */
    suspend fun resetOperationalData(): Int {
        val cleared = db.clearTables(ResetPlan.CLEAR)
        audit("ریست داده", "${cleared} جدول خالی شد؛ اطلاعات پایه و مشتریان ماندند")
        return cleared
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
     * پرداخت از صندوقِ انتخابی در بخش مالی عمومی ثبت می‌شود.
     *
     * [paySource] باید همان صندوقی باشد که موجودی‌اش کنترل شده است؛
     * وگرنه پول از صندوقی کم می‌شود که کاربر انتخابش نکرده بود.
     */
    suspend fun settleTailorWages(tailorLabel: String, total: Long, paySource: String = "WALLET") = db.atomic {
        settleTailorWagesTx(tailorLabel, total, paySource)
    }

    private suspend fun settleTailorWagesTx(
        tailorLabel: String,
        total: Long,
        paySource: String
    ) {
        db.tailorWageDao().settleForTailor(tailorLabel, System.currentTimeMillis())
        spend(paySource, total, "تسویه کارمزد خیاط $tailorLabel")
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
                jl(Accounts.box(paySource), credit = total)
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

    /**
     * تأمین‌کننده‌های ثبت‌شده — برای انتخاب به‌جای تایپِ دوباره.
     *
     * نامی که هر بار از نو نوشته می‌شود، با یک حرفِ متفاوت یک طرفِ حسابِ
     * جدا می‌سازد و بدهیِ یک نفر بین دو نام تکه می‌شود.
     */
    /**
     * بدهیِ خرج‌کارِ بی‌صاحب — باقی‌ماندهٔ پیش از اصلاح.
     *
     * تا پیش از این، خرج‌کار در ژورنال بدهی می‌شد ولی در دفترِ طرفِ حساب
     * چیزی ثبت نمی‌شد. پس آن بدهی نه معلوم بود به که، نه راهی برای
     * تسویه داشت. تفاضلِ این دو سمت همان مبلغِ بی‌صاحب است.
     *
     * سندهای تازه هر دو سمت را می‌زنند، پس در این تفاضل نمی‌آیند.
     */
    suspend fun orphanWorkCostPayable(): Long {
        val inJournal = db.journalDao().creditOfAccountByRef(Accounts.PAYABLE, "WORK_ITEMS")
        val inLedger = db.ledgerDao().creditByRef("WORK_ITEMS")
        return (inJournal - inLedger).coerceAtLeast(0)
    }

    /**
     * تسویهٔ بدهیِ خرج‌کارِ بی‌صاحب — یک‌بار، برای پاک کردنِ گذشته.
     *
     * این پول در واقعیت پرداخت شده بوده (دکمه و زیپ نسیه نمی‌مانَد)، ولی
     * اپ هرگز از صندوق کمش نکرده. پس اینجا همان پرداختِ عقب‌افتاده ثبت
     * می‌شود: بدهی صفر و صندوق به اندازهٔ واقعیت کم.
     *
     * @return false اگر چیزی برای تسویه نباشد یا صندوق کافی نباشد؛ در هر
     * دو حال هیچ چیز ثبت نمی‌شود.
     */
    suspend fun settleOrphanWorkCost(paySource: String): Boolean = db.atomic {
        settleOrphanWorkCostTx(paySource)
    }

    private suspend fun settleOrphanWorkCostTx(
        paySource: String
    ): Boolean {
        val amount = orphanWorkCostPayable()
        if (amount <= 0) return false
        val src = paySource.trim().uppercase()
        if (!hasFunds(src, amount)) return false

        spend(src, amount, "تسویهٔ خرج‌کارِ ثبت‌شدهٔ پیشین", "خرج کار")
        postJournal(
            "تسویهٔ خرج‌کارِ پیشین", "WORK_ITEMS_SETTLE", "",
            listOf(
                jl(Accounts.PAYABLE, debit = amount),
                jl(Accounts.box(src), credit = amount)
            )
        )
        audit("تسویه خرج‌کار پیشین", "$amount ؋ از $src")
        return true
    }

    fun observeSuppliers(): Flow<List<Party>> =
        db.ledgerDao().observeParties().map { list -> list.filter { it.type == "SUPPLIER" } }

    /**
     * ثبتِ تأمین‌کنندهٔ دایمی. اگر از قبل باشد چیزی عوض نمی‌شود.
     *
     * فقط وقتی صدا زده می‌شود که کاربر گفته باشد این طرف دایمی است؛
     * خریدِ یک‌بارهٔ سرِ کوچه نباید فهرست را شلوغ کند.
     */
    suspend fun rememberSupplier(name: String, phone: String = "") {
        val n = name.trim()
        if (n.isBlank()) return
        val exists = db.ledgerDao().observeParties().first()
            .any { it.type == "SUPPLIER" && it.name.trim().equals(n, ignoreCase = true) }
        if (exists) return
        db.ledgerDao().insertParty(Party(name = n, type = "SUPPLIER", phone = phone.trim()))
        audit("ثبت تأمین‌کننده", n)
    }

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

    /**
     * ثبتِ یک اتفاقِ واقع‌شده در صندوقِ خروجی.
     *
     * **باید داخلِ `db.atomic { … }` صدا زده شود.** کلِ ارزشِ این کار
     * همین است: رویداد و دادهٔ اصلی در یک تراکنش می‌نشینند، پس
     * نمی‌شود سندی ثبت شود و رویدادش نه — یا برعکس. اگر بیرون از
     * تراکنش صدا زده شود، همان مسئلهٔ «دو نوشتنِ جدا» برمی‌گردد که
     * مرزِ `Tx` برای بستنش ساخته شد. بررسیِ `eventtx` این را نگه
     * می‌دارد.
     *
     * فعلاً **هیچ‌کس این جدول را نمی‌خواند.** عمدی است و در نقشهٔ
     * مهاجرت گام ۳ نوشته شده: اول نوشتن راه بیفتد و چند هفته داده
     * جمع شود، بعد بازتابِ حسابرسی روی همان سوار شود. این‌طور اگر
     * شکلِ رویدادها غلط باشد، پیش از آنکه چیزی به آن تکیه کند معلوم
     * می‌شود.
     */
    private suspend fun emit(
        type: String,
        aggregate: String,
        aggregateId: String,
        payload: String = ""
    ) {
        db.domainEventDao().insert(
            DomainEvent(
                type = type,
                aggregate = aggregate,
                aggregateId = aggregateId,
                payload = payload,
                user = CurrentUser.name,
                role = CurrentUser.role
            )
        )
    }

    /**
     * هرسِ رویدادهای پردازش‌شدهٔ کهنه.
     *
     * [RETENTION_MONTHS] ماه — تصمیمِ کارگاه. دلیلش: دو سالِ مالی
     * کامل برای هر بازبینی یا اختلافِ حساب کافی است، و جدول بی‌مرز
     * رشد نمی‌کند.
     *
     * رویدادِ **پردازش‌نشده** هرچقدر هم کهنه باشد نگه داشته می‌شود؛
     * کهنه بودنش یعنی مصرف‌کننده‌ای نچرخیده، و پاک کردنش داده را از
     * بین می‌برد نه اینکه جا باز کند.
     */
    suspend fun pruneEvents(now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - RETENTION_MONTHS * 30L * 24 * 60 * 60 * 1000
        return db.domainEventDao().prune(cutoff)
    }

    // =========================
    // Double-entry journal (ژورنالِ حسابداری دوطرفه)
    // =========================

    fun observeAccountBalances(): Flow<List<com.afghanjama.data.dao.AccountBalance>> =
        db.journalDao().observeAccountBalances()

    /** ماندهٔ حساب‌ها در یک بازه — برای صورتِ سود و زیانِ دوره. */
    fun observeAccountBalancesBetween(from: Long, to: Long): Flow<List<com.afghanjama.data.dao.AccountBalance>> =
        db.journalDao().observeAccountBalancesBetween(from, to)

    // --- فقط-خواندنی، برای خودآزمایی ---

    suspend fun auditEntryTotals(): List<com.afghanjama.data.dao.EntryTotal> =
        db.journalDao().entryTotals()

    suspend fun auditAccountBalances(): List<com.afghanjama.data.dao.AccountBalance> =
        db.journalDao().observeAccountBalances().first()

    suspend fun auditLedgerEntries(): List<LedgerEntry> =
        db.ledgerDao().observeAllEntries().first()

    suspend fun auditFinishedStock(): List<com.afghanjama.data.entities.FinishedStock> =
        db.finishedStockDao().observeAll().first()

    suspend fun auditMaterialStock(): List<com.afghanjama.data.entities.MaterialStock> =
        db.materialStockDao().observeAll().first()

    /** همهٔ سطرهای صندوق — برای تطبیقِ جریانِ نقد با ژورنال. */
    suspend fun auditTransactions(): List<Transaction> =
        db.financeDao().observeTx().first()

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
        "SALARY_RECEIPT" -> "MA"     // معاش (حقوق ماهانه)
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
    ): Boolean = db.atomic {
        recordManualLedgerTx(type, name, amount, isPayment, paySource, note)
    }

    private suspend fun recordManualLedgerTx(
        type: String,
        name: String,
        amount: Long,
        isPayment: Boolean,
        paySource: String,
        note: String
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
                    settleTailorWages(name.trim(), amount, paySource)
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
                            "CUSTOMER" -> Accounts.CUSTOMER_PREPAY
                            // پیش‌پرداخت به کارمند هنوز هزینه نشده؛ طلبِ ماست
                            // تا با حقوقش تهاتر شود. اگر اینجا هزینه ثبت
                            // می‌شد، پرداختِ حقوق آن را دوباره می‌شمرد.
                            "EMPLOYEE", "INSPECTOR" -> Accounts.STAFF_ADVANCE
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
                        when (type) {
                            "CUSTOMER" -> Accounts.CUSTOMER_PREPAY
                            // کارمند پیش‌پرداختش را پس می‌دهد
                            "EMPLOYEE", "INSPECTOR" -> Accounts.STAFF_ADVANCE
                            else -> Accounts.OTHER_INCOME
                        },
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
        listOf(type, color).map { it.trim() }
            .filter { it.isNotEmpty() && !it.isPlaceholderDash() }
            .joinToString(" ")

    /**
     * «—» و «-» و «ندارد» جای خالی‌اند، نه رنگ.
     *
     * صفحه‌ها وقتی رنگی انتخاب نشده یک خط تیره نشان می‌دهند. آن خط تیره
     * تا امروز به نامِ انبار می‌چسبید و «مخمل سرخ —» می‌ساخت که با
     * «مخمل سرخ»ِ خریداری‌شده یکی نبود — پس برداشت به ردیفِ دیگری
     * می‌خورد و موجودی هرگز کم نمی‌شد.
     */
    private fun String.isPlaceholderDash(): Boolean {
        val s = trim()
        return s.isEmpty() ||
            s.all { it in "-–—_.،, " } ||
            s == "ندارد" || s == "بدون رنگ" || s == "نامشخص"
    }

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

    /**
     * حذفِ ردیفِ انبار — فقط وقتی **خالی** باشد.
     *
     * ردیفی که موجودی دارد ارزش هم دارد، و آن ارزش در حسابِ «موجودی
     * مواد» نشسته است. حذفِ مستقیمش یعنی جنس از انبار می‌پرد ولی حساب
     * سرِ جایش می‌مانَد و برای همیشه از واقعیت جدا می‌شود — همان انحرافی
     * که یک بار در همین پروژه پیدا شد و اصلاحش کارِ کمی نبود.
     *
     * پس اول باید با «ضایعات» یا «اصلاح موجودی» صفر شود؛ آن مسیر سندِ
     * حسابداری‌اش را هم می‌زند. بعد این حذف فقط ردیفِ خالی را برمی‌دارد.
     *
     * @return true اگر حذف شد.
     */
    suspend fun deleteMaterialStockIfEmpty(item: MaterialStock): Boolean {
        val cur = db.materialStockDao().find(item.name.trim(), item.unit.trim()) ?: return true
        if (cur.amount > 0.0) return false
        db.materialStockDao().deleteById(cur.id)
        audit("حذف ردیف انبار", "${cur.name} (${cur.unit})")
        return true
    }

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
     *
     * @return ارزشِ ریالیِ جابه‌جاشده (همیشه مثبت) بر اساسِ میانگینِ **همین
     * لحظه**. صداکننده باید همین عدد را ژورنال کند نه برآوردِ خودش —
     * وگرنه حسابِ مواد از ارزشِ واقعیِ انبار جدا می‌افتد. مقدارِ برگشتی
     * مقدارِ واقعاً جابه‌جاشده را در نظر می‌گیرد، پس اگر سقفِ صفر جلوی
     * بخشی از برداشت را بگیرد، ارزش هم به همان نسبت کمتر است.
     */
    suspend fun changeMaterialStock(
        name: String,
        unit: String,
        delta: Double,
        reason: String = "اصلاح",
        note: String = ""
    ): Long {
        if (delta == 0.0) return 0L
        val now = System.currentTimeMillis()

        /*
         * قلم با واحدِ دقیق پیدا نشد؟ با همان نام و هر واحدِ دیگری بگرد.
         *
         * انبار با کلیدِ «نام + واحد» نگه داشته می‌شود. اگر پارچه با «متر»
         * خریده شده باشد ولی سفارش واحدش را «یارد» یا خالی بگذارد، جست‌وجو
         * خطا می‌خورد و — بدتر از آن — یک ردیفِ صفرِ تازه ساخته می‌شد و
         * برداشت **بی‌صدا** هیچ اثری نمی‌گذاشت. موجودیِ اصلی دست‌نخورده
         * می‌ماند و کاربر هیچ خطایی نمی‌دید.
         *
         * حالا اول واحدِ دقیق، بعد همان نام با هر واحدی که موجودی دارد.
         */
        val exact = db.materialStockDao().find(name.trim(), unit.trim())
        val byNameOnly = if (exact == null && delta < 0) {
            db.materialStockDao().observeAll().first()
                .firstOrNull { it.name.trim() == name.trim() && it.amount > 0.0 }
        } else {
            null
        }
        // برداشت از قلمی که در انبار نیست، نباید ردیفِ **صفرِ تازه** بسازد.
        // همین کار بود که «مخمل سرخ —» را کنارِ «مخمل سرخ» می‌نشاند و
        // انبار را شلوغ می‌کرد، بی آنکه چیزی واقعاً کم شود.
        if (exact == null && byNameOnly == null && delta < 0) {
            audit(
                "برداشت از انبار انجام نشد",
                "«${name.trim()}» با واحد «${unit.trim()}» در انبار نیست — $reason $note"
            )
            return 0L
        }
        val cur = exact
            ?: byNameOnly
            ?: MaterialStock(name = name.trim(), unit = unit.trim(), amount = 0.0, updatedAt = now)
        val newAmount = (cur.amount + delta).coerceAtLeast(0.0)
        // آنچه واقعاً جابه‌جا شد؛ با سقفِ صفر می‌تواند کمتر از delta باشد
        val movedAmount = newAmount - cur.amount
        db.materialStockDao().upsert(cur.copy(amount = newAmount, updatedAt = now))
        logMovement(cur.name, cur.unit, delta, reason, note)

        // برداشتی که به جایی نخورد باید دیده شود، نه اینکه بی‌صدا رد شود.
        if (delta < 0 && movedAmount == 0.0) {
            audit(
                "برداشت از انبار انجام نشد",
                "«${name.trim()}» با واحد «${unit.trim()}» موجودی نداشت — $reason $note"
            )
        }
        if (reason == "اصلاح") audit("اصلاح دستی موجودی", "$name: $delta $unit")
        return kotlin.math.abs(movedAmount * cur.avgPrice).toLong()
    }

    /**
     * اصلاحِ موجودی یا ثبتِ ضایعات، **با سندِ حسابداری**.
     *
     * تا امروز این دو مسیر فقط مقدارِ انبار را عوض می‌کردند و هیچ سندی
     * نمی‌زدند؛ یعنی ارزشِ انبار تغییر می‌کرد ولی حسابِ «موجودی مواد» سرِ
     * جایش می‌ماند و برای همیشه از واقعیت جدا می‌شد. شمارشِ انبار و ضایعات
     * در کارگاه هر هفته پیش می‌آید، پس انحرافش هم مرتب بیشتر می‌شد.
     *
     * کم‌شدن هزینه است (ضایعات یا کسریِ شمارش) و زیادشدن هزینهٔ منفی
     * (چیزی که پیدا شده و ثبت نبوده) — همان رسمِ استانداردِ اصلاحِ انبار.
     */
    suspend fun adjustMaterialStock(
        name: String,
        unit: String,
        delta: Double,
        reason: String,
        note: String = ""
    ) {
        if (delta == 0.0) return
        val moved = changeMaterialStock(name, unit, delta, reason = reason, note = note)
        if (moved <= 0L) return
        postJournal(
            "$reason — ${name.trim()}", "MATERIAL_ADJUST", "",
            if (delta < 0) listOf(
                jl(Accounts.EXPENSES, debit = moved),
                jl(Accounts.MATERIALS, credit = moved)
            ) else listOf(
                jl(Accounts.MATERIALS, debit = moved),
                jl(Accounts.EXPENSES, credit = moved)
            )
        )
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
     * مبلغ کل از منبع انتخابی پرداخت می‌شود.
     *
     * @return false اگر خرید نقدی باشد و صندوق کافی نباشد، یا منبعِ
     * پرداخت پشتیبانی‌نشده باشد. در هر دو حالت **هیچ چیزی ثبت نمی‌شود**.
     */
    suspend fun recordPurchaseInvoice(
        invoice: PurchaseInvoice,
        items: List<PurchaseItem>
    ): Boolean = db.atomic {
        recordPurchaseInvoiceTx(invoice, items)
    }

    private suspend fun recordPurchaseInvoiceTx(
        invoice: PurchaseInvoice,
        items: List<PurchaseItem>
    ): Boolean {
        // «به حساب مشتری» هنوز پیاده نشده: پولی خارج نمی‌شد ولی سندش نقد را
        // بستانکار می‌کرد و مواد هم وارد انبار می‌شد — یعنی صندوقِ سند از
        // صندوقِ واقعی جدا می‌افتاد. تا پیاده شدنش، رد می‌شود؛ نیمه‌ثبت
        // بدتر از ثبت‌نشدن است. از صفحهٔ خرید قابلِ انتخاب نیست.
        if (invoice.paySource == "CUSTOMER") return false
        // خریدِ نقدی نباید صندوق را منفی کند. کنترل **پیش از** هر نوشتنی
        // انجام می‌شود، وگرنه فاکتور و مواد ثبت می‌شدند و فقط پولش نه.
        if (CashPolicy.isCashSource(invoice.paySource) &&
            !hasFunds(invoice.paySource, invoice.total)
        ) {
            return false
        }
        db.procurementDao().insertInvoice(invoice)
        db.procurementDao().insertItems(items)
        // بسته واحدِ **خرید** است، نه واحدِ مصرف. اگر تعدادِ داخلِ بسته
        // داده شده باشد، انبار عددی نگه داشته می‌شود تا برداشتِ روزانه با
        // همان چیزی حساب شود که واقعاً مصرف می‌گردد. مبلغ عوض نمی‌شود، پس
        // میانگینِ هر عدد خودش از تقسیمِ کلِ مبلغ بر تعدادِ کل درمی‌آید.
        items.forEach {
            val packed = it.perPack > 1
            addMaterialPurchase(
                name = it.name,
                unit = if (packed) "عدد" else it.unit,
                amount = if (packed) it.qty * it.perPack else it.qty,
                totalPrice = it.total
            )
        }
        if (invoice.total > 0) {
            when (invoice.paySource) {
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
        return true
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
    suspend fun settleSupplier(supplier: String, amount: Long, paySource: String, note: String) = db.atomic {
        settleSupplierTx(supplier, amount, paySource, note)
    }

    private suspend fun settleSupplierTx(
        supplier: String,
        amount: Long,
        paySource: String,
        note: String
    ) {
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

    /**
     * برگشتِ مواد به فروشنده — آینهٔ دقیقِ [recordPurchaseInvoice].
     *
     * مواد از انبار خارج و ارزشش از دارایی کم می‌شود. طرفِ مقابلِ سند
     * بستگی دارد به اینکه خرید نسیه بوده یا نقد:
     *  - [refundCash] = false → بدهیِ ما به فروشنده کم می‌شود (حالتِ نسیه)
     *  - [refundCash] = true  → فروشنده پول را پس می‌دهد و به صندوق می‌آید
     *
     * @return false اگر موجودیِ انبار کمتر از مقدارِ برگشتی باشد.
     */
    suspend fun recordPurchaseReturn(
        supplier: String,
        name: String,
        unit: String,
        qty: Double,
        amount: Long,
        refundCash: Boolean,
        cashBox: String = "WALLET",
        note: String = ""
    ): Boolean = db.atomic {
        recordPurchaseReturnTx(supplier, name, unit, qty, amount, refundCash, cashBox, note)
    }

    private suspend fun recordPurchaseReturnTx(
        supplier: String,
        name: String,
        unit: String,
        qty: Double,
        amount: Long,
        refundCash: Boolean,
        cashBox: String,
        note: String
    ): Boolean {
        val sup = supplier.trim().ifBlank { "نامشخص" }
        val item = name.trim()
        if (item.isEmpty() || qty <= 0.0 || amount <= 0) return false

        // نمی‌شود چیزی را برگرداند که در انبار نیست
        val stock = db.materialStockDao().find(item, unit.trim())?.amount ?: 0.0
        if (stock < qty) return false

        val memo = note.ifBlank { "برگشت $item به $sup" }

        // ارزشِ واقعیِ جنسی که از انبار بیرون رفت — نه مبلغی که فروشنده
        // پس می‌دهد. این دو می‌توانند فرق کنند (چانه‌زنی، نرخِ عوض‌شده) و
        // ژورنال‌کردنِ مبلغِ برگشتی حسابِ مواد را از انبار جدا می‌کرد.
        val stockValueOut = changeMaterialStock(
            item, unit, -qty, reason = "برگشت به فروشنده", note = memo
        )

        if (refundCash) {
            income(cashBox, amount, memo)
        } else {
            // کاهشِ بدهیِ ما — همان اثرِ پرداخت، ولی بدونِ خروجِ نقد
            db.supplierDao().insert(
                SupplierLedger(supplier = sup, amount = amount, type = "PAYMENT", note = memo)
            )
        }
        postLedger("SUPPLIER", sup, amount, 0, "PURCHASE_RETURN", note = memo)
        createDocument("RETURN", sup, amount, note = memo)
        audit("برگشت از خرید", "$sup — $item — $amount ؋")

        // مواد به ارزشِ واقعیِ خودش از انبار بیرون می‌رود؛ تفاوتش با مبلغِ
        // برگشتی سود یا زیانِ همین برگشت است و به هزینه‌ها می‌نشیند.
        val gap = amount - stockValueOut
        postJournal(
            memo, "PURCHASE_RETURN", "",
            buildList {
                add(jl(if (refundCash) Accounts.box(cashBox) else Accounts.PAYABLE, debit = amount))
                if (stockValueOut > 0) add(jl(Accounts.MATERIALS, credit = stockValueOut))
                if (gap > 0) add(jl(Accounts.EXPENSES, credit = gap))
                if (gap < 0) add(jl(Accounts.EXPENSES, debit = -gap))
            }
        )
        return true
    }

    // =========================
    // Finished Goods (انبار محصول نهایی + فروش جزئی)
    // =========================

    fun observeFinishedStock(): Flow<List<FinishedStock>> =
        db.finishedStockDao().observeAvailable()

    fun observeFinishedSales(): Flow<List<FinishedSale>> =
        db.finishedStockDao().observeSales()

    /** افزودن محصول تولیدشده به انبار با میانگین وزنی بهای تمام‌شده. */
    /**
     * ورودِ کالا به انبارِ محصول با **مبلغِ کلِ همان دسته**، نه میانگینِ هر عدد.
     *
     * قبلاً میانگینِ گردشده گرفته می‌شد و ته‌ماندهٔ تقسیم گم می‌شد؛ چون
     * ژورنال کلِ بهای تمام‌شده را بدهکار می‌کرد ولی فروش فقط
     * «تعداد × میانگینِ گردشده» را برمی‌گرداند، حسابِ موجودیِ محصول
     * حتی با انبارِ خالی هم به صفر برنمی‌گشت.
     */
    suspend fun addFinishedStock(name: String, size: String, qty: Int, batchValue: Long) = db.atomic {
        addFinishedStockTx(name, size, qty, batchValue)
    }

    private suspend fun addFinishedStockTx(
        name: String,
        size: String,
        qty: Int,
        batchValue: Long
    ) {
        if (qty <= 0) return
        val now = System.currentTimeMillis()
        val cur = db.finishedStockDao().find(name.trim(), size.trim())
            ?: FinishedStock(name = name.trim(), size = size.trim(), qty = 0, updatedAt = now)

        // اگر ردیف کسری داشت، بهای آن عددها قبلاً با برآورد به حساب رفته
        // بود. حالا بهای واقعی رسیده و اختلافِ برآورد باید اصلاح شود،
        // وگرنه ردیفی با تعدادِ صفر ارزشِ غیرِ صفر نگه می‌دارد.
        val variance = if (cur.qty < 0) {
            val covered = minOf(qty, -cur.qty)
            covered * batchValue / qty - covered * cur.avgCost
        } else 0L

        val newQty = cur.qty + qty
        val newValue = cur.totalValue + batchValue - variance
        db.finishedStockDao().upsert(
            cur.copy(
                qty = newQty,
                totalValue = newValue,
                // میانگین فقط برای نمایش است، ولی وقتی تعداد مثبت نیست
                // همان برآوردِ قبلی باید بماند: ارزشِ منفیِ ردیف بر همین
                // عدد بنا شده و صفر کردنش حساب را به‌هم می‌ریزد.
                avgCost = if (newQty > 0) newValue / newQty else cur.avgCost,
                updatedAt = now
            )
        )

        if (variance != 0L) {
            // اختلافِ برآورد به بهای تمام‌شدهٔ فروش می‌رود — چون خطا آنجا
            // رخ داده بود: کالایی فروخته شده و بهایش اشتباه برآورد شده.
            val label = "اصلاح بهای کسری «${name.trim()}»"
            postJournal(
                label, "SHORTAGE_ADJUST", "",
                if (variance > 0) listOf(
                    jl(Accounts.COGS, debit = variance),
                    jl(Accounts.FINISHED, credit = variance)
                ) else listOf(
                    jl(Accounts.FINISHED, debit = -variance),
                    jl(Accounts.COGS, credit = -variance)
                )
            )
            audit("اصلاح بهای کسری", "${name.trim()} — ${variance} ؋")
        }
    }

    /**
     * بهای تمام‌شدهٔ خروجِ [qty] عدد از یک ردیفِ انبار، و ارزشِ باقی‌مانده.
     *
     * وقتی آخرین عددها می‌روند، هرچه در ردیف مانده یک‌جا به بهای تمام‌شده
     * می‌رود — یعنی ارزشِ ردیف دقیقاً صفر می‌شود و هیچ ته‌مانده‌ای در
     * حسابِ موجودی جا نمی‌ماند.
     */
    private fun takeFromStock(item: FinishedStock, qty: Int): Pair<Long, Long> {
        if (qty <= 0) return 0L to item.totalValue
        val available = item.qty.coerceAtLeast(0)
        if (qty <= available) {
            if (qty >= item.qty) return item.totalValue to 0L
            val cost = item.totalValue * qty / item.qty
            return cost to (item.totalValue - cost)
        }
        // کسری: بیشتر از موجودی رفت. آنچه واقعاً در انبار بود کلِ ارزشش را
        // می‌برد، و برای عددهای اضافه بهای تمام‌شده با آخرین میانگینِ معلوم
        // برآورد می‌شود. ارزشِ ردیف منفی می‌ماند، یعنی «به اندازهٔ این مبلغ
        // جنسی فروخته‌ایم که هنوز واردش نکرده‌ایم» — و با ورودِ بعدی خودش
        // تسویه می‌شود.
        val fromStock = if (available > 0) item.totalValue else 0L
        val excess = qty - available
        val cost = fromStock + excess * item.avgCost
        return cost to (item.totalValue - cost)
    }

    /**
     * شمارشِ انبارِ محصول: تعدادِ واقعی را می‌نشاند، **با سندِ حسابداری**.
     *
     * تا امروز هیچ راهی نبود که یک ردیفِ انبارِ محصول اصلاح یا حذف شود.
     * وقتی یک بار عددِ غلط وارد انبار شده بود (مثلِ همان «۱۰ عدد برش
     * خورد، ۵ تا دوخته شد، ۱۰ تا وارد انبار شد») آن عددهای خیالی برای
     * همیشه می‌ماندند: در فهرستِ فروش دیده می‌شدند، در «انبار محصول»
     * داشبورد شمرده می‌شدند، و ارزششان در حسابِ ۱۰۵۰ می‌نشست.
     *
     * فقط کم کردنِ تعداد کافی نیست. ارزشِ آن عددها هنگامِ ورود بدهکارِ
     * «موجودی محصول» شده؛ اگر تعداد برود و سند نه، دفتر و انبار برای
     * همیشه از هم جدا می‌مانند — همان چیزی که خودآزمایی در بررسیِ
     * «حسابِ موجودی محصول = ارزشِ واقعیِ انبار» می‌گیرد.
     *
     * پس کم‌شدن هزینه است (کسریِ شمارش) و زیادشدن هزینهٔ منفی — همان
     * رسمی که [adjustMaterialStock] برای موادِ خام دارد.
     *
     * ارزش‌گذاری از [takeFromStock] می‌آید، پس وقتی ردیف به صفر می‌رسد
     * هرچه در آن مانده یک‌جا خارج می‌شود و هیچ ته‌مانده‌ای در حساب جا
     * نمی‌مانَد. ردیفِ صفرشده خودبه‌خود از فهرست ناپدید می‌شود چون
     * `observeAvailable()` فقط ردیف‌های ناصفر را می‌دهد؛ ولی خودِ سطر
     * پاک نمی‌شود تا تاریخچه‌اش بی‌صاحب نشود.
     *
     * @param countedQty تعدادی که واقعاً در انبار شمرده شده
     * @return false اگر ردیف پیدا نشود یا چیزی برای اصلاح نباشد
     */
    suspend fun adjustFinishedStock(
        name: String,
        size: String,
        countedQty: Int,
        note: String = ""
    ): Boolean = db.atomic {
        adjustFinishedStockTx(name, size, countedQty, note)
    }

    private suspend fun adjustFinishedStockTx(
        name: String,
        size: String,
        countedQty: Int,
        note: String
    ): Boolean {
        val cur = db.finishedStockDao().find(name.trim(), size.trim()) ?: return false
        val target = countedQty.coerceAtLeast(0)

        val newValue: Long
        if (target < cur.qty) {
            // کسری: بهای عددهای رفته از همان قاعدهٔ فروش درمی‌آید
            val (_, left) = takeFromStock(cur, cur.qty - target)
            newValue = left
        } else if (target > cur.qty) {
            // اضافه پیدا شده: با آخرین میانگینِ معلوم ارزش می‌گیرد
            newValue = cur.totalValue + (target - cur.qty) * cur.avgCost
        } else {
            // تعداد درست است ولی ممکن است ارزشِ ته‌مانده روی ردیفِ خالی
            // جا مانده باشد؛ آن هم باید صفر شود وگرنه دفتر نمی‌خوانَد.
            newValue = if (target == 0) 0L else cur.totalValue
        }

        val valueDelta = newValue - cur.totalValue
        if (target == cur.qty && valueDelta == 0L) return false

        val now = System.currentTimeMillis()
        db.finishedStockDao().upsert(
            cur.copy(
                qty = target,
                totalValue = newValue,
                avgCost = if (target > 0) newValue / target else cur.avgCost,
                updatedAt = now
            )
        )

        val label = "شمارش انبار محصول — ${name.trim()}" +
            if (size.isNotBlank()) " (${size.trim()})" else ""
        if (valueDelta != 0L) {
            postJournal(
                label, "FINISHED_ADJUST", "",
                if (valueDelta < 0) listOf(
                    jl(Accounts.EXPENSES, debit = -valueDelta),
                    jl(Accounts.FINISHED, credit = -valueDelta)
                ) else listOf(
                    jl(Accounts.FINISHED, debit = valueDelta),
                    jl(Accounts.EXPENSES, credit = valueDelta)
                )
            )
        }
        audit(
            "اصلاح موجودی محصول",
            "${name.trim()}: از ${cur.qty} به $target عدد" +
                (if (valueDelta != 0L) " — ${-valueDelta} ؋" else "") +
                (if (note.isNotBlank()) " • ${note.trim()}" else "")
        )
        return true
    }

    /**
     * ورودِ [batch] عدد از یک سفارش به انبار محصول نهایی — نه کلِ سفارش.
     *
     * این همان جایی است که «۱۰ عدد برش خورد، ۵ تا دوخته شد، ۱۰ تا وارد
     * انبار شد» اتفاق می‌افتاد. حالا فقط همان عددهایی وارد می‌شوند که
     * نظارت را گذرانده‌اند، با سهمِ بهای خودشان.
     *
     * سفارش تنها وقتی به STORED (بایگانیِ تولید) می‌رود که همهٔ عددهایش
     * از این مسیر گذشته باشند؛ وگرنه به «دوخت» برمی‌گردد و منتظرِ دستهٔ
     * بعدی می‌ماند.
     */
    suspend fun depositBatchToFinished(order: Order, batch: Int) = db.atomic {
        depositBatchToFinishedTx(order, batch)
    }

    private suspend fun depositBatchToFinishedTx(
        order: Order,
        batch: Int
    ) {
        if (batch <= 0) return
        val batchValue = PartialFlow.depositValue(
            qty = order.qty,
            stored = order.storedQty,
            batch = batch,
            fixedCost = order.fabricPrice + order.workCost,
            sewnCost = order.sewingCost,
            batches = sewnBatchesOfOrder(order.id.toString()),
            storedCost = order.storedCost
        )
        // مبلغِ کامل می‌رود، نه میانگینِ گردشده — همان عددی که ژورنال بدهکار می‌کند
        addFinishedStock(order.designTitle, order.size, batch, batchValue)

        val stored = order.storedQty + batch
        val complete = stored >= order.qty
        changeOrderStatus(
            order,
            if (complete) OrderStatus.STORED.name else OrderStatus.SEWING.name
        ) {
            it.copy(
                storedQty = stored,
                storedCost = it.storedCost + batchValue,
                reviewQty = 0
            )
        }
        // ژورنال: بهای تمام‌شده از «کار در جریان» به «موجودی محصول» می‌رود؛
        // اگر برگشتی از فروش باشد (SENT)، از «بهای تمام‌شدهٔ فروش» برمی‌گردد.
        if (batchValue > 0) {
            val from = if (order.status == OrderStatus.SENT.name) Accounts.COGS else Accounts.WIP
            postJournal(
                "ورود به انبار محصول ${order.orderCode}", "TO_FINISHED", order.orderCode,
                listOf(
                    jl(Accounts.FINISHED, debit = batchValue),
                    jl(from, credit = batchValue)
                )
            )
        }
        // سفارش تبدیل به موجودیِ بی‌نامِ انبار شد؛ بدهیِ ازپیش‌ثبت‌شدهٔ مشتری
        // (SALE_BILLING هنگام ثبت سفارش) خنثی می‌شود — فروشِ واقعی هنگام
        // فروش از انبار محصول حساب و صورت‌حساب می‌شود.
        //
        // فقط یک بار، وقتی سفارش کامل شد: بدهی برای کلِ سفارش ثبت شده بود،
        // پس خنثی‌کردنش هم یک‌جاست، وگرنه هر دستهٔ جزئی یک بار کاملش را
        // بستانکار می‌کند و حسابِ مشتری چند برابر می‌شود.
        if (complete && order.customerName.isNotBlank() && order.agreedPrice > 0) {
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
    /**
     * تحویلِ سفارشِ آمادهٔ یک مشتری به خودِ او — آخرین قدمِ چرخهٔ کار.
     *
     * تا حالا سفارشِ تأییدشده به موجودیِ بی‌نامِ انبار تبدیل می‌شد و بعد
     * باید کسی همان لباس را در انبار پیدا می‌کرد و دوباره به همان مشتری
     * «می‌فروخت»؛ نام و قیمتِ توافقی دو بار تایپ می‌شد و وضعیتِ «تحویل شد»
     * هیچ‌وقت روی سفارش نمی‌نشست.
     *
     * اینجا همان فروش انجام می‌شود، ولی از روی خودِ سفارش: تعداد، مشتری و
     * قیمت از سفارش می‌آیند و در پایان سفارش «تحویل شد» می‌شود. حساب‌ها
     * دقیقاً از مسیرِ [sellFinished] می‌گذرند تا هیچ راهِ دومی برای پول
     * ساخته نشود.
     *
     * @return پیغامِ خطا، یا null اگر انجام شد.
     */
    /** موجودیِ انبارِ محصول برای طرح و سایزِ یک سفارش — ۰ اگر ردیفی نباشد. */
    suspend fun stockForOrder(order: Order): Int =
        db.finishedStockDao().find(order.designTitle, order.size)?.qty ?: 0

    suspend fun deliverOrderToCustomer(
        order: Order,
        qty: Int,
        unitPrice: Long,
        receivedNow: Long,
        applyPrepay: Long
    ): String? {
        if (order.customerName.isBlank())
            return "این سفارش مشتری ندارد؛ از انبار محصول بفروشید."
        if (order.status != OrderStatus.STORED.name)
            return "فقط سفارشی که نظارت را گذرانده و در انبار محصول است تحویل می‌شود."
        if (unitPrice <= 0) return "قیمت را وارد کنید."

        val remaining = (order.qty - order.deliveredQty).coerceAtLeast(0)
        if (remaining <= 0) return "همهٔ این سفارش قبلاً تحویل شده."
        if (qty <= 0) return "تعداد را وارد کنید."
        if (qty > remaining)
            return "از این سفارش فقط ${remaining} عدد باقی مانده."

        val allowShortage = SalePolicy.allowNegativeStock()
        var item = db.finishedStockDao().find(order.designTitle, order.size)

        if (item == null) {
            if (!allowShortage) {
                return "«${order.designTitle}» در انبار محصول موجود نیست. " +
                    "موجودیِ انبار برای هر طرح مشترک است، پس ممکن است با فروش یا " +
                    "تحویلِ دیگری خالی شده باشد."
            }
            // با اجازهٔ کسری، ردیفِ صفر ساخته می‌شود تا کسری جایی ثبت شود
            // و بعداً با ورودِ جنس تسویه گردد.
            db.finishedStockDao().upsert(
                FinishedStock(
                    name = order.designTitle.trim(),
                    size = order.size.trim(),
                    qty = 0,
                    updatedAt = System.currentTimeMillis()
                )
            )
            item = db.finishedStockDao().find(order.designTitle, order.size)
                ?: return "ساختِ ردیفِ انبار انجام نشد."
        }

        if (!allowShortage) {
            if (item.qty <= 0) {
                return "«${order.designTitle}» در انبار محصول موجود نیست. " +
                    "موجودیِ انبار برای هر طرح مشترک است، پس ممکن است با فروش یا " +
                    "تحویلِ دیگری خالی شده باشد."
            }
            if (item.qty < qty)
                return "موجودی انبار ${item.qty} عدد است، کمتر از ${qty} عددِ خواسته‌شده."
        }

        val ok = sellInvoice(
            lines = listOf(SaleLine(item, qty, unitPrice)),
            customerName = order.customerName,
            receivedNow = receivedNow,
            applyPrepay = applyPrepay
        )
        if (!ok) return "ثبتِ فروش انجام نشد."

        val delivered = order.deliveredQty + qty
        val fullyDelivered = delivered >= order.qty
        if (fullyDelivered) {
            changeOrderStatus(order, OrderStatus.SENT.name) { it.copy(deliveredQty = delivered) }
        } else {
            // هنوز باقی دارد — در صفِ تحویل می‌ماند
            db.orderDao().update(order.copy(deliveredQty = delivered))
        }
        audit(
            "تحویل سفارش به مشتری",
            "${order.orderCode} — ${order.customerName} — ${qty} عدد — ${(unitPrice * qty)} ؋" +
                if (fullyDelivered) "" else " (باقی: ${order.qty - delivered})"
        )
        return null
    }

    /**
     * پیش‌دریافتِ استفاده‌نشدهٔ یک مشتری — بیعانه‌هایی که گرفته‌ایم منهای
     * آنچه تا حالا روی فروش‌ها اعمال شده. برای پیشنهادِ خودکار در فروش.
     */
    suspend fun customerPrepayBalance(customerName: String): Long {
        val name = customerName.trim()
        if (name.isBlank()) return 0L
        val rows = db.ledgerDao().observeEntriesForParty("CUSTOMER", name).first()
        val paid = rows.filter { it.refType == "CUSTOMER_ADVANCE" }.sumOf { it.credit }
        val used = rows.filter { it.refType == "PREPAY_APPLIED" }.sumOf { it.debit }
        return (paid - used).coerceAtLeast(0L)
    }

    /** یک ردیفِ فاکتور فروش: کدام کالا، چند عدد، به چه قیمتی. */
    data class SaleLine(
        val item: FinishedStock,
        val qty: Int,
        val unitPrice: Long,
        /** تخفیفِ همین ردیف؛ هرگز بیشتر از خودِ ردیف نمی‌شود. */
        val discount: Long = 0
    ) {
        /** تخفیفِ مؤثر — بیشتر از مبلغِ ردیف پذیرفته نمی‌شود، وگرنه فروش منفی می‌شد. */
        val appliedDiscount: Long get() = discount.coerceIn(0L, qty * unitPrice)

        val total: Long get() = qty * unitPrice - appliedDiscount
    }

    /**
     * فاکتور فروش با هر تعداد ردیف.
     *
     * یک فاکتور می‌تواند چند طرحِ متفاوت داشته باشد — همان‌طور که در
     * دنیای واقعی مشتری یک پیراهن و دو واسکت با هم می‌خرد. همهٔ ردیف‌ها
     * یک `code` مشترک می‌گیرند، پس یک فاکتورند؛ ولی هر ردیف سطرِ خودش را
     * در `finished_sales` دارد تا برگشت از فروش بتواند فقط همان ردیف را
     * برگرداند.
     *
     * پول یک بار روی جمعِ کلِ فاکتور تفکیک می‌شود (بیعانه/نقد/نسیه)، یک
     * سندِ ژورنال و یک فاکتور ساخته می‌شود — نه یکی برای هر ردیف.
     *
     * [receivedNow] = -1 یعنی «همه نقد».
     */
    /*
     * پوستهٔ تراکنش. بدنهٔ اصلی دست‌نخورده در `sellInvoiceTx` است.
     *
     * **چرا بدنه جدا شد و مستقیم پیچیده نشد:** `atomic` یک لامبدای
     * غیرِ inline می‌گیرد، پس `return false`های داخلِ بدنه — که وارسیِ
     * ورودی‌اند — کامپایل نمی‌شدند. جدا کردنِ بدنه معنای `return` را
     * دقیقاً حفظ می‌کند و هیچ خطی از منطق را تغییر نمی‌دهد.
     *
     * سودِ جانبی: `…Tx` همان چیزی است که در نقشهٔ معماری قرار است
     * فردا به `CommandHandler` تبدیل شود.
     */
    suspend fun sellInvoice(
        lines: List<SaleLine>,
        customerName: String,
        receivedNow: Long = -1L,
        applyPrepay: Long = 0L
    ): Boolean = db.atomic {
        sellInvoiceTx(lines, customerName, receivedNow, applyPrepay)
    }

    private suspend fun sellInvoiceTx(
        lines: List<SaleLine>,
        customerName: String,
        receivedNow: Long,
        applyPrepay: Long
    ): Boolean {
        val valid = lines.filter { it.qty > 0 && it.unitPrice > 0 }
        if (valid.isEmpty()) return false
        // موجودی را برای ردیف‌های تکراریِ یک کالا هم با هم می‌سنجیم،
        // وگرنه دو ردیف از یک طرح می‌توانستند بیشتر از موجودی بفروشند.
        val neededPerItem = valid.groupBy { it.item.id }
            .mapValues { (_, rows) -> rows.sumOf { it.qty } }
        if (!SalePolicy.allowNegativeStock()) {
            valid.map { it.item }.distinctBy { it.id }.forEach { item ->
                if ((neededPerItem[item.id] ?: 0) > item.qty) return false
            }
        }

        val now = System.currentTimeMillis()
        val revenue = valid.sumOf { it.total }
        val code = CodeGen.makePurchaseCode().replaceFirst("KH", "FR")
        val customer = customerName.trim()

        // کسرِ موجودی و برداشتِ ارزش: یک بار برای هر کالا، به اندازهٔ جمعِ
        // ردیف‌هایش. بهای تمام‌شده از ارزشِ واقعیِ ردیف برداشته می‌شود، نه
        // از میانگینِ گردشده، تا حسابِ موجودی ته‌مانده نگه ندارد.
        val costPerItem = mutableMapOf<Long, Long>()
        valid.map { it.item }.distinctBy { it.id }.forEach { item ->
            val taken = neededPerItem[item.id] ?: 0
            val (takenValue, leftValue) = takeFromStock(item, taken)
            costPerItem[item.id] = takenValue
            val leftQty = item.qty - taken
            db.finishedStockDao().upsert(
                item.copy(
                    qty = leftQty,
                    totalValue = leftValue,
                    // با تعدادِ صفر یا منفی، برآوردِ قبلی نگه داشته می‌شود:
                    // ارزشِ منفیِ کسری بر همین عدد بنا شده و صفر کردنش
                    // رابطهٔ «ارزش = تعداد × برآورد» را می‌شکند.
                    avgCost = if (leftQty > 0) leftValue / leftQty else item.avgCost,
                    updatedAt = now
                )
            )
        }
        val cost = costPerItem.values.sum()
        val profit = revenue - cost

        // هر ردیف سطرِ خودش را دارد، با کدِ مشترکِ فاکتور. بهای تمام‌شدهٔ
        // برداشته‌شده بینِ ردیف‌های همان کالا پخش می‌شود و ته‌ماندهٔ تقسیم
        // به آخرین ردیف می‌رود، تا جمعِ سطرها دقیقاً همان `cost` باشد.
        val remainingCost = costPerItem.toMutableMap()
        val remainingQty = neededPerItem.toMutableMap()
        valid.forEach { line ->
            val id = line.item.id
            val leftQty = remainingQty[id] ?: line.qty
            val leftCost = remainingCost[id] ?: 0L
            val lineCost = if (line.qty >= leftQty) leftCost else leftCost * line.qty / leftQty
            remainingCost[id] = leftCost - lineCost
            remainingQty[id] = leftQty - line.qty
            db.finishedStockDao().insertSale(
                FinishedSale(
                    code = code,
                    productName = line.item.name,
                    size = line.item.size,
                    qty = line.qty,
                    unitPrice = line.unitPrice,
                    total = line.total,
                    cost = lineCost,
                    customerName = customer,
                    discount = line.appliedDiscount
                )
            )
        }

        // تفکیکِ پول: نقدِ همین حالا + بیعانهٔ اعمال‌شده + باقی‌ماندهٔ طلب.
        // receivedNow = -1 یعنی صداکنندهٔ قدیمی چیزی نگفته → همه نقد.
        val prepay = applyPrepay.coerceIn(0L, revenue)
        val cashIn = (if (receivedNow < 0) revenue - prepay else receivedNow)
            .coerceIn(0L, revenue - prepay)
        val onCredit = revenue - prepay - cashIn

        val what = if (valid.size == 1) "«${valid.first().item.name}»"
        else "${valid.size} قلم کالا"

        if (cashIn > 0) income("WALLET", cashIn, "فروش $what ($code)")
        if (prepay > 0 && customer.isNotBlank()) {
            // بیعانه مصرف شد — تا دوباره روی فروشِ بعدی پیشنهاد نشود.
            //
            // عمداً بدهکار و بستانکارش برابر است، یعنی ماندهٔ حساب را تکان
            // نمی‌دهد. بیعانه لحظهٔ گرفتنش یک بار بستانکار شده و صورت‌حسابِ
            // همین فروش هم کلِ مبلغ را بدهکار می‌کند؛ پس خودِ بیعانه همان‌جا
            // تسویه می‌شود. اگر این سطر فقط بدهکار می‌بود، بیعانه دو بار از
            // مشتری گرفته می‌شد و کسی که کامل تسویه کرده باز بدهکار می‌ماند.
            postLedger(
                "CUSTOMER", customer, prepay, prepay,
                "PREPAY_APPLIED", code, "اعمال بیعانه روی فروش (تسویهٔ داخلی)"
            )
        }
        // بدهکارِ فروش در دفتر کل (متقابلِ دریافتی) تا حساب مشتری تراز بماند؛
        // فروشِ بی‌نام طرفِ حساب ندارد و آینه نمی‌شود (دریافتی‌اش هم نمی‌شود).
        if (customer.isNotBlank()) {
            postLedger("CUSTOMER", customer, revenue, 0, "SALE_BILLING", code, "فروش از انبار")
        }
        if (cashIn > 0) {
            addCustomerPayment(
                CustomerPayment(
                    // کدِ همین فاکتور، نه یک برچسبِ ثابت: دفتر کل این را
                    // به‌عنوان مرجعِ سطر ثبت می‌کند و «بدهی قبلی» روی
                    // فاکتور فقط وقتی درست درمی‌آید که پرداختِ همین فاکتور
                    // از ماندهٔ قبلی قابلِ جدا کردن باشد.
                    orderId = code,
                    customerName = customer,
                    amount = cashIn,
                    source = "SALE",
                    note = "فروش $what از انبار محصول"
                )
            )
        }
        // سود فقط تا سقفِ نقدِ واقعاً دریافت‌شده به صندوق فایده می‌رود؛
        // وگرنه صندوق برای پولی که هنوز نرسیده خالی می‌شد.
        val profitMove = profit.coerceAtMost(cashIn).coerceAtLeast(0L)
        if (profitMove > 0L) {
            // برچسبِ داخلی لازم است: بی آن، سودِ **هر فروش** در گزارش هم
            // ورودیِ نقد شمرده می‌شد و هم خروجیِ نقد، و زیرِ دستهٔ «سایر»
            // به‌عنوان هزینه دیده می‌شد.
            spend(
                "WALLET", profitMove, "انتقال سود فروش $what به فایده",
                category = CashPolicy.INTERNAL_MOVE
            )
            income(
                "PROFIT", profitMove, "سود فروش $what",
                category = CashPolicy.INTERNAL_MOVE
            )
        }
        // فاکتور فروش
        createDocument("SALE", customer, revenue, code, "فروش $what")
        audit("فروش از انبار", "$code — $revenue ؋ — ${valid.size} ردیف")
        // ژورنال: درآمدِ فروش + خروجِ بهای تمام‌شده + انتقالِ سود به صندوق فایده
        postJournal(
            "فروش $what ($code)", "SALE", code,
            listOf(
                jl(Accounts.CASH, debit = cashIn),
                // بیعانه‌ای که قبلاً بدهیِ ما بود، حالا با تحویلِ کالا آزاد می‌شود
                jl(Accounts.CUSTOMER_PREPAY, debit = prepay),
                // باقی‌مانده طلبِ ما از مشتری است
                jl(Accounts.RECEIVABLE, debit = onCredit),
                jl(Accounts.SALES, credit = revenue),
                jl(Accounts.COGS, debit = cost),
                jl(Accounts.FINISHED, credit = cost)
            )
        )
        if (profitMove > 0L) {
            postJournal(
                "انتقال سود فروش ($code) به صندوق فایده", "PROFIT_MOVE", code,
                listOf(
                    jl(Accounts.PROFIT_BOX, debit = profitMove),
                    jl(Accounts.CASH, credit = profitMove)
                )
            )
        }
        return true
    }

    /**
     * فروشِ تک‌ردیفی — پوستهٔ نازکی روی [sellInvoice] تا صفحه‌های موجود
     * دست‌نخورده کار کنند و هرگز دو مسیرِ جدا برای پول وجود نداشته باشد.
     */
    suspend fun sellFinished(
        item: FinishedStock,
        qty: Int,
        unitPrice: Long,
        customerName: String,
        receivedNow: Long = -1L,
        applyPrepay: Long = 0L,
        discount: Long = 0L
    ): Boolean {
        if (qty <= 0 || unitPrice <= 0) return false
        if (qty > item.qty && !SalePolicy.allowNegativeStock()) return false
        return sellInvoice(
            lines = listOf(SaleLine(item, qty, unitPrice, discount)),
            customerName = customerName,
            receivedNow = receivedNow,
            applyPrepay = applyPrepay
        )
    }

    /**
     * برگشت از فروش — آینهٔ دقیقِ [sellFinished].
     *
     * کالا با همان بهای تمام‌شدهٔ فروش به انبار محصول برمی‌گردد و پولِ
     * مشتری یا نقد پس داده می‌شود ([refundCash] = true) یا به‌صورت
     * بستانکاری روی حسابش می‌ماند. سودی که هنگام فروش به «صندوق فایده»
     * منتقل شده بود، به همان نسبت برمی‌گردد.
     *
     * برگشتِ جزئی مجاز است؛ مجموع برگشت‌ها هرگز از تعدادِ فروخته‌شده
     * بیشتر نمی‌شود (کنترلِ سقف داخلِ خودِ UPDATE است).
     *
     * @return false اگر تعداد نامعتبر باشد، سقفِ برگشت پر شده باشد، یا
     *         موجودیِ صندوق برای پس‌دادنِ نقد کافی نباشد.
     */
    suspend fun recordSaleReturn(
        sale: FinishedSale,
        qty: Int,
        refundCash: Boolean,
        cashBox: String = "WALLET"
    ): Boolean = db.atomic {
        recordSaleReturnTx(sale, qty, refundCash, cashBox)
    }

    private suspend fun recordSaleReturnTx(
        sale: FinishedSale,
        qty: Int,
        refundCash: Boolean,
        cashBox: String
    ): Boolean {
        if (qty <= 0 || qty > sale.returnableQty) return false

        // سهمی حساب می‌شود نه «تعداد × فی»: ردیفِ تخفیف‌دار وگرنه بیشتر از
        // چیزی که مشتری داده بود پس می‌گرفت، و تقسیمِ صحیح هم هر بار چند
        // افغانی جا می‌گذاشت.
        val refund = sale.refundFor(qty)
        val costBack = sale.costFor(qty)

        // پولی که نداریم نمی‌توانیم پس بدهیم — قبل از هر تغییری کنترل شود
        if (refundCash && balanceOf(cashBox) < refund) return false

        // سقفِ برگشت را خودِ دیتابیس تضمین می‌کند؛ اگر جای خالی نبود، هیچ
        // اثرِ دیگری هم ثبت نمی‌شود.
        if (db.finishedStockDao().addReturnedQty(sale.id, qty) == 0) return false

        val customer = sale.customerName.trim()
        val code = "BR-" + sale.code
        val memo = "برگشت $qty عدد «${sale.productName}» از فروش ${sale.code}"

        // کالا با همان بهای تمام‌شده به انبار محصول برمی‌گردد
        addFinishedStock(sale.productName, sale.size, qty, costBack)

        if (refundCash) spend(cashBox, refund, memo, category = "برگشتی فروش")

        if (customer.isNotBlank()) {
            // فروش خنثی می‌شود: مبلغ بستانکارِ مشتری می‌شود (طلبِ او از ما)
            postLedger("CUSTOMER", customer, 0, refund, "SALE_RETURN", code, memo)
            // اگر نقد پس دادیم، همان‌جا تسویه می‌شود و مانده صفر می‌ماند
            if (refundCash) {
                postLedger("CUSTOMER", customer, refund, 0, "RETURN_REFUND", code, memo)
            }
        }
        createDocument("RETURN", customer, refund, code, memo)
        audit("برگشت از فروش", "${sale.code} — $refund ؋")

        // ژورنال: برگشتِ درآمد + برگشتِ کالا از بهای تمام‌شده به انبار
        postJournal(
            memo, "SALE_RETURN", code,
            listOf(
                jl(Accounts.SALES, debit = refund),
                jl(
                    if (refundCash) Accounts.box(cashBox) else Accounts.CUSTOMER_PREPAY,
                    credit = refund
                ),
                jl(Accounts.FINISHED, debit = costBack),
                jl(Accounts.COGS, credit = costBack)
            )
        )

        // سودِ منتقل‌شده به «فایده» به همان نسبت برمی‌گردد — اما هرگز
        // بیش از موجودیِ خودِ صندوقِ فایده، تا منفی نشود.
        if (sale.total > sale.cost) {
            val movable = minOf(
                (refund - costBack).coerceAtLeast(0),
                balanceOf("PROFIT").coerceAtLeast(0)
            )
            if (movable > 0) {
                spend(
                    "PROFIT", movable, "برگشت سود فروش «${sale.productName}» ($code)",
                    category = CashPolicy.INTERNAL_MOVE
                )
                income(
                    "WALLET", movable, "برگشت سود فروش ($code)",
                    category = CashPolicy.INTERNAL_MOVE
                )
                postJournal(
                    "برگشت سود فروش ($code) از صندوق فایده", "PROFIT_MOVE", code,
                    listOf(
                        jl(Accounts.CASH, debit = movable),
                        jl(Accounts.PROFIT_BOX, credit = movable)
                    )
                )
            }
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

    /**
     * پیشنهادِ نامِ «مسئول برش»: کسانی که قبلاً برش زده‌اند، به‌علاوهٔ
     * کارکنانی که سمت‌شان برش است. عمداً خیاطان اینجا نمی‌آیند — برشکار
     * کارِ دیگری است و آوردنِ فهرستِ خیاط فقط اشتباه‌انداز بود.
     *
     * نامی که یک‌بار تایپ شود از دفعهٔ بعد خودش در این فهرست است، چون
     * از روی رکوردهای برشِ ثبت‌شده ساخته می‌شود.
     */
    fun observeCutterNames(): Flow<List<String>> =
        combine(
            db.cuttingRecordDao().observeCutters(),
            db.masterDataDao().observeStaff()
        ) { past, staff ->
            val fromStaff = staff
                .filter { it.role.contains("برش") }
                .map { it.name.trim() }
            (past.map { it.trim() } + fromStaff)
                .filter { it.isNotBlank() }
                .distinct()
        }

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

    /**
     * دستهٔ یک طرح — همان پوشه‌ای که کالاهایش در انبارِ محصول می‌نشینند.
     * خالی یعنی «دسته‌بندی‌نشده».
     */
    suspend fun setDesignCategory(id: Long, category: String) {
        db.masterDataDao().setDesignCategory(id, category.trim())
        audit("دستهٔ طرح", "#$id → ${category.trim().ifBlank { "بی‌دسته" }}")
    }

    /** دسته‌های به‌کاررفته، برای پیشنهاد دادن هنگامِ دسته‌بندیِ طرح. */
    fun observeDesignCategories(): Flow<List<String>> =
        db.masterDataDao().observeDesignCategories()

    /** نقشهٔ «نامِ طرح → دسته» برای پوشه‌بندیِ انبار. */
    fun observeDesignCategoryMap(): Flow<Map<String, String>> =
        db.masterDataDao().observeDesignItems().map { list ->
            StockFolders.categoryMap(list.map { it.title to it.category })
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
     * پیش‌پرداختِ تسویه‌نشدهٔ یک طرفِ حساب — عددِ مثبت یعنی او به ما بدهکار
     * است.
     *
     * چرا ماندهٔ دفتر کل همان پیش‌پرداخت است؟ چون [paySalary] تعهد و
     * پرداختش را با هم ثبت می‌کند و ماندهٔ حقوق همان‌جا صفر می‌شود. پس هر
     * ماندهٔ مثبتی که روی کارمند می‌ماند، پولی است که از قبل گرفته.
     */
    suspend fun outstandingAdvance(type: String, name: String): Long =
        partyBalanceUpTo(type, name, System.currentTimeMillis()).coerceAtLeast(0L)

    /**
     * پرداختِ حقوقِ ماهانه. مثلِ هر رویدادِ مالیِ دیگر از همین قیف عبور
     * می‌کند: نقد → دفتر کل → ژورنالِ دوطرفه → رسید → لاگِ حسابرسی.
     *
     * در دفتر کل دو سطر ثبت می‌شود (تعهدِ حقوق و پرداختِ آن) تا صورت‌حسابِ
     * کارمند خوانا بماند و ماندهٔ حسابش پس از پرداخت صفر شود.
     *
     * [amount] حقوقِ **کامل** است و [deductAdvance] آن بخشش که کارمند از
     * قبل به‌عنوان پیش‌پرداخت گرفته. نقدِ خارج‌شده تفاوتِ این دو است.
     *
     * بی این کسر، پیش‌پرداخت هیچ‌وقت تهاتر نمی‌شد: کارمند تا ابد در دفتر
     * بدهکار می‌ماند، حسابِ «پیش‌پرداخت کارکنان» تا ابد بادکرده، و هزینهٔ
     * حقوق به اندازهٔ همان پیش‌پرداخت کمتر از واقع ثبت می‌شد. مقدارِ پیش‌فرضِ
     * صفر یعنی رفتارِ قبلی مو‌به‌مو حفظ می‌شود.
     *
     * @return false اگر مبلغ نامعتبر باشد یا موجودیِ صندوق برای نقدِ
     * پرداختی کفایت نکند.
     */
    suspend fun paySalary(
        employee: String,
        amount: Long,
        periodKey: String,
        periodLabel: String,
        source: String = "WALLET",
        note: String = "",
        deductAdvance: Long = 0
    ): Boolean = db.atomic {
        paySalaryTx(employee, amount, periodKey, periodLabel, source, note, deductAdvance)
    }

    private suspend fun paySalaryTx(
        employee: String,
        amount: Long,
        periodKey: String,
        periodLabel: String,
        source: String,
        note: String,
        deductAdvance: Long
    ): Boolean {
        val emp = employee.trim()
        if (emp.isEmpty() || amount <= 0) return false
        // کسر نه از حقوق بیشتر می‌شود و نه از پیش‌پرداختِ واقعیِ کارمند
        val deduct = deductAdvance.coerceIn(0L, minOf(amount, outstandingAdvance("EMPLOYEE", emp)))
        val cashOut = amount - deduct
        // کنترل روی نقدِ خارج‌شده است، نه حقوقِ کامل — وگرنه کارگاهی که
        // بخشی را از قبل پیش‌پرداخت کرده نمی‌تواند بقیه را بدهد.
        if (!hasFunds(source, cashOut)) return false

        val memo = "حقوق $periodLabel — $emp"
        db.salaryDao().insert(
            com.afghanjama.data.entities.SalaryPayment(
                employee = emp, amount = amount,
                periodKey = periodKey, periodLabel = periodLabel,
                source = source, note = note.trim()
            )
        )
        if (cashOut > 0) {
            spend(source, cashOut, note.ifBlank { memo }, category = "حقوق کارکنان")
        }

        // دفتر کل: تعهدِ حقوقِ کامل (بستانکار) و نقدِ پرداختی (بدهکار).
        // تفاوتشان همان پیش‌پرداختی است که تهاتر می‌شود، پس ماندهٔ کارمند
        // از پیش‌پرداخت هم پاک می‌شود و به صفر می‌رسد.
        postLedger("EMPLOYEE", emp, 0, amount, "SALARY", periodKey, memo)
        if (cashOut > 0) {
            postLedger("EMPLOYEE", emp, cashOut, 0, "SALARY_PAID", periodKey, note.ifBlank { memo })
        }

        createDocument("SALARY_RECEIPT", emp, amount, refId = periodKey, note = memo)
        audit(
            "پرداخت حقوق",
            "$emp — $periodLabel — $amount ؋" +
                if (deduct > 0) " (کسرِ پیش‌پرداخت $deduct ؋)" else ""
        )

        // ژورنال: هزینهٔ حقوقِ کامل، در برابرِ نقد و تهاترِ پیش‌پرداخت
        postJournal(
            memo, "SALARY", periodKey,
            buildList {
                add(jl(Accounts.EXPENSES, debit = amount))
                if (deduct > 0) add(jl(Accounts.STAFF_ADVANCE, credit = deduct))
                if (cashOut > 0) add(jl(Accounts.box(source), credit = cashOut))
            }
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

    /**
     * اصلاحِ دستیِ ساعتِ ورود و خروج.
     *
     * کارگاه واقعی است: خیاط ساعت هفت آمده ولی ساعت نُه دکمه را زده، یا
     * شب یادش رفته خروج بزند. بدونِ این، همان عددِ غلط تا ابد در کارکرد
     * و حقوق می‌ماند.
     *
     * **خروج نمی‌تواند پیش از ورود باشد** — بازهٔ منفی کارکرد را منفی
     * می‌کند و از آنجا به حقوق می‌رسد. `null` یعنی «هنوز داخل است».
     *
     * @return `false` اگر بازه وارونه باشد؛ آن‌وقت چیزی نوشته نمی‌شود.
     */
    suspend fun editAttendance(record: AttendanceRecord, checkIn: Long, checkOut: Long?): Boolean {
        if (checkOut != null && checkOut < checkIn) return false
        db.attendanceDao().update(record.copy(checkIn = checkIn, checkOut = checkOut))
        return true
    }

    /** ثبت خروج برای بازهٔ بازِ کارمند. */
    suspend fun checkOut(employee: String) {
        val open = db.attendanceDao().findOpen(employee.trim()) ?: return
        db.attendanceDao().update(open.copy(checkOut = System.currentTimeMillis()))
    }

    // =========================
    // صندوقِ درخواست‌های گوشی‌های کارگران (مدلِ «یک نویسنده»)
    // =========================

    fun observePendingRequests(): Flow<List<com.afghanjama.data.entities.SyncRequest>> =
        db.syncRequestDao().observePending()

    fun observeAllRequests(): Flow<List<com.afghanjama.data.entities.SyncRequest>> =
        db.syncRequestDao().observeAll()

    fun observePendingRequestCount(): Flow<Int> =
        db.syncRequestDao().observePendingCount()

    /**
     * تأییدِ یک درخواست — تنها جایی که کارِ رسیده از شبکه واقعاً ثبت
     * می‌شود، و از همان توابعِ همیشگی می‌گذرد. هیچ مسیرِ میان‌بری برای
     * دادهٔ شبکه ساخته نشده است.
     *
     * @return پیغامِ خطا، یا null اگر انجام شد.
     */
    suspend fun approveRequest(id: Long, note: String = ""): String? {
        val r = db.syncRequestDao().getById(id) ?: return "درخواست پیدا نشد."
        if (r.status != "PENDING") return "این درخواست قبلاً رسیدگی شده."

        when (r.type) {
            "SEWING_DONE" -> {
                val a = db.sewingAssignmentDao().getById(r.refId)
                    ?: return "تحویلِ مرتبط پیدا نشد؛ شاید لغو شده باشد."
                if (a.status == "DONE") return "این کار قبلاً تحویل شده."
                completeAssignment(a.id, deliveredQty = r.amount.takeIf { it > 0 })
            }
            // حضور و غیاب با نامِ خالی کار می‌کند، نه با برچسبِ «[T10] احمد».
            // برچسب برای کارمزد و تحویلِ دوخت کلیدِ درست است و دست نمی‌خورد؛
            // فقط همین دو شاخه نامِ خالی می‌خواهند.
            "ATTENDANCE_IN" -> checkIn(r.worker.bareWorkerName())
            "ATTENDANCE_OUT" -> checkOut(r.worker.bareWorkerName())
            "NOTE" -> Unit  // پیام فقط خوانده می‌شود
            else -> return "نوعِ درخواست شناخته نشد."
        }

        db.syncRequestDao().update(
            r.copy(
                status = "APPROVED",
                decidedAt = System.currentTimeMillis(),
                decidedNote = note.trim()
            )
        )
        audit("تأیید درخواست کارگر", "${r.worker} — ${r.summary}")
        return null
    }

    suspend fun rejectRequest(id: Long, note: String = "") {
        val r = db.syncRequestDao().getById(id) ?: return
        if (r.status != "PENDING") return
        db.syncRequestDao().update(
            r.copy(
                status = "REJECTED",
                decidedAt = System.currentTimeMillis(),
                decidedNote = note.trim()
            )
        )
        audit("رد درخواست کارگر", "${r.worker} — ${r.summary}")
    }

    // =========================
    // خواندنِ اسناد برای چاپ
    // =========================

    /** ردیف‌های یک فاکتور فروش (کدِ مشترک). */
    suspend fun saleLinesByCode(code: String): List<com.afghanjama.data.entities.FinishedSale> =
        db.finishedStockDao().salesByCode(code)

    /** اقلامِ یک فاکتور خرید بر اساسِ کدِ فاکتور. */
    suspend fun purchaseItemsByCode(code: String): List<PurchaseItem> =
        db.procurementDao().itemsByInvoiceCode(code)

    suspend fun purchaseInvoiceByCode(code: String): PurchaseInvoice? =
        db.procurementDao().invoiceByCode(code)

    /**
     * «بدهی قبلی» برای چاپ روی فاکتور: ماندهٔ طرفِ حساب بدونِ احتسابِ
     * خودِ این فاکتور. عددِ مثبت یعنی طرف به ما بدهکار است.
     */
    suspend fun partyBalanceBefore(
        type: String,
        name: String,
        excludeRef: String,
        atMs: Long
    ): Long {
        val n = name.trim()
        if (n.isBlank()) return 0L
        return db.ledgerDao().balanceBefore(type, n, excludeRef, atMs)
    }

    /** ماندهٔ طرفِ حساب تا یک لحظه، با احتسابِ همین فاکتور. */
    suspend fun partyBalanceUpTo(type: String, name: String, atMs: Long): Long {
        val n = name.trim()
        if (n.isBlank()) return 0L
        return db.ledgerDao().balanceUpTo(type, n, atMs)
    }

    /**
     * عکسِ کالای انبار. عکسِ قبلی — اگر بود — پاک می‌شود تا پوشهٔ اپ پر از
     * فایلِ بی‌صاحب نشود. نامِ خالی یعنی «عکس را بردار».
     */
    suspend fun setStockPhoto(
        item: FinishedStock,
        fileName: String,
        deleteFile: (String) -> Unit
    ) {
        val old = item.photoFile
        db.finishedStockDao().upsert(
            item.copy(photoFile = fileName.trim(), updatedAt = System.currentTimeMillis())
        )
        if (old.isNotBlank() && old != fileName.trim()) deleteFile(old)
        audit(
            if (fileName.isBlank()) "حذف عکس کالا" else "افزودن عکس کالا",
            "${item.name} ${item.size}".trim()
        )
    }

    /** نامِ عکس‌هایی که هنوز صاحب دارند — سفارش‌ها و کالاهای انبار. */
    suspend fun liveStockPhotoFileNames(): Set<String> =
        db.finishedStockDao().observeAll().first()
            .map { it.photoFile }.filter { it.isNotBlank() }.toSet()

    /**
     * کدِ اختصاصیِ طرح — همان که در «اطلاعات پایه» وارد می‌شود (مثل DIP-12).
     *
     * روی فاکتوری که دستِ مشتری می‌رود باید همین بیاید، نه شمارهٔ ردیفِ
     * دیتابیس: آن عدد بیرون از اپ هیچ معنایی ندارد، روی گوشیِ دیگر عددِ
     * دیگری می‌شود و با پاک کردنِ داده از نو شروع می‌شود. مشتری که برای
     * سفارشِ دوباره زنگ می‌زند باید کدی بگوید که کارگاه بشناسد.
     *
     * اگر طرح ثبت نشده یا کد ندارد، خالی می‌مانَد — کدِ ساختگی روی فاکتور
     * بدتر از نداشتنِ کد است.
     */
    suspend fun designCodeFor(designTitle: String): String =
        db.masterDataDao().designCodeByTitle(designTitle.trim()).orEmpty().trim()

    /** مشتری بر اساسِ نام — برای بلوکِ «خریدار» روی فاکتور. */
    suspend fun customerByName(name: String): Customer? {
        val n = name.trim()
        if (n.isBlank()) return null
        return db.masterDataDao().findCustomerByName(n)
    }

    // =========================
    // عکس‌های سفارش
    // =========================

    fun observeOrderPhotos(orderId: String): Flow<List<com.afghanjama.data.entities.OrderPhoto>> =
        db.orderPhotoDao().observeForOrder(orderId)

    fun observeAllOrderPhotos(): Flow<List<com.afghanjama.data.entities.OrderPhoto>> =
        db.orderPhotoDao().observeAll()

    /**
     * نامِ همهٔ عکس‌هایی که هنوز صاحب دارند — برای جاروی فایل‌های یتیم.
     * عکسِ کالای انبار هم در همین پوشه است، پس باید اینجا شمرده شود وگرنه
     * جارو عکسِ سالمِ کالاها را پاک می‌کند.
     */
    suspend fun livePhotoFileNames(): Set<String> =
        db.orderPhotoDao().observeAll().first().map { it.fileName }.toSet() +
            liveStockPhotoFileNames()

    suspend fun addOrderPhoto(order: Order, fileName: String, note: String = "") {
        db.orderPhotoDao().insert(
            com.afghanjama.data.entities.OrderPhoto(
                orderId = order.id.toString(),
                orderCode = order.orderCode,
                fileName = fileName,
                note = note.trim()
            )
        )
        audit("افزودن عکس به سفارش", order.orderCode)
    }

    /**
     * حذفِ عکس. فایل را هم پاک می‌کند، وگرنه پوشهٔ اپ پر از عکسِ بی‌صاحب
     * می‌شود — روی گوشیِ ارزانِ کارگاه این خیلی زود دیده می‌شود.
     */
    suspend fun deleteOrderPhoto(
        photo: com.afghanjama.data.entities.OrderPhoto,
        deleteFile: (String) -> Unit
    ) {
        db.orderPhotoDao().delete(photo)
        deleteFile(photo.fileName)
        audit("حذف عکس سفارش", photo.orderCode)
    }

    // =========================
    // وقت‌های نان و چای
    // =========================

    fun observeBreakTimes(): Flow<List<com.afghanjama.data.entities.BreakTime>> =
        db.breakTimeDao().observeAll()

    suspend fun upsertBreakTime(row: com.afghanjama.data.entities.BreakTime): Long =
        db.breakTimeDao().upsert(row)

    suspend fun deleteBreakTime(row: com.afghanjama.data.entities.BreakTime) =
        db.breakTimeDao().delete(row)

    suspend fun breakTimeCount(): Int = db.breakTimeDao().count()

    /** چند نفر همین حالا داخلِ کارگاه‌اند — برای یادآور و برای خودِ صفحه. */
    suspend fun insideCount(): Int = db.breakTimeDao().insideCount()

    // =========================
    // Customer measurements (اندازه‌های مشتری)
    // =========================

    fun observeMeasurements(customerId: Long): Flow<List<CustomerMeasurement>> =
        db.customerMeasurementDao().observeForCustomer(customerId)

    suspend fun upsertMeasurement(row: CustomerMeasurement) =
        db.customerMeasurementDao().upsert(row.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteMeasurement(id: Long) =
        db.customerMeasurementDao().deleteById(id)

    /**
     * اندازه‌ها به تفکیکِ نامِ مشتری. کلید نامِ trim‌شده است تا با
     * `Order.customerName` که کاربر تایپ کرده جور در بیاید.
     *
     * این همان چیزی است که برش و دوخت لازم دارند: تا حالا اندازه ثبت
     * می‌شد ولی فقط در صفحهٔ خودِ مشتری دیده می‌شد، یعنی به دستِ کسی که
     * قیچی و سوزن دستش است هرگز نمی‌رسید.
     */
    fun observeMeasurementsByCustomer(): Flow<Map<String, List<NamedMeasurement>>> =
        db.customerMeasurementDao().observeAllNamed()
            .map { rows -> rows.groupBy { it.customerName.trim() } }

    // =========================
    // Work Costs (CatalogDao)
    // =========================

    fun observeWorkCosts(): Flow<List<WorkCost>> =
        db.catalogDao().observeWorkCosts()

    suspend fun insertWorkCost(item: WorkCost) =
        db.catalogDao().insertWorkCosts(listOf(item))

    // ✅ NEW: update work cost
    // =========================
    // ویرایش و حذفِ نام‌های اطلاعات پایه
    //
    // **اسنادِ گذشته دست نمی‌خورند، و این عمدی است.** نامِ خیاط و طرح
    // هنگامِ ثبتِ سفارش به‌صورتِ متن در خودِ سفارش کپی می‌شود. پس:
    //
    // - حذف چیزی را یتیم نمی‌کند؛ سفارشِ قدیمی نامش را با خودش دارد.
    // - تغییرِ نام روی سفارش‌های قبلی اثر نمی‌گذارد؛ فاکتوری که چاپ شده
    //   و دستِ مشتری است نباید با ویرایشِ یک فهرست عوض شود.
    //
    // یعنی این دو کار فقط می‌گویند «از این به بعد این نام». برای اصلاحِ
    // یک سفارشِ مشخص، جای درستش خودِ آن سفارش است نه اینجا.
    // =========================

    suspend fun renameTailor(id: Long, name: String) {
        val v = name.trim()
        if (v.isNotEmpty()) db.masterDataDao().renameTailor(id, v)
    }

    suspend fun renameInspector(id: Long, name: String) {
        val v = name.trim()
        if (v.isNotEmpty()) db.masterDataDao().renameInspector(id, v)
    }

    suspend fun renameFabricType(id: Long, title: String) {
        val v = title.trim()
        if (v.isNotEmpty()) db.masterDataDao().renameFabricType(id, v)
    }

    suspend fun renameFabricColor(id: Long, title: String) {
        val v = title.trim()
        if (v.isNotEmpty()) db.masterDataDao().renameFabricColor(id, v)
    }

    suspend fun renameSize(id: Long, title: String) {
        val v = title.trim()
        if (v.isNotEmpty()) db.masterDataDao().renameSize(id, v)
    }

    suspend fun renameDesign(id: Long, title: String) {
        val v = title.trim()
        if (v.isNotEmpty()) db.masterDataDao().renameDesign(id, v)
    }

    suspend fun deleteTailor(id: Long) = db.masterDataDao().deleteTailor(id)
    suspend fun deleteInspector(id: Long) = db.masterDataDao().deleteInspector(id)
    suspend fun deleteFabricType(id: Long) = db.masterDataDao().deleteFabricType(id)
    suspend fun deleteFabricColor(id: Long) = db.masterDataDao().deleteFabricColor(id)
    suspend fun deleteSize(id: Long) = db.masterDataDao().deleteSize(id)
    suspend fun deleteDesign(id: Long) = db.masterDataDao().deleteDesign(id)

    suspend fun updateWorkCost(item: WorkCost) =
        db.catalogDao().updateWorkCost(item)

    // ✅ NEW: delete work cost
    suspend fun deleteWorkCostById(id: Long) =
        db.catalogDao().deleteWorkCostById(id)
}

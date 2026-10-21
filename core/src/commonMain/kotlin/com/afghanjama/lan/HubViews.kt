package com.afghanjama.lan

import com.afghanjama.data.Db
import com.afghanjama.data.OrderPipeline
import com.afghanjama.data.entities.LanDevice
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.entities.clockSuspect
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.bareWorkerName
import com.afghanjama.ui.vm.BoardRow
import com.afghanjama.ui.vm.debtPlanFlow
import com.afghanjama.util.withTime
import kotlinx.coroutines.flow.first

/**
 * تصویرِ هر نقش از دفتر — **فقط آنچه همان نقش حقِ دیدنش را دارد** (طرح، B.2).
 *
 * - **کارگر:** کارِ زیرِ دستِ خودش با کارمزدِ فی‌عددِ خودش (سؤالِ ۷: بله، فقط
 *   مالِ خودش)، درخواست‌های خودش و وضعشان، و تابلو.
 * - **صاحب‌کار:** صندوقِ درخواست‌ها، سفارش‌های امروز، دیرکرده و باز (کد،
 *   مشتری، مرحله، موعد — بی پول)، کسانی که داخل‌اند، فهرستِ امروزِ پیگیریِ
 *   طلب، و تابلو.
 * - **تابلوی دیوار:** فقط تابلو — نه پول، نه نامِ مشتری.
 *
 * چیزی از نقشی به نقشِ دیگر نشت نمی‌کند، چون هر نقش تابعِ خودش را دارد و
 * هیچ‌کدام از دیگری نمی‌خوانَد؛ گوشی هم تصویرِ نقشِ دیگر را نمی‌پذیرد
 * ([HubSnapshot.parse]).
 */
object HubViews {

    private const val DAY = 86_400_000L

    /** همان «در حالِ دوخت»ِ قراردادِ ۱ (`/board`)، یک جا برای هر دو. */
    suspend fun board(db: Db, now: Long): List<BoardRow> {
        val assignments = db.sewingAssignmentDao().observeInProgress().first()
        val byCode = db.orderDao().observeAll().first().associateBy { it.orderCode }
        return assignments.map { a ->
            val o = byCode[a.orderCode]
            val due = o?.dueDate ?: 0L
            BoardRow(
                tailor = a.tailorLabel.trim(),
                orderCode = a.orderCode,
                design = o?.designTitle.orEmpty(),
                qty = a.qty,
                days = ((now - a.createdAt) / DAY).toInt().coerceAtLeast(0),
                dueIn = if (due > 0) ((due - now) / DAY).toInt() else null,
            )
        }
    }

    /** تابلو در تصویرِ گوشی — تا همان سقفی که گوشی می‌خوانَد. */
    private suspend fun sent(db: Db, now: Long): List<BoardRow> = board(db, now).take(HubProtocol.SNAPSHOT_BOARD_LINES)

    suspend fun snapshot(db: Db, repo: Repo, device: LanDevice, now: Long): HubSnapshot? =
        when (HubRole.of(device.role)) {
            HubRole.WORKER -> HubSnapshot(HubRole.WORKER, worker = worker(db, repo, device.personLabel), board = sent(db, now))
            HubRole.OWNER -> HubSnapshot(HubRole.OWNER, owner = owner(db, repo, now), board = sent(db, now))
            HubRole.WALL -> HubSnapshot(HubRole.WALL, board = sent(db, now))
            null -> null
        }

    fun requestLine(r: SyncRequest): RequestLine = RequestLine(
        id = r.id,
        clientKey = r.clientKey.orEmpty(),
        type = r.type,
        summary = r.summary,
        worker = r.worker,
        device = r.deviceName,
        status = r.status,
        note = if (r.status == "PENDING") r.note else r.decidedNote.ifBlank { r.note },
        receivedAt = r.receivedAt ?: r.createdAt,
        pressedAt = r.clientAt,
        clockSuspect = r.clockSuspect(),
    )

    private suspend fun worker(db: Db, repo: Repo, person: String): WorkerView {
        val me = person.trim()
        val mine = db.sewingAssignmentDao().observeAll().first().filter { it.tailorLabel.trim() == me && me.isNotEmpty() }
        val design = repo.observeDesignCodeByOrder().first()
        return WorkerView(
            person = me,
            inHand = mine.filter { it.status != "DONE" }.map {
                WorkLine(it.id, it.orderCode, design[it.orderCode].orEmpty(), it.qty, it.unitWage, it.createdAt)
            },
            doneTotal = mine.count { it.status == "DONE" },
            requests = if (me.isEmpty()) emptyList() else db.syncRequestDao().recentFor(me, 30).map { requestLine(it) },
            insideSince = me.bareWorkerName().takeIf { it.isNotEmpty() }?.let { db.attendanceDao().findOpen(it)?.checkIn },
        )
    }

    /** سفارشی که هنوز در کارگاه است — همان قاعدهٔ `DeliveryForecast` (نقدِ ۱۹). */
    private val OPEN = setOf(
        OrderStatus.IN_STOCK.name, OrderStatus.CUTTING.name, OrderStatus.CUT_DONE.name,
        OrderStatus.SEWING.name, OrderStatus.REVIEW.name,
    )

    private fun stage(o: Order): String =
        if (o.status == OrderStatus.STORED.name) "آمادهٔ تحویل"
        else OrderPipeline.STAGES.getOrNull(OrderPipeline.indexOf(o.status))?.label ?: o.status

    private fun line(o: Order) = OrderLine(o.orderCode, o.customerName, stage(o), o.dueDate, o.createdAt)

    private suspend fun owner(db: Db, repo: Repo, now: Long): OwnerView {
        val dayStart = withTime(now, 0, 0)
        val dayEnd = dayStart + DAY
        val orders = db.orderDao().observeAll().first()
        val open = orders.filter { it.status in OPEN }
        // «عقب‌افتاده» فقط با موعدِ واقعی: موعدِ ۰ یعنی «بی موعد»، نه دیرکرد (نقدِ ۱۹ الف).
        val dueToday = open.filter { it.dueDate in dayStart until dayEnd }
        val overdue = open.filter { it.dueDate in 1 until dayStart }.sortedBy { it.dueDate }
        val createdToday = orders.filter { it.createdAt in dayStart until dayEnd }
        val inside = db.attendanceDao().observeRecent().first().filter { it.checkOut == null }.map { it.employee }.distinct()
        val plan = debtPlanFlow(repo).first()
        return OwnerView(
            pending = db.syncRequestDao().observePending().first().take(HubProtocol.SNAPSHOT_PENDING_LINES).map { requestLine(it) },
            dueToday = dueToday.take(HubProtocol.SNAPSHOT_ORDER_LINES).map { line(it) },
            overdue = overdue.take(HubProtocol.SNAPSHOT_ORDER_LINES).map { line(it) },
            createdToday = createdToday.take(HubProtocol.SNAPSHOT_ORDER_LINES).map { line(it) },
            openOrders = open.sortedWith(compareBy({ if (it.dueDate > 0) it.dueDate else Long.MAX_VALUE }, { it.createdAt }))
                .take(100).map { line(it) },
            openCount = open.size,
            readyCount = orders.count { it.status == OrderStatus.STORED.name },
            insideNow = inside,
            debtToday = plan.today.take(HubProtocol.SNAPSHOT_DEBT_LINES).map {
                DebtLine(it.debtor.name, it.debtor.phone, it.debtor.amount, it.reason.label, it.contactedToday)
            },
        )
    }

    /** چکیدهٔ یک تصویر — گوشی با همین می‌فهمد چیزی عوض شده یا نه (نقدِ ۱۵). */
    fun cursorOf(snapshot: HubSnapshot): String =
        LanCrypto.b64(LanPrims.sha256(LanJson.write(snapshot.toJson()).encodeToByteArray()).copyOf(16))
}

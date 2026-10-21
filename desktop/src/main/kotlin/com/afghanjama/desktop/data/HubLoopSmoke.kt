package com.afghanjama.desktop.data

import com.afghanjama.data.Db
import com.afghanjama.data.entities.LegacyMode
import com.afghanjama.data.entities.RequestTime
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.Ack
import com.afghanjama.lan.AckStatus
import com.afghanjama.lan.DeviceCred
import com.afghanjama.lan.HubClient
import com.afghanjama.lan.HubEnvelope
import com.afghanjama.lan.HubOff
import com.afghanjama.lan.HubPairing
import com.afghanjama.lan.HubProtocol
import com.afghanjama.lan.HubRole
import com.afghanjama.lan.HubService
import com.afghanjama.lan.LanClient
import com.afghanjama.lan.LanGuard
import com.afghanjama.lan.LanHost
import com.afghanjama.lan.LanHub
import com.afghanjama.lan.LanJson
import com.afghanjama.lan.LanNetwork
import com.afghanjama.lan.LanPrims
import com.afghanjama.lan.LanResult
import com.afghanjama.lan.LanServer
import com.afghanjama.lan.Outbox
import com.afghanjama.lan.OutboxItem
import com.afghanjama.lan.OutboxState
import com.afghanjama.lan.OutboxWire
import com.afghanjama.lan.PairState
import com.afghanjama.lan.PairVia
import com.afghanjama.lan.PairingV2
import com.afghanjama.lan.PhoneInfo
import com.afghanjama.lan.SyncAsk
import com.afghanjama.lan.SyncResult
import com.afghanjama.lan.hubTransport
import com.afghanjama.licence.LicenceFacts
import com.afghanjama.licence.LicenceGate
import com.afghanjama.licence.LicenceText
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LanPrefs
import com.afghanjama.util.randomUuid
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

/*
 * «کارگاهِ وصل» (قراردادِ ۲) روی همین ماشین: یک کامپیوترِ کارگاه (`LanHub`ِ
 * واقعی، با همان `LanServer` و `HubService` که برنامه دارد) و چند «گوشی»
 * (`HubClient`ِ واقعی) که روی سوکتِ واقعی به `127.0.0.1` وصل می‌شوند —
 * دفتر یک فایلِ موقت است.
 *
 * هر بند یکی از قول‌های فازِ B است: جفت شدن، همگام‌سازی، کارِ آفلاین و
 * برخورد، رمزِ نادرست، تکرار، ساعتِ نادرست، سقفِ حدس، دسترسیِ
 * برداشته‌شده، گوشیِ نسخهٔ قدیم، گوشیِ تازه با کامپیوترِ قدیم، و دفترِ
 * بازیابی‌شده روی کامپیوترِ دیگر. همین سرور روی گوشیِ اصلیِ اندرویدی هم
 * اجرا می‌شود (کدش در `jvmAndroidMain` است)؛ اینجا با دفترِ کامپیوتر.
 */

private var hubFailed = 0

private fun hubCheck(name: String, body: suspend () -> Unit) {
    try {
        runBlocking { body() }
        println("OK    $name")
    } catch (t: Throwable) {
        hubFailed++
        println("FAIL  $name")
        println("        ${t::class.java.name}: ${t.message}")
    }
}

private fun hubNeed(cond: Boolean, msg: String) {
    if (!cond) throw IllegalStateException(msg)
}

private fun <T> LanResult<T>.ok(what: String): T = when (this) {
    is LanResult.Ok -> value
    is LanResult.Err -> throw IllegalStateException("$what: $message")
}

private class HubHost(private val db: Db) : LanHost {
    override val deviceName: String = "کامپیوترِ آزمون"
    override fun localIp(): String = "192.168.77.7"
    override fun localIps(): List<String> = listOf("192.168.77.7")
    override fun networks(): Map<String, LanNetwork> = mapOf("192.168.77.7" to WORKSHOP)
    override fun ledger(): Db = db

    companion object {
        val WORKSHOP = LanNetwork("192.168.77.0/24", "192.168.77.1", "a4:2b:b0:02:34:56")
    }
}

private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

private fun listeningOn(port: Int): Boolean = runCatching {
    Socket().use { it.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500) }
}.isSuccess

/** یک درخواستِ HTTPِ خام؛ خطِ وضعیت و بدنه. */
private fun raw(port: Int, method: String, path: String, headers: Map<String, String>, body: String): Pair<Int, String> =
    Socket(InetAddress.getLoopbackAddress(), port).use { s ->
        s.soTimeout = 10_000
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("$method $path HTTP/1.1\r\n")
            headers.forEach { (k, v) -> append("$k: $v\r\n") }
            append("Content-Length: ${bytes.size}\r\n\r\n")
        }
        s.getOutputStream().apply { write(head.toByteArray(Charsets.ISO_8859_1)); write(bytes); flush() }
        val text = s.getInputStream().readBytes().toString(Charsets.UTF_8)
        val status = text.lineSequence().first().split(' ')[1].toInt()
        status to text.substringAfter("\r\n\r\n")
    }

/** بندهای قراردادِ ۲؛ شمارِ شکست‌ها را برمی‌گرداند. */
fun runHubLoop(dir: File): Int {
    println("-".repeat(52))
    println("protocol 2 (desktop hub + phones over 127.0.0.1)")

    val db = openDatabase(File(dir, "hub.db"))
    val repo = Repo(db)
    val settings = FileSettings(File(dir, "hub2-settings"))
    LanPrefs.setMode(settings, DeviceMode.MAIN)
    LanPrefs.setCode(settings, "482913")
    LanPrefs.setNetwork(settings, HubHost.WORKSHOP.encode())
    val host = HubHost(db)
    val port = freePort()
    val clock = AtomicLong(System.currentTimeMillis())
    val twinRef = java.util.concurrent.atomic.AtomicReference<String?>(null)
    val hub = LanHub(
        InetAddress.getLoopbackAddress(), port,
        guardClock = { clock.get() },
        licenceLoaded = {}, offIntervalMs = 100, onIntervalMs = 100,
        machine = { "machine-A" },
        pairLongPollMs = 1_500,
        findTwin = { _, _, _ -> twinRef.get() },
    )
    val transport = hubTransport(allowLoopback = true)
    val phone = HubClient(transport)
    val extra = mutableListOf<LanHub>()

    fun info(name: String, role: HubRole) = PhoneInfo(name, "android", "1.11.0", role)

    suspend fun offset(): Long =
        (phone.ping("127.0.0.1", port) as LanResult.Ok).value.serverTime!! - System.currentTimeMillis()

    /** «افزودنِ گوشی» تا «اجازه» — کارفرما وقتی گوشی منتظر است تصمیم می‌گیرد. */
    suspend fun pair(name: String, role: HubRole, person: String, via: PairVia = PairVia.QR): DeviceCred = coroutineScope {
        val view = hub.openPairing() ?: error("the pairing window did not open: ${hub.state.value}")
        val link = PairingV2.parse(view.qrText) ?: error("the hub's QR does not parse: ${view.qrText.take(30)}")
        val secret = if (via == PairVia.QR) link.secret else view.code.encodeToByteArray()
        val pending = phone.requestPair("127.0.0.1", port, via, secret, info(name, role), link.takeIf { via == PairVia.QR })
            .ok("pair request")
        val poll = async { phone.pollPair(pending) }
        delay(150)
        hub.approvePairing(role, person)?.let { error("approve refused: $it") }
        val grant = poll.await().ok("pair status")
        hubNeed(grant.state == PairState.APPROVED, "grant ${grant.state}")
        DeviceCred(grant.deviceId!!, grant.deviceKey!!, grant.hubId, grant.hubName, grant.role!!, grant.person)
    }

    suspend fun sync(cred: DeviceCred, items: List<OutboxWire> = emptyList(), cursor: String = "", off: Long? = null): SyncResult {
        val o = off ?: offset()
        return phone.sync(cred, "127.0.0.1", port, SyncAsk(System.currentTimeMillis(), cursor, items, "1.11.0", "android"), o)
    }

    fun ok(r: SyncResult): SyncResult.Ok = r as? SyncResult.Ok ?: error("sync failed: $r")

    fun item(type: String, payload: Map<String, Any?> = emptyMap(), at: Long = System.currentTimeMillis()) =
        OutboxWire(randomUuid().toString(), at, type, payload)

    val ahmad = "[T1] احمد"
    lateinit var worker: DeviceCred
    lateinit var owner: DeviceCred
    var mine = 0L
    var theirs = 0L

    try {
        hubCheck("the hub starts with protocol 2 on the workshop network") {
            hub.autoStart(settings, host)
            var ready = false
            repeat(100) { if (!ready) { ready = hub.state.value.serving && hub.hubService()?.ready == true; if (!ready) delay(30) } }
            hubNeed(ready, "not ready: ${hub.state.value}")
        }

        hubCheck("ping tells protocol 2 and the ledger's id, and nothing about the workshop") {
            val (status, body) = raw(port, "GET", "/ping", emptyMap(), "")
            hubNeed(status == 200, "ping $status")
            val o = LanJson.parseObject(body)!!
            hubNeed(o["protocolMax"] == 2L, "protocolMax ${o["protocolMax"]}")
            hubNeed(o["hubId"] == repo.hubRow()?.hubId, "hubId ${o["hubId"]} vs ${repo.hubRow()?.hubId}")
            hubNeed(o["pairingOpen"] == false, "pairing open before anyone asked")
            hubNeed("hubName" !in o && "rows" !in o, "ping carries workshop data: ${o.keys}")
        }

        hubCheck("outside the pairing window every pairing route is 404 and says nothing") {
            hubNeed(phone.hello("127.0.0.1", port) == LanResult.Err(HubClient.PAIR_CLOSED), "hello answered")
            for (p in listOf(HubProtocol.PATH_PAIR, HubProtocol.PATH_PAIR_STATUS)) {
                val (s, b) = raw(port, "POST", p, emptyMap(), "{\"requestId\":\"x\",\"pairingId\":\"y\"}")
                hubNeed(s == 404, "$p answered $s: $b")
            }
        }

        hubCheck("a device that never paired gets nothing from sync") {
            val stranger = DeviceCred(randomUuid().toString(), LanPrims.random(32), repo.hubRow()!!.hubId, "", HubRole.WORKER, ahmad)
            hubNeed(sync(stranger) is SyncResult.Unpaired, "a stranger synced")
            val (s, b) = raw(port, "POST", HubProtocol.PATH_SYNC, emptyMap(), "{}")
            val o = LanJson.parseObject(b)!!
            hubNeed(s == 401 && o.keys == setOf("ok", "code", "error"), "no-header sync got $s ${o.keys}")
        }

        hubCheck("a worker pairs by QR; the owner picks the person; the window closes behind it") {
            mine = db.sewingAssignmentDao().insert(
                SewingAssignment(orderId = randomUuid().toString(), orderCode = "AJ-1", tailorLabel = ahmad, qty = 5, unitWage = 40)
            )
            theirs = db.sewingAssignmentDao().insert(
                SewingAssignment(orderId = randomUuid().toString(), orderCode = "AJ-2", tailorLabel = "[T2] محمود", qty = 4, unitWage = 55)
            )
            worker = pair("گوشیِ احمد", HubRole.WORKER, ahmad)
            val row = repo.lanDevice(worker.deviceId)!!
            hubNeed(row.personLabel == ahmad && row.role == "worker" && row.active, "row $row")
            hubNeed(worker.person == ahmad, "phone told ${worker.person}")
            hubNeed(phone.hello("127.0.0.1", port) is LanResult.Err, "the window stayed open for a second phone")
            hubNeed(!row.toString().contains(row.keyB64), "a device row printed its key")
        }

        hubCheck("ten wrong pairing codes shut only the code and block this address; the window and its QR stay open") {
            coroutineScope {
                val view = hub.openPairing()!!
                val hello = phone.hello("127.0.0.1", port).ok("hello")
                repeat(HubProtocol.PAIR_MAX_FAILURES) { i ->
                    val draft = HubEnvelope.pairRequest(hello, PairVia.CODE, "000000000000".encodeToByteArray(), info("x", HubRole.WORKER))
                    val (s, b) = raw(port, "POST", HubProtocol.PATH_PAIR, emptyMap(), draft.body)
                    hubNeed(s == 401 || s == 429, "wrong code ${i + 1}: $s $b")
                }
                val v = hub.pairing.value
                hubNeed(v.open && !v.allowCode && v.codeNote == HubPairing.TOO_MANY, "window $v / code ${v.allowCode}")
                hubNeed(view.code.isNotEmpty(), "no code")
                val blocked = raw(port, "POST", HubProtocol.PATH_HELLO, emptyMap(), "{}")
                hubNeed(blocked.first == 429, "this address was not blocked: ${blocked.first}")
                clock.addAndGet(LanGuard.BLOCK_MS + 1)
                // همان پنجره هنوز باز است (پنج از ده دقیقه) و QRِ آن جفت می‌کند.
                hubNeed(raw(port, "POST", HubProtocol.PATH_HELLO, emptyMap(), "{}").first == 200, "the window did not survive the guesses")
                val link = PairingV2.parse(view.qrText)!!
                val pending = phone.requestPair("127.0.0.1", port, PairVia.QR, link.secret, info("تابلوی انبار", HubRole.WALL), link)
                    .ok("QR after the code was shut")
                val poll = async { phone.pollPair(pending) }
                delay(150)
                hub.approvePairing(HubRole.WALL, "")?.let { error("approve refused: $it") }
                hubNeed(poll.await().ok("poll").state == PairState.APPROVED, "the QR did not pair")
            }
        }

        hubCheck("the manual code pairs a wall display; the owner role is never granted through the code") {
            val view = hub.openPairing()!!
            val hello = phone.hello("127.0.0.1", port).ok("hello")
            val ownerByCode = HubEnvelope.pairRequest(hello, PairVia.CODE, view.code.encodeToByteArray(), info("x", HubRole.OWNER))
            val (s, _) = raw(port, "POST", HubProtocol.PATH_PAIR, emptyMap(), ownerByCode.body)
            hubNeed(s == 403, "an owner asked by code got $s")
            val wall = pair("تابلوی سالن", HubRole.WALL, "", via = PairVia.CODE)
            hubNeed(wall.role == HubRole.WALL, "role ${wall.role}")
            val snap = ok(sync(wall)).reply.snapshot!!
            hubNeed(snap.worker == null && snap.owner == null && snap.board.size == 2, "wall sees $snap")
            val r = ok(sync(wall, listOf(item("NOTE", mapOf("text" to "سلام")))))
            hubNeed(r.reply.acks.single().status == AckStatus.REJECTED, "a wall display sent a note")
        }

        hubCheck("a second phone waiting at the same time is told to wait; a rejected phone hears it") {
            hub.openPairing()!!
            val link = PairingV2.parse(hub.pairing.value.qrText)!!
            val p1 = phone.requestPair("127.0.0.1", port, PairVia.QR, link.secret, info("یک", HubRole.WORKER), link).ok("p1")
            val p2 = phone.requestPair("127.0.0.1", port, PairVia.QR, link.secret, info("دو", HubRole.WORKER), link)
            hubNeed(p2 == LanResult.Err(HubService.BUSY), "second phone: $p2")
            hubNeed(hub.rejectPairing(), "reject failed")
            val g = phone.pollPair(p1).ok("p1 poll")
            hubNeed(g.state == PairState.REJECTED && g.deviceKey == null, "rejected phone got $g")
            hub.closePairing()
        }

        hubCheck("sync carries the outbox: attendance, the worker's own job and a Dari note are queued once, under the paired person") {
            val pressed = System.currentTimeMillis() - 2 * 3_600_000L
            val items = listOf(
                item("ATTENDANCE_IN", at = pressed),
                item("SEWING_DONE", mapOf("assignmentId" to mine, "qty" to 3L, "worker" to "[T2] محمود")),
                item("NOTE", mapOf("text" to "آستین‌های سفارشِ ۱۲ را دوباره دوختم.")),
            )
            val r = ok(sync(worker, items))
            hubNeed(r.reply.acks.map { it.status } == listOf(AckStatus.QUEUED, AckStatus.QUEUED, AckStatus.QUEUED), "acks ${r.reply.acks}")
            val rows = db.syncRequestDao().observeAll().first().filter { it.protocol == 2 }
            hubNeed(rows.size == 3, "rows ${rows.size}")
            hubNeed(rows.all { it.worker == ahmad && it.deviceId == worker.deviceId && it.clientKey != null }, "identity from the request: $rows")
            val att = rows.first { it.type == "ATTENDANCE_IN" }
            // ساعتِ گوشی با ساعتِ کامپیوتر فرق دارد (بندِ پیش ساعتِ کامپیوتر را پنج
            // دقیقه جلو برد)؛ لحظهٔ زدن به ساعتِ **کامپیوتر** ثبت می‌شود.
            val atHub = pressed + offset()
            hubNeed(kotlin.math.abs(att.clientAt!! - atHub) < 5_000, "press time ${att.clientAt} vs $atHub (hub clock)")
            hubNeed(rows.first { it.type == "SEWING_DONE" }.summary == "AJ-1 — ۳ عدد از ۵ عدد", "summary ${rows.first { it.type == "SEWING_DONE" }.summary}")
            hubNeed(rows.first { it.type == "NOTE" }.note == "آستین‌های سفارشِ ۱۲ را دوباره دوختم.", "the Dari note did not round-trip")
            hubNeed(r.reply.licenceState == null || r.reply.licenceWritable, "licence ${r.reply.licenceState}")

            // همان صف دوباره: هیچ ردیفِ تازه‌ای.
            val again = ok(sync(worker, items))
            hubNeed(again.reply.acks.all { it.status == AckStatus.DUPLICATE }, "resend ${again.reply.acks}")
            hubNeed(db.syncRequestDao().observeAll().first().count { it.protocol == 2 } == 3, "a resend added rows")
        }

        hubCheck("a worker cannot report another tailor's job, and sees only their own work and wage") {
            val before = db.syncRequestDao().observeAll().first().size
            val r = ok(sync(worker, listOf(item("SEWING_DONE", mapOf("assignmentId" to theirs, "qty" to 4L)))))
            val a = r.reply.acks.single()
            hubNeed(a.status == AckStatus.REJECTED && a.reason.contains("به نامِ شما نیست"), "ack $a")
            hubNeed(db.syncRequestDao().observeAll().first().size == before, "a refused job left a row")
            val w = r.reply.snapshot!!.worker!!
            hubNeed(w.inHand.map { it.orderCode } == listOf("AJ-1") && w.inHand.single().unitWage == 40L, "in hand ${w.inHand}")
            hubNeed(r.reply.snapshot!!.owner == null, "a worker received the owner's view")
            hubNeed(w.requests.all { it.worker == ahmad }, "someone else's requests leaked")
            val cursor = r.reply.cursor
            hubNeed(ok(sync(worker, cursor = cursor)).reply.snapshot == null, "an unchanged cursor brought the snapshot again")
        }

        hubCheck("offline, then a conflict: a job finished on the computer meanwhile is refused with its reason, visibly") {
            val job = db.sewingAssignmentDao().insert(
                SewingAssignment(orderId = randomUuid().toString(), orderCode = "AJ-3", tailorLabel = ahmad, qty = 2, unitWage = 30)
            )
            var queue = Outbox.add(
                emptyList(),
                OutboxItem(randomUuid().toString(), "SEWING_DONE", mapOf("assignmentId" to job, "qty" to 2L), "AJ-3", System.currentTimeMillis(), worker.deviceId, ahmad),
            ).first
            // گوشی بیرون از کارگاه: کامپیوتر در دسترس نیست.
            val away = phone.sync(worker, "127.0.0.1", freePort(), SyncAsk(System.currentTimeMillis(), "", queue.map { it.toWire() }, "1.11.0", "android"), 0)
            hubNeed(away is SyncResult.Unreachable, "an unreachable hub answered: $away")
            queue = Outbox.failed(queue, queue.map { it.key }, System.currentTimeMillis())
            hubNeed(Outbox.pendingCount(queue) == 1 && Outbox.label(queue.single()) == "در صفِ ارسال", "queue $queue")
            // در همین فاصله کارفرما همان کار را روی کامپیوتر تمام می‌کند.
            repo.completeAssignment(job)
            val back = ok(sync(worker, Outbox.retryNow(queue, System.currentTimeMillis()).map { it.toWire() }))
            queue = Outbox.acked(queue, back.reply.acks, System.currentTimeMillis())
            hubNeed(queue.single().state == OutboxState.REJECTED, "state ${queue.single().state}")
            hubNeed(Outbox.label(queue.single()) == "رد شد: این کار قبلاً تحویل شده.", "label ${Outbox.label(queue.single())}")
        }

        hubCheck("attendance pressed offline two hours ago is approved at the press, not at approval") {
            val req = db.syncRequestDao().observeAll().first().first { it.protocol == 2 && it.type == "ATTENDANCE_IN" }
            hubNeed(repo.approveRequest(req.id) == null, "approve failed")
            val open = db.attendanceDao().findOpen("احمد")!!
            hubNeed(open.checkIn == req.clientAt, "checkIn ${open.checkIn}, pressed ${req.clientAt}")
        }

        hubCheck("a press from three days offline is flagged and waits for the owner's choice") {
            ok(sync(worker, listOf(item("ATTENDANCE_OUT", at = System.currentTimeMillis() - 3 * 86_400_000L))))
            val req = db.syncRequestDao().observeAll().first().first { it.protocol == 2 && it.type == "ATTENDANCE_OUT" }
            val why = repo.approveRequest(req.id)
            hubNeed(why != null && why.contains("ساعتِ گوشی معتبر نیست"), "AUTO approved a suspect time: $why")
            hubNeed(db.syncRequestDao().getById(req.id)!!.status == "PENDING", "the suspect request left the inbox")
            hubNeed(repo.approveRequest(req.id, time = RequestTime.ARRIVAL) == null, "arrival time refused")
            val shift = db.attendanceDao().findLast("احمد")!!
            hubNeed(shift.checkOut != null && kotlin.math.abs(shift.checkOut!! - req.receivedAt!!) < 1_000, "shift $shift")
        }

        hubCheck("the owner's phone approves a worker's request once; the audit names the phone, not the desk") {
            owner = pair("گوشیِ صاحب‌کار", HubRole.OWNER, "")
            ok(sync(worker, listOf(item("NOTE", mapOf("text" to "فردا دیرتر می‌آیم.")))))
            val view = ok(sync(owner)).reply.snapshot!!.owner!!
            val pending = view.pending.first { it.summary == "پیام" && it.status == "PENDING" }
            val auditBefore = db.auditDao().observeRecent().first().size
            val cmd = item("APPROVE_REQUEST", mapOf("requestId" to pending.id))
            val a1 = ok(sync(owner, listOf(cmd))).reply.acks.single()
            hubNeed(a1.status == AckStatus.APPLIED, "approve $a1")
            hubNeed(db.syncRequestDao().getById(pending.id)!!.status == "APPROVED", "not approved")
            val audit = db.auditDao().observeRecent().first()
            hubNeed(audit.size == auditBefore + 1, "audit rows ${audit.size - auditBefore}")
            hubNeed(audit.first().user == "گوشیِ «گوشیِ صاحب‌کار»", "audit names «${audit.first().user}»")
            val a2 = ok(sync(owner, listOf(cmd))).reply.acks.single()
            hubNeed(a2 == a1, "the same command again answered $a2")
            hubNeed(db.auditDao().observeRecent().first().size == auditBefore + 1, "a resend wrote again")
            val stale = ok(sync(owner, listOf(item("APPROVE_REQUEST", mapOf("requestId" to pending.id))))).reply.acks.single()
            hubNeed(stale.status == AckStatus.REJECTED && stale.reason.contains("قبلاً رسیدگی شده"), "stale $stale")
        }

        hubCheck("a debt follow-up from the owner's phone is recorded once, however often it is sent") {
            suspend fun follows() = db.domainEventDao().observeFor("customer", "حمید").first().size
            val before = follows()
            val cmd = item("DEBT_FOLLOW_UP", mapOf("customer" to "حمید", "outcome" to "PROMISED", "untilDays" to 3L))
            hubNeed(ok(sync(owner, listOf(cmd))).reply.acks.single().status == AckStatus.APPLIED, "not applied")
            ok(sync(owner, listOf(cmd)))
            hubNeed(follows() == before + 1, "events ${follows() - before}")
        }

        hubCheck("a worker's phone cannot send the owner's commands") {
            val a = ok(sync(worker, listOf(item("APPROVE_REQUEST", mapOf("requestId" to 1L))))).reply.acks.single()
            hubNeed(a.status == AckStatus.REJECTED, "a worker approved: $a")
        }

        // ------------------------------------------------ حمله‌های بازبینیِ فازِ B

        fun sealAsk(cred: DeviceCred, items: List<OutboxWire>, off: Long, claims: Map<String, Any?> = emptyMap()) =
            HubEnvelope.sealRequest(
                cred.deviceId, cred.key, HubProtocol.PATH_SYNC, System.currentTimeMillis() + off,
                SyncAsk(System.currentTimeMillis(), "", items, "1.11.0", "android").toJson() + claims,
            )

        fun opened(cred: DeviceCred, s: HubEnvelope.Sealed, body: String): com.afghanjama.lan.SyncReply =
            HubEnvelope.openResponse(cred.deviceId, cred.key, HubProtocol.PATH_SYNC, s.nonce, body)
                ?.let { com.afghanjama.lan.SyncReply.parse(it) } ?: error("the reply did not open")

        hubCheck("a hub restart forgets every nonce, yet a replayed job, approval or follow-up changes nothing") {
            val off = offset()
            val text = "پیش از خاموش شدنِ کامپیوتر"
            val note = sealAsk(worker, listOf(item("NOTE", mapOf("text" to text))), off)
            hubNeed(raw(port, "POST", HubProtocol.PATH_SYNC, note.headers, note.body).first == 200, "note refused")
            val req = db.syncRequestDao().observePending().first().last { it.deviceId == worker.deviceId && it.note == text }
            val approve = sealAsk(owner, listOf(item("APPROVE_REQUEST", mapOf("requestId" to req.id))), off)
            val firstApprove = raw(port, "POST", HubProtocol.PATH_SYNC, approve.headers, approve.body)
            hubNeed(firstApprove.first == 200, "approve refused")
            val approveAck = opened(owner, approve, firstApprove.second).acks.single()
            hubNeed(approveAck.status == AckStatus.APPLIED, "approve $approveAck")
            val debt = sealAsk(owner, listOf(item("DEBT_FOLLOW_UP", mapOf("customer" to "ناصر", "outcome" to "PROMISED", "untilDays" to 2L))), off)
            hubNeed(raw(port, "POST", HubProtocol.PATH_SYNC, debt.headers, debt.body).first == 200, "follow-up refused")

            suspend fun state() = listOf(
                db.syncRequestDao().recentFor(ahmad, 200).size,
                db.auditDao().observeRecent().first().size,
                db.domainEventDao().observeFor("customer", "ناصر").first().size,
            )
            val before = state()
            hubNeed(before[2] == 1, "follow-ups ${before[2]}")
            hubNeed(db.syncRequestDao().getById(req.id)!!.status == "APPROVED", "not approved")

            // «روشن شدنِ دوباره»: پروسهٔ تازه روی همان دفتر، با حافظهٔ nonceِ خالی.
            val p2 = freePort()
            val reborn = LanHub(
                InetAddress.getLoopbackAddress(), p2, guardClock = { clock.get() }, licenceLoaded = {},
                offIntervalMs = 100, onIntervalMs = 100, machine = { "machine-A" }, pairLongPollMs = 1_500,
                findTwin = { _, _, _ -> null },
            )
            extra += reborn
            try {
                reborn.start(settings, host)
                var ready = false
                repeat(100) { if (!ready) { ready = reborn.state.value.serving && reborn.hubService()?.ready == true; if (!ready) delay(30) } }
                hubNeed(ready, "the restarted hub is not serving: ${reborn.state.value}")
                // همان بایت‌ها: نگهبانِ تکرار تازه است و آن‌ها را می‌پذیرد (۲۰۰)…
                val r1 = raw(p2, "POST", HubProtocol.PATH_SYNC, note.headers, note.body)
                val r2 = raw(p2, "POST", HubProtocol.PATH_SYNC, approve.headers, approve.body)
                val r3 = raw(p2, "POST", HubProtocol.PATH_SYNC, debt.headers, debt.body)
                hubNeed(listOf(r1.first, r2.first, r3.first) == listOf(200, 200, 200), "replays after restart: ${r1.first} ${r2.first} ${r3.first}")
                // …ولی کلیدِ تکرارنشدنیِ هر کار در دفتر است: هیچ چیز دوباره اجرا نمی‌شود.
                hubNeed(opened(worker, note, r1.second).acks.single().status == AckStatus.DUPLICATE, "the note was queued again")
                hubNeed(opened(owner, approve, r2.second).acks.single() == approveAck, "the approval answered differently")
                hubNeed(state() == before, "a replay after restart changed the ledger: $before -> ${state()}")
                // و همان پروسه بارِ دوم را می‌شناسد.
                hubNeed(raw(p2, "POST", HubProtocol.PATH_SYNC, note.headers, note.body).first == 401, "a second replay passed")
            } finally {
                reborn.stop()
            }
        }

        hubCheck("no owner command, owner view or role can come from a worker's phone, whatever its message claims") {
            ok(sync(worker, listOf(item("NOTE", mapOf("text" to "برای آزمونِ نقش")))))
            val target = db.syncRequestDao().observePending().first().last { it.deviceId == worker.deviceId }
            val follows = db.domainEventDao().observeFor("customer", "حمید").first().size
            val cmds = listOf(
                item("APPROVE_REQUEST", mapOf("requestId" to target.id)),
                item("REJECT_REQUEST", mapOf("requestId" to target.id)),
                item("DEBT_FOLLOW_UP", mapOf("customer" to "حمید", "outcome" to "PROMISED", "untilDays" to 1L)),
            )
            // پیام خودش را «صاحب‌کار» می‌خوانَد؛ هویت از جفت شدن است نه از پیام.
            val s = sealAsk(worker, cmds, offset(), mapOf("role" to "owner", "person" to "", "deviceId" to owner.deviceId))
            val (status, body) = raw(port, "POST", HubProtocol.PATH_SYNC, s.headers, s.body)
            hubNeed(status == 200, "status $status")
            val reply = opened(worker, s, body)
            hubNeed(reply.acks.size == 3 && reply.acks.all { it.status == AckStatus.REJECTED }, "acks ${reply.acks}")
            hubNeed(reply.role == HubRole.WORKER && reply.snapshot?.owner == null, "role ${reply.role}, owner view ${reply.snapshot?.owner != null}")
            hubNeed(db.syncRequestDao().getById(target.id)!!.status == "PENDING", "the worker decided its own request")
            hubNeed(db.domainEventDao().observeFor("customer", "حمید").first().size == follows, "a worker wrote a follow-up")
            hubNeed(repo.lanDevice(worker.deviceId)!!.role == "worker", "the worker's role changed")
            repo.rejectRequest(target.id)
        }

        lateinit var flooder: DeviceCred

        hubCheck("a flooding phone stops at its cap of waiting requests, and the owner's phone still syncs") {
            flooder = pair("گوشیِ محمود", HubRole.WORKER, "[T2] محمود")
            val long = "این پیام برای پر کردنِ صندوق است. ".repeat(12).take(300)
            var queued = 0
            var refused = 0
            repeat(6) {
                val r = ok(sync(flooder, List(HubProtocol.MAX_OUTBOX_PER_SYNC) { item("NOTE", mapOf("text" to long)) }))
                queued += r.reply.acks.count { it.status == AckStatus.QUEUED }
                refused += r.reply.acks.count { it.status == AckStatus.REJECTED }
            }
            hubNeed(queued == HubProtocol.MAX_PENDING_PER_DEVICE && refused == 300 - queued, "queued $queued, refused $refused")
            val o = sync(owner)
            hubNeed(o is SyncResult.Ok, "the owner's phone no longer syncs after the flood: $o")
            val view = (o as SyncResult.Ok).reply.snapshot!!.owner!!
            hubNeed(view.pending.size <= HubProtocol.SNAPSHOT_PENDING_LINES, "owner view carries ${view.pending.size} requests")
            for (r in db.syncRequestDao().observePending().first().filter { it.deviceId == flooder.deviceId }) repo.rejectRequest(r.id)
        }

        hubCheck("revoke waits for a sync in flight; nothing from that phone lands after it returns") {
            coroutineScope {
                val off = offset()
                val calls = List(20) { n ->
                    async {
                        runCatching {
                            phone.sync(
                                flooder, "127.0.0.1", port,
                                SyncAsk(System.currentTimeMillis(), "", List(5) { item("NOTE", mapOf("text" to "هم‌زمان $n-$it")) }, "1.11.0", "android"),
                                off,
                            )
                        }
                    }
                }
                delay(20)
                hubNeed(hub.revokeDevice(flooder.deviceId), "revoke refused")
                suspend fun landed() = db.syncRequestDao().observePending().first().count { it.deviceId == flooder.deviceId }
                val atRevoke = landed()
                calls.awaitAll()
                hubNeed(landed() == atRevoke, "work landed after the revoke returned: $atRevoke -> ${landed()}")
                for (r in db.syncRequestDao().observePending().first().filter { it.deviceId == flooder.deviceId }) repo.rejectRequest(r.id)
            }
            // تلاش‌های ردشده این نشانی را بسته‌اند؛ پنج دقیقه بعد.
            clock.addAndGet(LanGuard.BLOCK_MS + 1)
        }

        hubCheck("a captured request replayed byte for byte is refused and changes nothing") {
            val off = offset()
            val ask = SyncAsk(System.currentTimeMillis(), "", listOf(item("NOTE", mapOf("text" to "یک بار"))), "1.11.0", "android")
            val sealed = HubEnvelope.sealRequest(worker.deviceId, worker.key, HubProtocol.PATH_SYNC, System.currentTimeMillis() + off, ask.toJson())
            val first = raw(port, "POST", HubProtocol.PATH_SYNC, sealed.headers, sealed.body)
            hubNeed(first.first == 200, "the original was refused: ${first.first}")
            val rows = db.syncRequestDao().observeAll().first().size
            val replay = raw(port, "POST", HubProtocol.PATH_SYNC, sealed.headers, sealed.body)
            hubNeed(replay.first == 401, "the replay got ${replay.first}")
            hubNeed(db.syncRequestDao().observeAll().first().size == rows, "the replay wrote")
            // سرخطِ تازه با همان جعبه: AAD نمی‌خواند.
            val swapped = sealed.headers + (HubProtocol.HEADER_NONCE to com.afghanjama.lan.LanCrypto.b64(LanPrims.random(12)))
            hubNeed(raw(port, "POST", HubProtocol.PATH_SYNC, swapped, sealed.body).first == 401, "a fresh nonce on an old box passed")
        }

        hubCheck("a request with the phone clock eleven minutes off is refused with the hub's time") {
            val r = sync(worker, off = offset() - 11 * 60_000L)
            hubNeed(r is SyncResult.Clock && kotlin.math.abs(r.serverTime - clock.get()) < 5_000, "got $r")
            hubNeed(sync(worker) is SyncResult.Ok, "the right time was refused afterwards")
        }

        hubCheck("ten bad requests a minute shut this address out, even with the right key; five minutes later it works") {
            val forged = DeviceCred(worker.deviceId, LanPrims.random(32), worker.hubId, "", HubRole.WORKER, ahmad)
            repeat(LanGuard.MAX_FAILURES) { i ->
                val r = sync(forged)
                hubNeed(r is SyncResult.Unpaired || r is SyncResult.Blocked, "forged ${i + 1}: $r")
            }
            hubNeed(sync(worker) is SyncResult.Blocked, "the right key got through a block")
            clock.addAndGet(LanGuard.BLOCK_MS + 1)
            hubNeed(sync(worker) is SyncResult.Ok, "still blocked after five minutes")
        }

        hubCheck("a read-only hub queues nothing, and every reply carries the licence") {
            val now = System.currentTimeMillis()
            LicenceGate.install(LicenceFacts(null, "X", now - 400 * 86_400_000L, now, ordersSinceTrialStart = 999))
            try {
                val r = ok(sync(worker, listOf(item("NOTE", mapOf("text" to "نوشته نشود")))))
                hubNeed(r.reply.acks.single().reason == LicenceText.LAN_REFUSAL, "ack ${r.reply.acks}")
                hubNeed(r.reply.licenceState == "READ_ONLY" && !r.reply.licenceWritable, "licence ${r.reply.licenceState}/${r.reply.licenceWritable}")
            } finally {
                LicenceGate.resetForTest()
            }
        }

        hubCheck("revoking a phone takes effect on its very next sync") {
            hubNeed(hub.revokeDevice(worker.deviceId), "revoke refused")
            hubNeed(sync(worker) is SyncResult.Unpaired, "a revoked phone synced")
            val row = repo.lanDevice(worker.deviceId)!!
            hubNeed(row.keyB64.isEmpty() && row.revokedAt != null, "row $row")
        }

        hubCheck("a 1.10 phone (protocol 1) hears «update this phone» in Dari when the old path is closed") {
            val v1 = LanClient("127.0.0.1", "482913", port, allowLoopback = true)
            hubNeed(v1.ping() is LanResult.Ok, "ping must still answer")
            hubNeed(v1.board() == LanResult.Err(HubService.UPDATE_PHONE), "got ${v1.board()}")
        }

        hubCheck("with the old path open a 1.10 phone works, and its use keeps «auto» open") {
            hub.hubService()!!.updateIdentity { it.copy(legacyV1 = LegacyMode.ON.name) }
            val v1 = LanClient("127.0.0.1", "482913", port, allowLoopback = true)
            hubNeed(v1.board() is LanResult.Ok, "board ${v1.board()}")
            hubNeed(v1.sendRequest("گوشیِ قدیم", ahmad, "NOTE", "پیام", note = "از ۱.۱۰") is LanResult.Ok, "request refused")
            hubNeed(repo.hubRow()!!.lastV1At != null, "the old phone's visit was not noted")
            hub.hubService()!!.updateIdentity { it.copy(legacyV1 = LegacyMode.AUTO.name) }
            hubNeed(v1.board() is LanResult.Ok, "auto closed while an old phone was still coming")
        }

        hubCheck("a new phone against a 1.10 hub (no protocolMax) falls back to protocol 1") {
            val oldDb = openDatabase(File(dir, "old-hub.db"))
            val p = freePort()
            val old = LanServer(oldDb, InetAddress.getLoopbackAddress(), p)
            hubNeed(old.start("551177"), "old server did not start")
            try {
                val ping = phone.ping("127.0.0.1", p).ok("ping old hub")
                hubNeed(ping.protocolMax == 1 && ping.hubId == null, "old hub looked like $ping")
                hubNeed(phone.hello("127.0.0.1", p) is LanResult.Err, "an old hub offered protocol 2 pairing")
            } finally {
                old.stop()
                runCatching { oldDb.closeConnection() }
            }
        }

        hubCheck("a ledger restored on another computer stays silent until the owner confirms, and not while the old one answers") {
            hub.stop()
            val p = freePort()
            val moved = LanHub(
                InetAddress.getLoopbackAddress(), p, guardClock = { clock.get() }, licenceLoaded = {},
                offIntervalMs = 100, onIntervalMs = 100, machine = { "machine-B" }, findTwin = { _, _, _ -> twinRef.get() },
            )
            extra += moved
            moved.start(settings, host)
            var waited = 0
            while (moved.state.value.off != HubOff.MOVED && waited < 3_000) { delay(30); waited += 30 }
            hubNeed(moved.state.value.off == HubOff.MOVED, "state ${moved.state.value}")
            hubNeed(!listeningOn(p), "a moved ledger served")
            twinRef.set("192.168.77.9")
            hubNeed(moved.confirmHere() == "192.168.77.9", "the old hub was not found")
            hubNeed(moved.state.value.off == HubOff.DUPLICATE_HUB && !listeningOn(p), "served next to its twin: ${moved.state.value}")
            twinRef.set(null)
            hubNeed(moved.confirmHere() == null, "confirm refused")
            hubNeed(moved.state.value.serving && listeningOn(p), "not serving after confirm: ${moved.state.value}")
            val ping = phone.ping("127.0.0.1", p).ok("ping moved")
            hubNeed(ping.hubId == repo.hubRow()!!.hubId, "the hub id did not travel with the ledger")
            hubNeed(repo.hubRow()!!.machine == "machine-B", "machine ${repo.hubRow()!!.machine}")
            // گوشیِ صاحب‌کار بی جفت شدنِ دوباره همان‌جا کار می‌کند.
            val r = phone.sync(owner, "127.0.0.1", p, SyncAsk(System.currentTimeMillis(), "", emptyList(), "1.11.0", "android"),
                (phone.ping("127.0.0.1", p) as LanResult.Ok).value.serverTime!! - System.currentTimeMillis())
            hubNeed(r is SyncResult.Ok, "the owner's phone lost its pairing with the restore: $r")
        }
    } finally {
        hub.stop()
        extra.forEach { it.stop() }
        runCatching { db.closeConnection() }
    }
    return hubFailed
}

@Suppress("unused")
private fun Ack.short(): String = "$status:$reason"

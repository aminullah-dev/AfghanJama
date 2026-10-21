package com.afghanjama.lan

import com.afghanjama.AppVersion
import com.afghanjama.data.Db
import com.afghanjama.data.entities.LanDevice
import com.afghanjama.data.entities.LegacyMode
import com.afghanjama.data.repo.Repo
import com.afghanjama.licence.LicenceGate
import com.afghanjama.util.randomUuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * قراردادِ ۲ روی کامپیوتر (یا گوشیِ اصلیِ اندرویدی) — جفت شدن و همگام‌سازی.
 *
 * **قانونِ «یک نویسنده» سرِ جایش است.** گوشی هرگز دفتر را نمی‌نویسد: کارِ
 * کارگر در صندوقِ درخواست‌ها می‌نشیند تا کارفرما تأیید کند، و فرمانِ
 * صاحب‌کار از فهرستِ بسته‌ای است که همان تابع‌های همیشگیِ دفتر را صدا
 * می‌زند ([Repo.applyOwnerCommand]).
 *
 * **دستگاهِ جفت‌نشده هیچ نمی‌گیرد.** همگام‌سازی پیش از هر خواندنی از دفتر
 * چهار چیز را می‌سنجد، به این ترتیب: نشانیِ بسته نیست ([LanGuard])، زمانِ
 * درخواست در بازه است، دستگاه در دفتر هست و کلیدش زنده است، و پاکت با همان
 * کلید باز می‌شود. بعد nonce ثبت می‌شود ([ReplayGuard]) و تکرارش رد. هر
 * شکست یکی به شمارِ همان نشانی می‌افزاید؛ ده تا در دقیقه و آن نشانی پنج
 * دقیقه بسته است — همان نگهبانِ رمزِ قراردادِ ۱، مشترک میانِ هر دو.
 *
 * جوابِ هر شکستِ پیش از باز شدنِ پاکت یکی است («این گوشی شناخته نشد») و
 * هیچ‌چیزِ دفتر در آن نیست؛ فقط ساعتِ نادرست جوابِ جدا دارد، با ساعتِ
 * کامپیوتر — که راز نیست و گوشی برای درست کردنِ ساعتش لازمش دارد.
 */
class HubService(
    private val db: Db,
    private val guard: LanGuard,
    private val clock: () -> Long = { System.currentTimeMillis() },
    /** نشانی‌ها و درگاهِ این کامپیوتر — از [LanHub]. */
    private val addresses: () -> Pair<List<String>, Int>,
    /** پنجرهٔ «افزودنِ گوشی» و حافظهٔ تکرار با [LanHub] می‌مانند، نه با یک سرور. */
    val pairing: HubPairing = HubPairing(clock),
    val replay: ReplayGuard = ReplayGuard(clock),
    /** سقفِ انتظارِ پرسشِ «اجازه»؛ آزمونِ دودی کوتاهش می‌کند. */
    private val longPollMs: Long = HubProtocol.PAIR_LONG_POLL_MS,
) {

    data class HubInfo(val hubId: String, val hubName: String, val addresses: List<String>, val port: Int)

    /** شناسنامهٔ کارگاه از دفتر ([prepare])؛ تا خوانده نشده، قراردادِ ۲ «آماده نیست». */
    @Volatile private var identity: com.afghanjama.data.entities.LanHubRow? = null

    /** دفتر روی دستگاهِ دیگری بوده و کارفرما هنوز نگفته «مرکز اینجاست». */
    @Volatile var moved: Boolean = false
        private set

    val ready: Boolean get() = identity != null && !moved

    /** [prepare] تمام شد (درست یا نادرست). سرور تا آن وقت جواب نمی‌دهد. */
    private val prepared = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** تا [ms] منتظرِ [prepare] — چند میلی‌ثانیهٔ اولِ روشن شدن. */
    suspend fun awaitPrepared(ms: Long) {
        kotlinx.coroutines.withTimeoutOrNull(ms) { prepared.await() }
    }

    fun hubId(): String = identity?.hubId.orEmpty()

    /** ساعتِ همین سرویس — `/ping` همین را به گوشی می‌گوید تا بازهٔ زمان یکی باشد. */
    fun now(): Long = clock()

    private fun info(): HubInfo {
        val (ips, port) = addresses()
        val row = identity
        return HubInfo(row?.hubId.orEmpty(), row?.hubName.orEmpty(), ips.filter { HostAddress.isPrivate(it) }.take(8), port)
    }

    /**
     * شناسنامه را از دفتر می‌خوانَد (یا می‌سازد). اگر دفتر آخرین بار روی
     * دستگاهِ دیگری سرور بود (بازیابی روی کامپیوترِ تازه)، [moved] می‌شود و
     * هیچ گوشی‌ای جواب نمی‌گیرد تا [confirmHere].
     */
    suspend fun prepare(machine: String, defaultName: String): Boolean = try {
        var row = repo.ensureHubRow(machine, defaultName, clock())
        if (row.machine.isBlank()) {
            repo.updateHubRow { it.copy(machine = machine) }
            row = repo.hubRow() ?: row
        }
        moved = row.machine != machine
        identity = row
        !moved
    } finally {
        prepared.complete(Unit)
    }

    /** کارفرما گفت «این دستگاه حالا مرکزِ کارگاه است». */
    suspend fun confirmHere(machine: String) {
        repo.updateHubRow { it.copy(machine = machine) }
        identity = repo.hubRow()
        moved = false
    }

    /** نامِ کارگاه برای گوشی‌ها و حالتِ گوشی‌های قدیم — از صفحهٔ کامپیوتر. */
    suspend fun updateIdentity(change: (com.afghanjama.data.entities.LanHubRow) -> com.afghanjama.data.entities.LanHubRow) {
        repo.updateHubRow(change)
        identity = repo.hubRow()
    }

    fun identityRow(): com.afghanjama.data.entities.LanHubRow? = identity

    class Reply(val status: Int, val body: String)

    private val repo = Repo(db)

    /** یک همگام‌سازی در هر لحظه برای هر دستگاه — دو تلاشِ هم‌زمان یک کار را دو بار نمی‌سنجند. */
    private val inbox = Mutex()

    private fun json(status: Int, vararg pairs: Pair<String, Any?>): Reply =
        Reply(status, LanJson.write(mapOf("ok" to false, *pairs)))

    private fun unknown(): Reply = json(401, "code" to HubProtocol.ERR_AUTH, "error" to UNKNOWN_DEVICE)

    private fun blocked(waitMs: Long): Reply =
        json(429, "code" to HubProtocol.ERR_BLOCKED, "error" to LanGuard.blockedMessage(waitMs))

    /** هر مسیرِ `/v2/…`؛ [peer] نشانیِ IPv4ِ گوشی. */
    suspend fun route(method: String, path: String, request: LanWire.Request, peer: String): Reply {
        if (method != "POST") return json(405, "error" to "مسیر پیدا نشد.")
        if (!ready) return json(503, "code" to HubProtocol.ERR_DUPLICATE_HUB, "error" to NOT_READY)
        guard.blockedFor(peer).takeIf { it > 0 }?.let { return blocked(it) }
        return when (path) {
            HubProtocol.PATH_HELLO -> hello()
            HubProtocol.PATH_PAIR -> pair(request.body, peer)
            HubProtocol.PATH_PAIR_STATUS -> pairStatus(request.body, peer)
            HubProtocol.PATH_SYNC -> sync(request, peer)
            else -> json(404, "error" to "مسیر پیدا نشد.")
        }
    }

    // ================================================================ جفت شدن

    private fun closed(): Reply = json(404, "code" to HubProtocol.ERR_CLOSED, "error" to HubClient.PAIR_CLOSED)

    private fun hello(): Reply {
        val h = pairing.hello(info().hubId) ?: return closed()
        return Reply(200, LanJson.write(h.toJson()))
    }

    private fun pair(body: String, peer: String): Reply {
        val o = LanJson.parseObject(body) ?: return json(400, "error" to "درخواست خوانده نشد.")
        pairing.clearRejected()
        return when (val r = pairing.submit(o, peer)) {
            is HubPairing.Submit.Ok -> {
                guard.succeeded(peer)
                Reply(200, LanJson.write(mapOf("ok" to true, "requestId" to r.requestId)))
            }
            HubPairing.Submit.Closed -> closed()
            HubPairing.Submit.Busy -> json(409, "code" to HubProtocol.ERR_BUSY, "error" to BUSY)
            HubPairing.Submit.OwnerNeedsQr -> json(403, "code" to HubProtocol.ERR_ROLE, "error" to HubClient.OWNER_NEEDS_QR)
            is HubPairing.Submit.Wrong -> {
                val v = guard.admit(peer, false)
                if (v is LanGuard.Verdict.Blocked) blocked(v.waitMs)
                else json(401, "code" to HubProtocol.ERR_AUTH, "error" to if (r.closed) HubPairing.TOO_MANY else WRONG_SECRET)
            }
        }
    }

    private suspend fun pairStatus(body: String, peer: String): Reply {
        val o = LanJson.parseObject(body) ?: return json(400, "error" to "درخواست خوانده نشد.")
        val requestId = o.jStr("requestId").orEmpty()
        val key = pairing.keyFor(requestId)
            ?: return json(404, "code" to HubProtocol.ERR_CLOSED, "error" to "درخواستِ جفت شدن دیگر نیست؛ دوباره اسکن کنید.")
        val askNonce = HubEnvelope.openPairStatus(o, requestId, key)
        if (askNonce == null) {
            val v = guard.admit(peer, false)
            return if (v is LanGuard.Verdict.Blocked) blocked(v.waitMs) else unknown()
        }
        val outcome = pairing.await(requestId, longPollMs)
        val i = info()
        val now = clock()
        val grant = when (outcome) {
            is HubPairing.Outcome.Approved -> outcome.grant.copy(serverTime = now)
            is HubPairing.Outcome.Rejected -> PairGrant(PairState.REJECTED, i.hubId, i.hubName, now, reason = outcome.reason)
            null -> PairGrant(if (pairing.alive(requestId)) PairState.WAIT else PairState.EXPIRED, i.hubId, i.hubName, now)
        }
        return Reply(200, HubEnvelope.pairReply(grant, requestId, key, askNonce))
    }

    /**
     * کارفرما «اجازه» داد. نقش و آدم را خودش انتخاب کرده؛ گوشی فقط گفته بود
     * چه می‌خواهد. برای کارگر [person] لازم است؛ گوشیِ صاحب‌کار فقط با QR.
     *
     * @return `null` اگر انجام شد؛ وگرنه دلیل.
     */
    suspend fun approve(role: HubRole, person: String, name: String? = null): String? {
        val p = pairing.waiting() ?: return "گوشی‌ای منتظرِ اجازه نیست."
        if (role == HubRole.OWNER && p.via != PairVia.QR) return HubClient.OWNER_NEEDS_QR
        val label = if (role == HubRole.WORKER) com.afghanjama.data.entities.requestWorkerLabel(person) else ""
        if (role == HubRole.WORKER && label.isEmpty()) return "برای گوشیِ کارگر، کارگرش را انتخاب کنید."
        val key = LanPrims.random(LanCrypto.KEY_BYTES)
        val now = clock()
        val device = LanDevice(
            deviceId = randomUuid().toString(),
            name = (name?.trim()?.takeIf { it.isNotEmpty() } ?: p.info.name).take(PhoneInfo.NAME_LIMIT),
            platform = p.info.platform,
            role = role.wire,
            personLabel = label,
            keyB64 = LanCrypto.b64(key),
            createdAt = now,
            lastIp = p.ip,
            appVersion = p.info.appVersion,
        )
        repo.addLanDevice(device)
        val i = info()
        val grant = PairGrant(
            PairState.APPROVED, i.hubId, i.hubName, now, device.deviceId, key, role, label, i.addresses, i.port,
        )
        if (!pairing.decide(p.requestId, HubPairing.Outcome.Approved(grant))) {
            // پنجره همین حالا بسته شد؛ ردیفِ ساخته‌شده کلید را به کسی نداد.
            repo.revokeLanDevice(device.deviceId, now)
            return "پنجرهٔ «افزودنِ گوشی» بسته شد؛ دوباره باز کنید."
        }
        return null
    }

    fun reject(): Boolean {
        val p = pairing.waiting() ?: return false
        return pairing.decide(p.requestId, HubPairing.Outcome.Rejected("کارفرما اجازه نداد."))
    }

    /** «قطعِ دسترسی»: کلید از دفتر و nonceها از حافظه — همین حالا. */
    suspend fun revoke(deviceId: String): Boolean {
        val ok = repo.revokeLanDevice(deviceId, clock())
        replay.forget(deviceId)
        return ok
    }

    // ================================================================ همگام‌سازی

    private suspend fun sync(request: LanWire.Request, peer: String): Reply {
        val now = clock()
        val head = HubEnvelope.readHead { request.header(it) }
        val stale = head?.let { replay.timeOk(it.time) as? ReplayGuard.Verdict.Stale }
        val device = head?.takeIf { stale == null }?.let { repo.lanDevice(it.deviceId) }?.takeIf { it.active }
        val key = device?.let { LanCrypto.unb64(it.keyB64) }?.takeIf { it.size == LanCrypto.KEY_BYTES }
        val plain = if (head != null && key != null) HubEnvelope.openRequest(head, key, HubProtocol.PATH_SYNC, request.body) else null

        when (val v = guard.admit(peer, plain != null)) {
            is LanGuard.Verdict.Blocked -> return blocked(v.waitMs)
            LanGuard.Verdict.Wrong -> return if (stale != null) {
                json(401, "code" to HubProtocol.ERR_TIME, "serverTime" to stale.serverTime, "error" to CLOCK)
            } else unknown()
            LanGuard.Verdict.Pass -> Unit
        }
        head!!; device!!; key!!; plain!!

        when (replay.admit(head.deviceId, head.nonce, head.time)) {
            ReplayGuard.Verdict.Ok -> Unit
            ReplayGuard.Verdict.Full -> return json(429, "code" to HubProtocol.ERR_BLOCKED, "error" to "درخواست بیش از اندازه؛ کمی بعد.")
            else -> {
                // تکرارِ پاکتی که پیش‌تر پذیرفته شد: کلید را دارد ولی تازه نیست.
                guard.failed(peer)
                return unknown()
            }
        }

        val ask = SyncAsk.parse(plain)
            ?: return Reply(200, HubEnvelope.sealResponse(head, key, HubProtocol.PATH_SYNC, mapOf("ok" to false)))

        val acks = inbox.withLock {
            ask.outbox.map { w ->
                val cmd = HubCommand.of(w.type)
                when {
                    cmd == null -> Ack(w.key, AckStatus.REJECTED, "نوعِ درخواست پذیرفته نیست.")
                    cmd.queues -> repo.submitHubWork(device, w, ask.phoneNow, now)
                    else -> repo.applyOwnerCommand(device, w, now)
                }
            }
        }
        repo.touchLanDevice(device.deviceId, now, peer, ask.appVersion)

        val fresh = repo.lanDevice(device.deviceId) ?: device
        val snapshot = HubViews.snapshot(db, repo, fresh, now)
            ?: return unknown()
        val cursor = HubViews.cursorOf(snapshot)
        val i = info()
        val lic = LicenceGate.current()
        val reply = SyncReply(
            serverTime = now,
            acks = acks,
            cursor = cursor,
            snapshot = snapshot.takeIf { cursor != ask.cursor },
            licenceState = lic?.state?.name,
            licenceWritable = lic?.canWrite ?: true,
            role = HubRole.of(fresh.role) ?: HubRole.WALL,
            person = fresh.personLabel,
            deviceName = fresh.name,
            hubId = i.hubId,
            hubName = i.hubName,
            hubVersion = AppVersion.current,
            addresses = i.addresses,
        )
        return Reply(200, HubEnvelope.sealResponse(head, key, HubProtocol.PATH_SYNC, reply.toJson()))
    }

    // ================================================================ قراردادِ ۱

    /** گوشیِ نسخهٔ قدیم (رمزِ شش‌رقمی) پذیرفته می‌شود؟ ([LegacyMode]) */
    suspend fun legacyAllowed(): Boolean {
        val row = repo.hubRow() ?: return true
        return LegacyMode.allowed(LegacyMode.of(row.legacyV1), row.lastV1At, clock())
    }

    /** یک درخواستِ قراردادِ ۱ پذیرفته شد — «آخرین بار» برای [LegacyMode.AUTO]. */
    suspend fun noteLegacy() {
        val now = clock()
        val row = repo.hubRow() ?: return
        // هر دقیقه یک بار بس است؛ تابلوی دیواری هر ۱۵ ثانیه می‌پرسد.
        if (row.lastV1At != null && now - row.lastV1At < 60_000L) return
        repo.updateHubRow { it.copy(lastV1At = now) }
    }

    companion object {
        const val UNKNOWN_DEVICE =
            "این گوشی را کامپیوترِ کارگاه نمی‌شناسد — شاید دسترسی‌اش برداشته شده. روی کامپیوتر «افزودنِ گوشی» را بزنید و دوباره جفت کنید."
        const val CLOCK = "ساعتِ این گوشی با ساعتِ کامپیوترِ کارگاه بیش از ده دقیقه فرق دارد."
        const val WRONG_SECRET = "رمزِ جفت شدن درست نیست. رمز یا QR را دوباره از صفحهٔ «کارگاهِ وصل» روی کامپیوتر بخوانید."
        const val NOT_READY =
            "کامپیوترِ کارگاه هنوز آماده نیست؛ اگر دفترش تازه از پشتیبان برگشته، روی کامپیوتر «مرکز اینجاست» را بزنید."
        const val BUSY = "گوشیِ دیگری همین حالا منتظرِ اجازهٔ کارفرماست. کمی بعد دوباره امتحان کنید."

        /** پیامی که گوشیِ ۱.۹ و ۱.۱۰ وقتی قراردادِ ۱ بسته است می‌بیند. */
        const val UPDATE_PHONE =
            "این کامپیوترِ کارگاه نسخهٔ تازهٔ خیاط‌یار دارد و گوشی‌های نسخهٔ قدیم را دیگر نمی‌پذیرد. " +
                "برنامهٔ این گوشی را به نسخهٔ ۱.۱۱ یا تازه‌تر به‌روز کنید و با «افزودنِ گوشی» دوباره وصل شوید."
    }
}

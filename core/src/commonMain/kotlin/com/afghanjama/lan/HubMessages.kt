package com.afghanjama.lan

import com.afghanjama.ui.vm.BoardRow

/*
 * پیام‌های قراردادِ ۲ — شکلشان یک جا، برای کامپیوتر (سازنده) و گوشی
 * (خواننده). هر خواندنی سقفِ طول دارد: حتی پیامِ سنجیده هم نباید متنِ
 * یک‌مگابایتی در صفحهٔ کسی بنشاند.
 */

private fun String.cap(n: Int): String = if (length <= n) this else take(n)

/** جوابِ `/ping` — عمومی، بی هیچ دادهٔ دفتر. */
data class PingInfo(
    val app: String,
    /** کمترین و بیشترین قرارداد. کامپیوترِ ۱.۱۰ و پیش از آن فقط `protocol: 1` دارد. */
    val protocolMin: Int,
    val protocolMax: Int,
    val hubId: String?,
    val serverTime: Long?,
    val pairingOpen: Boolean,
    val appVersion: String?,
) {
    companion object {
        /**
         * **`protocolMax` نبوده یعنی ۱.** دستگاهِ اصلیِ ۱.۹ و ۱.۱۰ (گوشیِ اندرویدی
         * یا کامپیوتر) فقط `protocol` می‌فرستد؛ گوشیِ تازه با آن همان قراردادِ ۱
         * را حرف می‌زند، نه اینکه «نسخهٔ ناشناخته» بگوید و وصل نشود.
         */
        fun parse(o: Map<String, Any?>): PingInfo? {
            if (o.jBool("ok") != true) return null
            val legacy = o.jInt("protocol") ?: 1
            val max = o.jInt("protocolMax") ?: legacy
            val min = o.jInt("protocolMin") ?: minOf(legacy, max)
            return PingInfo(
                app = o.jStr("app").orEmpty().cap(40),
                protocolMin = min,
                protocolMax = max,
                hubId = o.jStr("hubId")?.cap(64),
                serverTime = o.jLong("serverTime"),
                pairingOpen = o.jBool("pairingOpen") == true,
                appVersion = o.jStr("appVersion")?.cap(24),
            )
        }
    }
}

/** جوابِ `/v2/pair/hello` — فقط وقتی پنجره باز است. */
data class PairHello(val hubId: String, val pairingId: String, val hubPub: ByteArray, val expiresInMs: Long) {
    fun toJson(): Map<String, Any?> = mapOf(
        "ok" to true, "protocol" to 2, "hubId" to hubId, "pairingId" to pairingId,
        "hubPub" to LanCrypto.b64(hubPub), "expiresIn" to expiresInMs,
    )

    override fun equals(other: Any?): Boolean = other is PairHello && hubId == other.hubId &&
        pairingId == other.pairingId && hubPub.contentEquals(other.hubPub) && expiresInMs == other.expiresInMs
    override fun hashCode(): Int = (hubId + pairingId).hashCode()

    companion object {
        fun parse(o: Map<String, Any?>): PairHello? {
            if (o.jBool("ok") != true) return null
            val pub = LanCrypto.unb64(o.jStr("hubPub"))?.takeIf { it.size == 65 } ?: return null
            return PairHello(
                hubId = o.jStr("hubId")?.cap(64) ?: return null,
                pairingId = o.jStr("pairingId")?.cap(64) ?: return null,
                hubPub = pub,
                expiresInMs = o.jLong("expiresIn") ?: 0L,
            )
        }
    }
}

/** آنچه گوشی دربارهٔ خودش در پاکتِ جفت شدن می‌گوید. */
data class PhoneInfo(
    val name: String,
    val platform: String,
    val appVersion: String,
    val wantRole: HubRole,
) {
    fun toJson(): Map<String, Any?> = mapOf(
        "name" to name.cap(NAME_LIMIT), "platform" to platform.cap(20),
        "appVersion" to appVersion.cap(24), "role" to wantRole.wire,
    )

    companion object {
        const val NAME_LIMIT = 60

        fun parse(o: Map<String, Any?>): PhoneInfo? {
            return PhoneInfo(
                name = o.jStr("name")?.trim()?.cap(NAME_LIMIT)?.ifBlank { null } ?: "گوشی",
                platform = o.jStr("platform").orEmpty().trim().cap(20),
                appVersion = o.jStr("appVersion").orEmpty().trim().cap(24),
                wantRole = HubRole.of(o.jStr("role")) ?: return null,
            )
        }
    }
}

enum class PairState { WAIT, APPROVED, REJECTED, EXPIRED }

/**
 * جوابِ «اجازه»، در پاکتِ کلیدِ جفت شدن. فقط [PairState.APPROVED] کلید و
 * نقش دارد.
 */
data class PairGrant(
    val state: PairState,
    val hubId: String,
    val hubName: String,
    val serverTime: Long,
    val deviceId: String? = null,
    val deviceKey: ByteArray? = null,
    val role: HubRole? = null,
    val person: String = "",
    val addresses: List<String> = emptyList(),
    val port: Int = HubProtocol.PORT,
    val reason: String = "",
) {
    // کلید هرگز در گزارش نمی‌نشیند.
    override fun toString(): String =
        "PairGrant(state=$state, hubId=$hubId, deviceId=$deviceId, role=$role, person=$person, key=…)"

    override fun equals(other: Any?): Boolean = other is PairGrant && toString() == other.toString() &&
        (deviceKey == null) == (other.deviceKey == null) &&
        (deviceKey == null || LanCrypto.sameBytes(deviceKey, other.deviceKey!!))
    override fun hashCode(): Int = toString().hashCode()

    fun toJson(): Map<String, Any?> = buildMap {
        put("state", state.name)
        put("hubId", hubId)
        put("hubName", hubName)
        put("serverTime", serverTime)
        put("reason", reason)
        if (state == PairState.APPROVED) {
            put("deviceId", deviceId)
            put("deviceKey", deviceKey?.let { LanCrypto.b64(it) })
            put("role", role?.wire)
            put("person", person)
            put("addresses", addresses)
            put("port", port)
        }
    }

    companion object {
        fun parse(o: Map<String, Any?>): PairGrant? {
            val state = runCatching { PairState.valueOf(o.jStr("state").orEmpty()) }.getOrNull() ?: return null
            val base = PairGrant(
                state = state,
                hubId = o.jStr("hubId")?.cap(64) ?: return null,
                hubName = o.jStr("hubName").orEmpty().cap(80),
                serverTime = o.jLong("serverTime") ?: 0L,
                reason = o.jStr("reason").orEmpty().cap(300),
            )
            if (state != PairState.APPROVED) return base
            val key = LanCrypto.unb64(o.jStr("deviceKey"))?.takeIf { it.size == LanCrypto.KEY_BYTES } ?: return null
            return base.copy(
                deviceId = o.jStr("deviceId")?.cap(64)?.takeIf { it.isNotBlank() } ?: return null,
                deviceKey = key,
                role = HubRole.of(o.jStr("role")) ?: return null,
                person = o.jStr("person").orEmpty().cap(80),
                addresses = o.jStrings("addresses").filter { HostAddress.isPrivate(it) }.take(8),
                port = o.jInt("port")?.takeIf { it in 1..65535 } ?: HubProtocol.PORT,
            )
        }
    }
}

// ------------------------------------------------------------------ همگام‌سازی

/** یک کارِ صفِ ارسال، همان‌طور که روی سیم می‌رود. */
data class OutboxWire(
    /** کلیدِ تکرارنشدنیِ همین کار (UUID) — کامپیوتر با آن تکرار را می‌شناسد. */
    val key: String,
    /** لحظهٔ زدنِ دکمه، با ساعتِ خودِ گوشی. */
    val at: Long,
    val type: String,
    val payload: Map<String, Any?>,
) {
    fun toJson(): Map<String, Any?> = mapOf("key" to key, "at" to at, "type" to type, "p" to payload)

    companion object {
        private val KEY = Regex("^[A-Za-z0-9-]{8,64}$")

        fun parse(o: Map<String, Any?>): OutboxWire? {
            return OutboxWire(
                key = o.jStr("key")?.takeIf { KEY.matches(it) } ?: return null,
                at = o.jLong("at") ?: return null,
                type = o.jStr("type")?.cap(32) ?: return null,
                payload = o.jObj("p") ?: emptyMap(),
            )
        }
    }
}

/** درونِ پاکتِ درخواستِ همگام‌سازی. */
data class SyncAsk(
    /** ساعتِ خامِ گوشی در لحظهٔ فرستادن — کامپیوتر با آن ساعتِ کارها را درست می‌کند. */
    val phoneNow: Long,
    /** چکیدهٔ آخرین تصویری که گوشی دارد؛ اگر همان باشد، دوباره فرستاده نمی‌شود. */
    val cursor: String,
    val outbox: List<OutboxWire>,
    val appVersion: String,
    val platform: String,
) {
    fun toJson(): Map<String, Any?> = mapOf(
        "v" to 2, "phoneNow" to phoneNow, "cursor" to cursor,
        "outbox" to outbox.map { it.toJson() }, "app" to appVersion, "platform" to platform,
    )

    companion object {
        fun parse(o: Map<String, Any?>): SyncAsk? {
            val items = o.jList("outbox") ?: emptyList<Any?>()
            if (items.size > HubProtocol.MAX_OUTBOX_PER_SYNC) return null
            val parsed = items.map { LanJson.asObject(it)?.let { m -> OutboxWire.parse(m) } ?: return null }
            return SyncAsk(
                phoneNow = o.jLong("phoneNow") ?: return null,
                cursor = o.jStr("cursor").orEmpty().cap(64),
                outbox = parsed,
                appVersion = o.jStr("app").orEmpty().cap(24),
                platform = o.jStr("platform").orEmpty().cap(20),
            )
        }
    }
}

/** پاسخِ کامپیوتر به یک کارِ صف. [ref] شمارهٔ درخواست در صندوق، اگر نشست. */
data class Ack(val key: String, val status: AckStatus, val reason: String = "", val ref: Long = 0L) {
    fun toJson(): Map<String, Any?> = mapOf("key" to key, "status" to status.name, "reason" to reason, "ref" to ref)

    companion object {
        fun parse(o: Map<String, Any?>): Ack? {
            return Ack(
                key = o.jStr("key")?.cap(64) ?: return null,
                status = runCatching { AckStatus.valueOf(o.jStr("status").orEmpty()) }.getOrNull() ?: return null,
                reason = o.jStr("reason").orEmpty().cap(300),
                ref = o.jLong("ref") ?: 0L,
            )
        }
    }
}

/** درونِ پاکتِ جواب. */
data class SyncReply(
    val serverTime: Long,
    val acks: List<Ack>,
    val cursor: String,
    /** `null` یعنی «همان که داری» ([SyncAsk.cursor] هنوز درست است). */
    val snapshot: HubSnapshot?,
    /** لایسنسِ کامپیوتر — گوشی پیروِ همان است. */
    val licenceState: String?,
    val licenceWritable: Boolean,
    val role: HubRole,
    val person: String,
    val deviceName: String,
    val hubId: String,
    val hubName: String,
    val hubVersion: String,
    val addresses: List<String>,
) {
    fun toJson(): Map<String, Any?> = buildMap {
        put("ok", true)
        put("serverTime", serverTime)
        put("acks", acks.map { it.toJson() })
        put("cursor", cursor)
        put("unchanged", snapshot == null)
        snapshot?.let { put("snapshot", it.toJson()) }
        put("licence", licenceState?.let { mapOf("state" to it, "writable" to licenceWritable) })
        put("device", mapOf("role" to role.wire, "person" to person, "name" to deviceName))
        put("hub", mapOf("id" to hubId, "name" to hubName, "version" to hubVersion, "addresses" to addresses))
    }

    companion object {
        fun parse(o: Map<String, Any?>): SyncReply? {
            if (o.jBool("ok") != true) return null
            val device = o.jObj("device") ?: return null
            val hub = o.jObj("hub") ?: return null
            val role = HubRole.of(device.jStr("role")) ?: return null
            val snapshot = if (o.jBool("unchanged") == true) null
            else HubSnapshot.parse(o.jObj("snapshot") ?: return null, role) ?: return null
            val lic = o.jObj("licence")
            return SyncReply(
                serverTime = o.jLong("serverTime") ?: return null,
                acks = o.jObjects("acks").mapNotNull { Ack.parse(it) },
                cursor = o.jStr("cursor").orEmpty().cap(64),
                snapshot = snapshot,
                licenceState = lic?.jStr("state")?.cap(24),
                licenceWritable = lic?.jBool("writable") ?: true,
                role = role,
                person = device.jStr("person").orEmpty().cap(80),
                deviceName = device.jStr("name").orEmpty().cap(PhoneInfo.NAME_LIMIT),
                hubId = hub.jStr("id")?.cap(64) ?: return null,
                hubName = hub.jStr("name").orEmpty().cap(80),
                hubVersion = hub.jStr("version").orEmpty().cap(24),
                addresses = hub.jStrings("addresses").filter { HostAddress.isPrivate(it) }.take(8),
            )
        }
    }
}

// ------------------------------------------------------------------ تصویرِ هر نقش

/** یک کارِ دوخت زیرِ دستِ کارگر. */
data class WorkLine(
    val id: Long,
    val orderCode: String,
    val designCode: String,
    val qty: Int,
    val unitWage: Long,
    val createdAt: Long,
) {
    fun toJson(): Map<String, Any?> = mapOf(
        "id" to id, "order" to orderCode, "design" to designCode, "qty" to qty, "wage" to unitWage, "at" to createdAt,
    )

    companion object {
        fun parse(o: Map<String, Any?>): WorkLine? {
            return WorkLine(
                id = o.jLong("id") ?: return null,
                orderCode = o.jStr("order").orEmpty().cap(40),
                designCode = o.jStr("design").orEmpty().cap(40),
                qty = o.jInt("qty") ?: 0,
                unitWage = o.jLong("wage") ?: 0L,
                createdAt = o.jLong("at") ?: 0L,
            )
        }
    }
}

/** یک درخواست در صندوق — برای کارگر فقط مالِ خودش، برای صاحب‌کار همهٔ منتظرها. */
data class RequestLine(
    val id: Long,
    val clientKey: String,
    val type: String,
    val summary: String,
    val worker: String,
    val device: String,
    val status: String,
    val note: String,
    /** لحظه‌ای که کامپیوتر آن را گرفت. */
    val receivedAt: Long,
    /** لحظهٔ زدنِ دکمه (با ساعتِ کامپیوتر)، اگر از قراردادِ ۲ آمده. */
    val pressedAt: Long?,
    /** ساعتِ گوشی معتبر نبود؛ کارفرما باید یکی از دو ساعت را انتخاب کند. */
    val clockSuspect: Boolean,
) {
    fun toJson(): Map<String, Any?> = mapOf(
        "id" to id, "key" to clientKey, "type" to type, "summary" to summary, "worker" to worker,
        "device" to device, "status" to status, "note" to note, "at" to receivedAt,
        "pressedAt" to pressedAt, "suspect" to clockSuspect,
    )

    companion object {
        fun parse(o: Map<String, Any?>): RequestLine? {
            return RequestLine(
                id = o.jLong("id") ?: return null,
                clientKey = o.jStr("key").orEmpty().cap(64),
                type = o.jStr("type").orEmpty().cap(32),
                summary = o.jStr("summary").orEmpty().cap(200),
                worker = o.jStr("worker").orEmpty().cap(80),
                device = o.jStr("device").orEmpty().cap(PhoneInfo.NAME_LIMIT),
                status = o.jStr("status").orEmpty().cap(16),
                note = o.jStr("note").orEmpty().cap(300),
                receivedAt = o.jLong("at") ?: 0L,
                pressedAt = o.jLong("pressedAt"),
                clockSuspect = o.jBool("suspect") == true,
            )
        }
    }
}

data class WorkerView(
    val person: String,
    val inHand: List<WorkLine>,
    val doneTotal: Int,
    val requests: List<RequestLine>,
    /** از کِی داخل است؛ `null` یعنی بیرون. */
    val insideSince: Long?,
) {
    fun toJson(): Map<String, Any?> = mapOf(
        "person" to person, "inHand" to inHand.map { it.toJson() }, "doneTotal" to doneTotal,
        "requests" to requests.map { it.toJson() }, "insideSince" to insideSince,
    )

    companion object {
        fun parse(o: Map<String, Any?>): WorkerView = WorkerView(
            person = o.jStr("person").orEmpty().cap(80),
            inHand = o.jObjects("inHand").take(500).mapNotNull { WorkLine.parse(it) },
            doneTotal = o.jInt("doneTotal") ?: 0,
            requests = o.jObjects("requests").take(200).mapNotNull { RequestLine.parse(it) },
            insideSince = o.jLong("insideSince"),
        )
    }
}

/** یک سفارش برای گوشیِ صاحب‌کار — نه پول، فقط کد، مشتری، مرحله و موعد. */
data class OrderLine(
    val code: String,
    val customer: String,
    val stage: String,
    val dueDate: Long,
    val createdAt: Long,
) {
    fun toJson(): Map<String, Any?> = mapOf(
        "code" to code, "customer" to customer, "stage" to stage, "due" to dueDate, "at" to createdAt,
    )

    companion object {
        fun parse(o: Map<String, Any?>): OrderLine? {
            return OrderLine(
                code = o.jStr("code")?.cap(40) ?: return null,
                customer = o.jStr("customer").orEmpty().cap(80),
                stage = o.jStr("stage").orEmpty().cap(40),
                dueDate = o.jLong("due") ?: 0L,
                createdAt = o.jLong("at") ?: 0L,
            )
        }
    }
}

/** یک نفر در فهرستِ امروزِ پیگیریِ طلب. */
data class DebtLine(
    val customer: String,
    val phone: String,
    val amount: Long,
    val reason: String,
    val contactedToday: Boolean,
) {
    fun toJson(): Map<String, Any?> = mapOf(
        "customer" to customer, "phone" to phone, "amount" to amount, "reason" to reason, "done" to contactedToday,
    )

    companion object {
        fun parse(o: Map<String, Any?>): DebtLine? {
            return DebtLine(
                customer = o.jStr("customer")?.cap(80) ?: return null,
                phone = o.jStr("phone").orEmpty().cap(24),
                amount = o.jLong("amount") ?: 0L,
                reason = o.jStr("reason").orEmpty().cap(60),
                contactedToday = o.jBool("done") == true,
            )
        }
    }
}

data class OwnerView(
    val pending: List<RequestLine>,
    val dueToday: List<OrderLine>,
    val overdue: List<OrderLine>,
    val createdToday: List<OrderLine>,
    val openOrders: List<OrderLine>,
    val openCount: Int,
    val readyCount: Int,
    val insideNow: List<String>,
    val debtToday: List<DebtLine>,
) {
    fun toJson(): Map<String, Any?> = mapOf(
        "pending" to pending.map { it.toJson() },
        "dueToday" to dueToday.map { it.toJson() },
        "overdue" to overdue.map { it.toJson() },
        "createdToday" to createdToday.map { it.toJson() },
        "open" to openOrders.map { it.toJson() },
        "openCount" to openCount,
        "readyCount" to readyCount,
        "inside" to insideNow,
        "debtToday" to debtToday.map { it.toJson() },
    )

    companion object {
        private fun orders(o: Map<String, Any?>, k: String) = o.jObjects(k).take(300).mapNotNull { OrderLine.parse(it) }

        fun parse(o: Map<String, Any?>): OwnerView = OwnerView(
            pending = o.jObjects("pending").take(300).mapNotNull { RequestLine.parse(it) },
            dueToday = orders(o, "dueToday"),
            overdue = orders(o, "overdue"),
            createdToday = orders(o, "createdToday"),
            openOrders = orders(o, "open"),
            openCount = o.jInt("openCount") ?: 0,
            readyCount = o.jInt("readyCount") ?: 0,
            insideNow = o.jStrings("inside").take(200).map { it.cap(80) },
            debtToday = o.jObjects("debtToday").take(200).mapNotNull { DebtLine.parse(it) },
        )
    }
}

/**
 * تصویرِ یک نقش — **فقط همان که نقش حقِ دیدنش را دارد.** کارگر کارِ خودش و
 * کارمزدِ خودش را می‌بیند؛ تابلوی دیوار نه پول دارد نه نامِ مشتری؛ صاحب‌کار
 * فهرستِ سفارش‌ها و صندوقِ درخواست‌ها را.
 */
data class HubSnapshot(
    val role: HubRole,
    val worker: WorkerView? = null,
    val owner: OwnerView? = null,
    val board: List<BoardRow> = emptyList(),
) {
    fun toJson(): Map<String, Any?> = buildMap {
        put("role", role.wire)
        worker?.let { put("worker", it.toJson()) }
        owner?.let { put("owner", it.toJson()) }
        put("board", board.map {
            mapOf("tailor" to it.tailor, "order" to it.orderCode, "design" to it.design, "qty" to it.qty,
                "days" to it.days, "dueIn" to it.dueIn)
        })
    }

    companion object {
        /** نقشِ تصویر باید همان نقشِ دستگاه باشد؛ تصویرِ نقشِ دیگر پذیرفته نمی‌شود. */
        fun parse(o: Map<String, Any?>, expected: HubRole): HubSnapshot? {
            if (HubRole.of(o.jStr("role")) != expected) return null
            return HubSnapshot(
                role = expected,
                worker = if (expected == HubRole.WORKER) o.jObj("worker")?.let { WorkerView.parse(it) } else null,
                owner = if (expected == HubRole.OWNER) o.jObj("owner")?.let { OwnerView.parse(it) } else null,
                board = o.jObjects("board").take(500).map {
                    BoardRow(
                        tailor = it.jStr("tailor").orEmpty().cap(80),
                        orderCode = it.jStr("order").orEmpty().cap(40),
                        design = it.jStr("design").orEmpty().cap(80),
                        qty = it.jInt("qty") ?: 0,
                        days = it.jInt("days") ?: 0,
                        dueIn = it.jInt("dueIn"),
                    )
                },
            )
        }
    }
}

package com.afghanjama.lan

import com.afghanjama.AppInfo
import com.afghanjama.data.Db
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.entities.isRepeatOf
import com.afghanjama.data.entities.ownsAssignment
import com.afghanjama.data.entities.requestWorkerLabel
import com.afghanjama.licence.LicenceGate
import com.afghanjama.licence.LicenceText
import com.afghanjama.util.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * سرورِ گوشیِ اصلی روی وای‌فای کارگاه.
 *
 * **قانونِ یکتای این فایل:** هرچه از شبکه می‌آید فقط می‌تواند یک
 * «درخواست» در جدولِ `sync_requests` بنشاند. هیچ مسیری اینجا سفارش،
 * فروش، پرداخت یا موجودی را دست نمی‌زند. تصمیمِ پول فقط روی گوشیِ
 * کارفرما و با دستِ خودش گرفته می‌شود — این همان چیزی است که کلِ
 * مسئلهٔ تضادِ دو نویسنده را حذف می‌کند.
 */
/**
 * **دیتابیس تزریق می‌شود، ساخته نمی‌شود.** تا دیروز هر درخواستِ شبکه
 * یک `buildAppDatabase(context)` تازه صدا می‌زد — هم اندروید را لازم
 * می‌کرد، هم برای هر «پینگ» یک اتصالِ تازه باز می‌کرد.
 *
 * با گرفتنِ `Db`، این سرور دیگر نمی‌داند روی چه چیزی اجرا می‌شود: گوشیِ
 * کارفرما یا پی‌سیِ دفتر. همان چیزی که فاز ۶ می‌خواست.
 */
class LanServer(
    private val db: Db,
    /**
     * نشانی‌ای که سرور روی آن گوش می‌دهد. `null` یعنی همهٔ کارت‌های شبکه
     * — همان چیزی که دستگاهِ اصلی لازم دارد. آزمونِ دودی فقط loopback را
     * می‌گیرد تا روی ماشینِ CI درگاهی رو به شبکه باز نکند.
     */
    private val bindAddress: InetAddress? = null,
    /** درگاه؛ برنامه همیشه [Lan.PORT]. آزمون درگاهِ خودش را می‌دهد. */
    private val port: Int = Lan.PORT,
    /**
     * سقفِ رمزِ نادرست برای هر نشانی ([LanGuard]). با خودِ سرور می‌ماند،
     * پس خاموش و روشن کردن بستنِ یک نشانی را پاک نمی‌کند؛ فقط «رمز تازه»
     * ([changeCode]). آزمونِ دودی ساعتِ خودش را به آن می‌دهد.
     */
    private val guard: LanGuard = LanGuard(),
) {

    private var socket: ServerSocket? = null
    private var scope: CoroutineScope? = null

    /**
     * فقط یک درخواست در هر لحظه در صندوق می‌نشیند. بی این، دو تلاشِ
     * هم‌زمانِ یک کارگر هر دو «تکراری نیست» می‌دیدند و هر دو ثبت می‌شدند.
     */
    private val inbox = Mutex()

    @Volatile var running: Boolean = false
        private set

    /** درگاهی که واقعاً گرفته شد؛ صفر وقتی خاموش است. */
    @Volatile var boundPort: Int = 0
        private set

    /** رمزِ اتصال؛ روی گوشیِ اصلی نمایش داده می‌شود و کارگر واردش می‌کند. */
    @Volatile private var code: String = ""

    fun start(pairCode: String): Boolean {
        if (running) return true
        code = pairCode.trim()
        if (code.isBlank()) return false
        var opened: ServerSocket? = null
        return runCatching {
            val s = ServerSocket().also { opened = it }
            // **`reuseAddress` پیش از `bind`.** تا دیروز بعد از ساختنِ
            // `ServerSocket(port)` گذاشته می‌شد — یعنی بعد از bind، جایی که
            // دیگر هیچ اثری ندارد. پس روشن کردنِ دوبارهٔ اشتراک چند ثانیه
            // بعد از خاموش کردنش (وقتی اتصال‌های قبلی هنوز در TIME_WAIT
            // بودند) با «پورت باز نشد» رد می‌شد.
            //
            // روی ویندوز گذاشته نمی‌شود: آنجا همین پرچم یعنی برنامهٔ دیگری
            // هم می‌تواند همین درگاه را بگیرد، و ویندوز TIME_WAIT را مانعِ
            // گوش دادن نمی‌داند.
            if (!onWindows) s.reuseAddress = true
            s.bind(InetSocketAddress(bindAddress, port))
            socket = s
            boundPort = s.localPort
            val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = sc
            running = true
            sc.launch { acceptLoop(s, sc) }
            true
        }.getOrElse {
            runCatching { opened?.close() }
            running = false
            boundPort = 0
            false
        }
    }

    /**
     * رمزِ تازه، بی بستن و باز کردنِ درگاه. هر چه با رمزِ قبلی حدس زده شده
     * بود دیگر به چیزی نزدیک نمی‌کند، پس نشانی‌های بسته هم باز می‌شوند —
     * کارگری که با رمزِ کهنه بسته شده بود با رمزِ تازه فوراً وصل می‌شود.
     */
    fun changeCode(newCode: String): Boolean {
        val c = newCode.trim()
        if (c.isBlank()) return false
        code = c
        guard.reset()
        return true
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        boundPort = 0
        scope?.cancel()
        scope = null
    }

    private suspend fun acceptLoop(server: ServerSocket, sc: CoroutineScope) {
        while (sc.isActive && running) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            sc.launch { runCatching { handle(client) }; runCatching { client.close() } }
        }
    }

    private suspend fun handle(client: Socket) {
        client.soTimeout = 10_000
        // خواندنِ بایتی و UTF-8ِ صریح — داستانش در [LanWire].
        val request = when (val r = LanWire.readRequest(BufferedInputStream(client.getInputStream()))) {
            is LanWire.Read.Ok -> r.request
            is LanWire.Read.Bad -> {
                respond(client, r.status, JSONObject().apply {
                    put("ok", false); put("error", r.message)
                })
                return
            }
            LanWire.Read.Closed -> return
        }
        val method = request.method
        val target = request.target
        val givenCode = request.header(Lan.HEADER_CODE).orEmpty()
        val body = request.body

        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "")

        // /ping بدونِ رمز جواب می‌دهد تا کارگر بفهمد نشانی درست است؛
        // ولی هیچ داده‌ای در آن نیست.
        if (path == Lan.PATH_PING) {
            respond(client, 200, JSONObject().apply {
                put("ok", true)
                put("app", AppInfo.NAME_LATIN)
                put("protocol", Lan.PROTOCOL)
                licence()?.let { put("licence", it) }
            })
            return
        }

        // سقفِ رمزِ نادرست برای هر نشانی (داستانش در [LanGuard]). نشانیِ
        // بسته حتی با رمزِ درست هم پذیرفته نمی‌شود؛ وگرنه همین که بسته
        // نمی‌ماند می‌گفت کدام حدس درست بوده.
        val peer = client.inetAddress?.hostAddress.orEmpty()
        val wait = guard.blockedFor(peer)
        if (wait > 0) {
            respond(client, 429, JSONObject().apply {
                put("ok", false); put("error", LanGuard.blockedMessage(wait))
            })
            return
        }

        // مقایسه با زمانِ ثابت ([LanGuard.sameCode]): `!=` در اولین حرفِ
        // ناجور برمی‌گشت و زمانِ جواب نشان می‌داد چند رقمِ اولِ رمز درست بوده.
        if (!LanGuard.sameCode(givenCode, code)) {
            guard.failed(peer)
            respond(client, 401, JSONObject().apply {
                put("ok", false); put("error", "رمزِ اتصال درست نیست.")
            })
            return
        }
        guard.succeeded(peer)

        when {
            method == "GET" && path == Lan.PATH_MY_WORK -> serveMyWork(client, param(query, "worker"))
            method == "GET" && path == Lan.PATH_BOARD -> serveBoard(client)
            method == "POST" && path == Lan.PATH_REQUEST -> acceptRequest(client, body)
            else -> respond(client, 404, JSONObject().apply {
                put("ok", false); put("error", "مسیر پیدا نشد.")
            })
        }
    }

    private fun param(query: String, key: String): String =
        query.split("&").firstNotNullOfOrNull {
            val i = it.indexOf('=')
            if (i > 0 && it.substring(0, i) == key)
                runCatching { URLDecoder.decode(it.substring(i + 1), "UTF-8") }.getOrNull()
            else null
        }.orEmpty()

    /** فقط خواندن: کارِ زیرِ دستِ همین خیاط و کارمزدِ تسویه‌نشده‌اش. */
    private suspend fun serveMyWork(client: Socket, worker: String) {
        val name = worker.trim()
        if (name.isBlank()) {
            respond(client, 400, JSONObject().apply {
                put("ok", false); put("error", "نامِ کارگر خالی است.")
            })
            return
        }
        val mine = db.sewingAssignmentDao().observeAll().first()
            .filter { it.tailorLabel.trim() == name }

        val items = JSONArray()
        mine.filter { it.status != "DONE" }.forEach { a ->
            items.put(JSONObject().apply {
                put("id", a.id)
                put("orderCode", a.orderCode)
                put("qty", a.qty)
                put("unitWage", a.unitWage)
                put("createdAt", a.createdAt)
            })
        }
        val doneCount = mine.count { it.status == "DONE" }
        respond(client, 200, JSONObject().apply {
            put("ok", true)
            licence()?.let { put("licence", it) }
            put("worker", name)
            put("inHand", items)
            put("doneTotal", doneCount)
        })
    }

    /**
     * تابلوی «در حال دوخت» برای صفحهٔ دیوارِ کارگاه — فقط خواندن.
     *
     * نه پول در آن هست نه نامِ مشتری؛ چیزی که روی دیوار می‌ماند نباید
     * حسابِ کسی را لو بدهد.
     */
    private suspend fun serveBoard(client: Socket) {
        val assignments = db.sewingAssignmentDao().observeInProgress().first()
        val orders = db.orderDao().observeAll().first()
        val byCode = orders.associateBy { it.orderCode }
        val now = System.currentTimeMillis()

        val arr = JSONArray()
        assignments.forEach { a ->
            val o = byCode[a.orderCode]
            arr.put(JSONObject().apply {
                put("tailor", a.tailorLabel.trim())
                put("orderCode", a.orderCode)
                put("design", o?.designTitle.orEmpty())
                put("qty", a.qty)
                put("days", ((now - a.createdAt) / 86_400_000L).toInt().coerceAtLeast(0))
                val due = o?.dueDate ?: 0L
                if (due > 0) put("dueIn", ((due - now) / 86_400_000L).toInt())
            })
        }
        respond(client, 200, JSONObject().apply {
            put("ok", true); put("rows", arr); put("at", now)
            licence()?.let { put("licence", it) }
        })
    }

    /**
     * وضعیتِ لایسنسِ همین دستگاهِ اصلی، برای گوشیِ کارگر.
     *
     * «یک لایسنس برای کلِ کارگاه»: کارگر لایسنسِ خودش را ندارد و پیرو
     * همین است. با هر جواب فرستاده می‌شود تا کارگر بی پرسشِ جدا بداند.
     * `null` یعنی هنوز خوانده نشده — آن‌وقت چیزی نمی‌گوییم.
     */
    private fun licence(): JSONObject? = LicenceGate.current()?.let { s ->
        JSONObject().apply {
            put("state", s.state.name)
            put("writable", s.canWrite)
        }
    }

    /** تنها راهِ نوشتن از شبکه — و فقط در صندوقِ درخواست‌ها. */
    private suspend fun acceptRequest(client: Socket, body: String) {
        // دستگاهِ اصلیِ فقط‌خواندنی درخواستِ تازه هم نمی‌نشاند؛ وگرنه
        // کارگر از راهِ همین در چیزی را ثبت می‌کرد که خودِ کارفرما
        // نمی‌تواند.
        if (!LicenceGate.canWrite()) {
            respond(client, 403, JSONObject().apply {
                put("ok", false); put("error", LicenceText.LAN_REFUSAL)
                licence()?.let { put("licence", it) }
            })
            return
        }
        val json = runCatching { JSONObject(body) }.getOrNull()
        if (json == null) {
            respond(client, 400, JSONObject().apply {
                put("ok", false); put("error", "بدنهٔ درخواست خوانده نشد.")
            })
            return
        }
        val type = json.optString("type").trim()
        if (type !in ALLOWED_TYPES) {
            respond(client, 400, JSONObject().apply {
                put("ok", false); put("error", "نوعِ درخواست پذیرفته نیست.")
            })
            return
        }
        val worker = requestWorkerLabel(json.optString("worker"))
        val refId = json.optLong("refId", 0L)
        val amount = json.optInt("amount", 0)
        val note = json.optString("note").take(300)

        // «تمام شد» فقط برای کارِ خودِ همین کارگر. همین قاعده سرِ تأیید هم
        // سنجیده می‌شود؛ اینجا فقط زودتر به خودِ کارگر گفته می‌شود.
        var assignmentQty = 0
        if (type == "SEWING_DONE") {
            val a = db.sewingAssignmentDao().getById(refId)
            assignmentQty = a?.qty ?: 0
            val refusal = when {
                a == null -> "این کار پیدا نشد؛ «تازه‌سازی کارها» را بزنید."
                !ownsAssignment(a, worker) -> "این کار به نامِ شما نیست."
                a.status == "DONE" -> "این کار قبلاً تحویل شده."
                else -> null
            }
            if (refusal != null) {
                respond(client, 409, JSONObject().apply {
                    put("ok", false); put("error", refusal)
                })
                return
            }
        }

        val (id, repeated) = inbox.withLock {
            val last = db.syncRequestDao().observeAll().first().firstOrNull { it.worker == worker }
            if (isRepeatOf(last, worker, type, refId, amount, note, nowMillis(), assignmentQty)) {
                // همان فشارِ دکمه، دوباره رسیده — همان ردیف را برمی‌گردانیم.
                last!!.id to true
            } else {
                db.syncRequestDao().insert(
                    SyncRequest(
                        deviceName = json.optString("device").take(60).ifBlank { "گوشی کارگر" },
                        worker = worker,
                        type = type,
                        summary = json.optString("summary").take(200),
                        refId = refId,
                        amount = amount,
                        note = note
                    )
                ) to false
            }
        }
        respond(client, 200, JSONObject().apply {
            put("ok", true); put("id", id)
            put(
                "message",
                if (repeated) "این درخواست پیش‌تر رسیده بود و منتظرِ تأییدِ کارفرماست."
                else "درخواست ثبت شد و منتظرِ تأییدِ کارفرماست."
            )
        })
    }

    private fun respond(client: Socket, status: Int, json: JSONObject) {
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 $status ${if (status == 200) "OK" else "ERROR"}\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        runCatching {
            client.getOutputStream().apply {
                write(head.toByteArray(Charsets.UTF_8))
                write(payload)
                flush()
            }
        }
    }

    companion object {
        /**
         * فهرستِ بسته و صریحِ کارهایی که از شبکه پذیرفته می‌شوند. هر
         * چیزی بیرونِ این فهرست رد می‌شود — تا اضافه‌شدنِ یک قابلیتِ
         * جدید ناخواسته دری به دفتر باز نکند.
         */
        val ALLOWED_TYPES = setOf(
            "SEWING_DONE", "ATTENDANCE_IN", "ATTENDANCE_OUT", "NOTE"
        )

        private val onWindows: Boolean =
            System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    }
}

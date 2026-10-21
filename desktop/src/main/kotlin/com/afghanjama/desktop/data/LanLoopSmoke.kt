package com.afghanjama.desktop.data

import com.afghanjama.data.Db
import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.HostAddress
import com.afghanjama.lan.HubOff
import com.afghanjama.lan.LanClient
import com.afghanjama.lan.LanHost
import com.afghanjama.lan.LanHub
import com.afghanjama.lan.LanNetwork
import com.afghanjama.lan.LanGuard
import com.afghanjama.lan.LanResult
import com.afghanjama.lan.LanServer
import com.afghanjama.lan.LanWire
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LanPrefs
import com.afghanjama.util.randomUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/*
 * سرور و مشتریِ اشتراکِ کارگاه، روی همین ماشین، سرِ یک دفترِ موقت.
 *
 * `lanSmoke` فقط قراردادها را می‌سنجد و سرور را روشن نمی‌کند. این یکی
 * روشنش می‌کند — **فقط روی loopback**، تا روی ماشینِ CI درگاهی رو به شبکه
 * باز نشود — و با همان `LanClient`ی که روی گوشیِ کارگر است درخواست
 * می‌فرستد.
 *
 * سه اشکالی که هیچ‌کدام سرِ ساخت دیده نمی‌شدند و این آزمون نگهشان
 * می‌دارد:
 *
 * ۱. بدنهٔ دری حرف به حرف خوانده می‌شد ولی اندازه‌اش بایت بود؛ درخواست
 *    هشت ثانیه می‌ماند، گوشی «وصل نشد» می‌گفت، و بعد ثبت می‌شد.
 * ۲. کدگذاریِ پیش‌فرضِ سیستم: این آزمون عمداً با `-Dfile.encoding=Cp1252`
 *    اجرا می‌شود. برنامهٔ بسته‌بندی‌شده روی ویندوز کدگذاریِ ANSIِ خودِ
 *    ویندوز را می‌گیرد: cp1252 روی ویندوزِ انگلیسی، cp1256 روی ویندوزی
 *    که زبانش فارسی یا دری است — هیچ‌کدام UTF-8 نیست، و دری در هر دو
 *    درهم می‌شد. باقیِ آزمون‌های دودی UTF-8 می‌گذارند و این دام را هرگز
 *    نمی‌دیدند.
 * ۳. `reuseAddress` بعد از bind گذاشته می‌شد و اثری نداشت؛ پس سرور دو
 *    بار پشتِ سرِ هم روی **همان** درگاه روشن می‌شود.
 * ۴. رمزِ شش‌رقمی سقفِ حدس نداشت؛ حالا ده رمزِ نادرست در دقیقه، بعد پنج
 *    دقیقه بسته — حتی با رمزِ درست ([LanGuard]).
 * ۵. سرور فقط از صفحه روشن می‌شد. حالا [LanHub] سرِ باز شدنِ برنامه
 *    روشنش می‌کند — پس از خوانده شدنِ لایسنس، و فقط روی شبکهٔ کارگاه.
 * ۶. کارفرما روی کامپیوتر نمی‌دید گوشیِ کارگر اصلاً رسیده یا نه؛ حالا هاب
 *    گوشی‌هایی را که رسیده‌اند می‌شمارد ([LanPeers]) — رمزِ نادرست نه.
 *
 * دفتر یک فایلِ موقت است (`openDatabase(tempFile)`)، نه دفترِ کارگاه.
 */

private var loopFailed = 0

private fun loopCheck(name: String, body: () -> Unit) {
    try {
        body()
        println("OK    $name")
    } catch (t: Throwable) {
        loopFailed++
        println("FAIL  $name")
        println("        ${t::class.java.name}: ${t.message}")
    }
}

private fun loopNeed(cond: Boolean, msg: String) {
    if (!cond) throw IllegalStateException(msg)
}

/**
 * دستگاهی ساختگی برای [LanHub]: شبکه‌اش دستِ آزمون است تا «رفتن به
 * چایخانه» و «برگشتن به کارگاه» بی جابه‌جا کردنِ ماشین آزموده شود.
 */
private class FakeHost(private val db: Db, @Volatile var nets: Map<String, LanNetwork>) : LanHost {
    override val deviceName: String = "میزِ آزمون"
    /** چند بار شبکه دیده شد — روی کامپیوترِ واقعی هر بار یعنی اجرای `route` و `arp`. */
    @Volatile var looked = 0
    override fun localIp(): String? = nets.keys.firstOrNull()
    override fun networks(): Map<String, LanNetwork> = nets.also { looked++ }
    override fun ledger(): Db = db
}

/** تا [ms] میلی‌ثانیه صبر تا [cond] راست شود. */
private fun waitFor(ms: Long, cond: () -> Boolean): Boolean {
    val until = System.currentTimeMillis() + ms
    while (System.currentTimeMillis() < until) {
        if (cond()) return true
        Thread.sleep(20)
    }
    return cond()
}

/**
 * آیا کسی روی این درگاهِ loopback گوش می‌دهد؟ با وصل شدن سنجیده می‌شود،
 * نه با گرفتنِ درگاه: گرفتن روی مک و لینوکس پس از بستنِ سرور گاهی به
 * خاطرِ TIME_WAIT شکست می‌خورد، و روی ویندوز با `reuseAddress` همیشه موفق است.
 */
private fun listening(port: Int): Boolean = runCatching {
    Socket().use { it.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500) }
}.isSuccess

/** یک درگاهِ آزاد روی loopback — بعد بسته می‌شود تا سرور همان را بگیرد. */
private fun freeLoopbackPort(): Int =
    ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

/** فرستادنِ بایت‌های خام و خواندنِ خطِ اولِ جواب. */
private fun rawExchange(port: Int, bytes: ByteArray, closeOutput: Boolean): String =
    Socket(InetAddress.getLoopbackAddress(), port).use { s ->
        s.soTimeout = 5_000
        s.getOutputStream().apply { write(bytes); flush() }
        if (closeOutput) s.shutdownOutput()
        s.getInputStream().readBytes().toString(Charsets.UTF_8).lineSequence().first()
    }

fun main() {
    // گزارش با UTF-8، جدا از کدگذاریِ پیش‌فرض. JDK ۱۷ (همانی که CI و
    // برنامهٔ بسته‌بندی‌شده دارند) `-Dstdout.encoding` را نمی‌شناسد — آن
    // از JDK ۱۹ است — و `System.out` با همان Cp1252ِ عمدی هر حرفِ دری را
    // «?» چاپ می‌کرد؛ پیامِ شکستِ دری در لاگِ CI خوانده نمی‌شد.
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
    System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8"))
    println("lan loop smoke - KhayatYar desktop")
    println("=".repeat(52))
    println("default charset: ${Charset.defaultCharset()}")

    val dir = Files.createTempDirectory("khayatyar-lan").toFile()
    val db = openDatabase(File(dir, "lan.db"))
    val repo = Repo(db)
    val code = "482913"
    val port = freeLoopbackPort()
    // ساعتِ نگهبانِ رمز دستِ آزمون است تا «پنج دقیقه بعد» منتظرِ پنج دقیقه نماند.
    val guardClock = AtomicLong(System.currentTimeMillis())
    val server = LanServer(db, InetAddress.getLoopbackAddress(), port, LanGuard(clock = { guardClock.get() }))
    val client = LanClient("127.0.0.1", code, port, allowLoopback = true)
    val ahmad = "[T1] احمد"

    suspend fun requests() = db.syncRequestDao().observeAll().first()

    try {
        loopCheck("the default charset is not UTF-8, so the Windows trap is live") {
            loopNeed(
                Charset.defaultCharset() != Charsets.UTF_8,
                "run with -Dfile.encoding=Cp1252; under UTF-8 this smoke cannot see the charset bug"
            )
        }

        loopCheck("the server binds the fixed loopback port") {
            loopNeed(server.start(code), "start() returned false")
            loopNeed(server.boundPort == port, "bound ${server.boundPort}, wanted $port")
        }

        loopCheck("without the test flag the client refuses loopback before any network call") {
            val r = runBlocking { LanClient("127.0.0.1", code, port).ping() }
            loopNeed(r is LanResult.Err && r.message == HostAddress.NOT_WORKSHOP, "got $r")
        }

        loopCheck("ping answers") {
            val r = runBlocking { client.ping() }
            loopNeed(r is LanResult.Ok, "got $r")
        }

        loopCheck("a wrong code is refused") {
            val r = runBlocking { LanClient("127.0.0.1", "000000", port, allowLoopback = true).board() }
            loopNeed(r is LanResult.Err && r.message.contains("رمز"), "got $r")
        }

        loopCheck("ten wrong codes a minute, then this phone is shut out for five minutes - even with the right code") {
            val wrong = LanClient("127.0.0.1", "000000", port, allowLoopback = true)
            // رمزِ درست شمارِ نادرستِ بندِ قبلی را پاک می‌کند؛ از صفر.
            loopNeed(runBlocking { client.board() } is LanResult.Ok, "the right code was refused before any guessing")
            repeat(10) { i ->
                val r = runBlocking { wrong.board() }
                loopNeed(r is LanResult.Err && r.message == "رمزِ اتصال درست نیست.", "wrong code ${i + 1}: $r")
            }
            val head = "GET /board HTTP/1.1\r\nX-Workshop-Code: 000000\r\n\r\n"
            val status = rawExchange(port, head.toByteArray(Charsets.ISO_8859_1), closeOutput = false)
            loopNeed(status.startsWith("HTTP/1.1 429"), "the 11th wrong code got «$status»")
            val right = runBlocking { client.board() }
            loopNeed(
                right is LanResult.Err && right.message.contains("۵ دقیقه"),
                "the right code got through a block (a block that lifts for the right code tells the guesser it was right): $right"
            )
            loopNeed(runBlocking { client.ping() } is LanResult.Ok, "ping carries no data and must still answer")
            guardClock.addAndGet(LanGuard.BLOCK_MS)
            loopNeed(runBlocking { client.board() } is LanResult.Ok, "still shut out after five minutes")
        }

        loopCheck("a new code lifts every block at once") {
            val wrong = LanClient("127.0.0.1", "111111", port, allowLoopback = true)
            repeat(10) { runBlocking { wrong.board() } }
            loopNeed(runBlocking { client.board() } is LanResult.Err, "ten wrong codes did not shut this phone out")
            loopNeed(server.changeCode(code), "changeCode refused")
            loopNeed(runBlocking { client.board() } is LanResult.Ok, "the block survived a new code")
        }

        loopCheck("forty wrong codes sent at once still get only ten answers; the rest are shut out") {
            // هر اتصال روی رشتهٔ خودش سنجیده می‌شود. اگر «بسته است؟» و «یک
            // نادرستِ دیگر» دو قدمِ جدا باشند، چهل حدسِ هم‌زمان همه از قدمِ
            // اول می‌گذرند پیش از آنکه دهمی ثبت شود — و سقف دیگر ده نیست.
            loopNeed(runBlocking { client.board() } is LanResult.Ok, "the right code was refused before the burst")
            val n = 40
            val head = "GET /board HTTP/1.1\r\nX-Workshop-Code: 000000\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
            val sockets = List(n) { Socket(InetAddress.getLoopbackAddress(), port).apply { soTimeout = 5_000 } }
            val go = java.util.concurrent.CountDownLatch(1)
            val statuses = java.util.concurrent.ConcurrentLinkedQueue<String>()
            val senders = sockets.map { s ->
                thread {
                    s.use {
                        go.await()
                        runCatching {
                            it.getOutputStream().apply { write(head); flush() }
                            statuses += it.getInputStream().readBytes().toString(Charsets.UTF_8).lineSequence().first()
                        }
                    }
                }
            }
            go.countDown()
            senders.forEach { it.join(10_000) }
            // بستنِ همین نشانی (loopback) بندهای بعدی را نگیرد، چه این بند قبول شود چه نه.
            val wrong = statuses.count { it.startsWith("HTTP/1.1 401") }
            val shut = statuses.count { it.startsWith("HTTP/1.1 429") }
            loopNeed(server.changeCode(code), "changeCode refused")
            loopNeed(
                wrong == LanGuard.MAX_FAILURES && shut == n - wrong,
                "$wrong answered as a wrong code and $shut shut out, of $n guesses sent at once"
            )
        }

        loopCheck("an IPv6 peer is turned away (it can take a fresh address for every ten guesses)") {
            val v6 = runCatching { InetAddress.getByName("::1") }.getOrNull()
            val p6 = freeLoopbackPort()
            val s6 = v6?.let { LanServer(db, it, p6) }
            if (s6 == null || !s6.start(code)) {
                println("        (no IPv6 loopback here - skipped)")
                return@loopCheck
            }
            try {
                val s = runCatching { Socket(v6, p6) }.getOrElse {
                    throw IllegalStateException("could not reach the IPv6 test server: $it")
                }
                // سرور بی جواب می‌بندد؛ خواندن یا هیچ برمی‌گرداند یا «reset» —
                // هر دو یعنی هیچ بایتی از دفتر نرسید.
                val got = s.use {
                    it.soTimeout = 5_000
                    runCatching {
                        it.getOutputStream().apply { write("GET /ping HTTP/1.1\r\n\r\n".toByteArray(Charsets.ISO_8859_1)); flush() }
                        it.getInputStream().readBytes().size
                    }.getOrDefault(0)
                }
                loopNeed(got == 0, "an IPv6 peer got $got bytes back")
            } finally {
                s6.stop()
            }
        }

        val text = "خیاط احمد کارِ سفارشِ ۱۲ را تمام کرد؛ آستین‌ها را دوباره دوخت. ".repeat(10).take(300)
        loopCheck("a 300-letter Dari request lands once, fast, byte for byte") {
            val started = System.nanoTime()
            val r = runBlocking { client.sendRequest("گوشیِ آزمون", ahmad, "NOTE", "پیام", note = text) }
            val ms = (System.nanoTime() - started) / 1_000_000
            println("        (round trip ${ms} ms, body ${text.toByteArray(Charsets.UTF_8).size} bytes)")
            loopNeed(r is LanResult.Ok, "got $r")
            // خوانندهٔ حرفی هشت ثانیه می‌ماند و بعد «وصل نشد» می‌داد.
            loopNeed(ms < 2_000, "took $ms ms - the reader is waiting for bytes that never come")
            val rows = runBlocking { requests() }
            loopNeed(rows.size == 1, "expected exactly one row, got ${rows.size}")
            loopNeed(rows[0].note == text, "the Dari text did not round-trip: «${rows[0].note.take(40)}…»")
            loopNeed(rows[0].worker == ahmad, "worker became «${rows[0].worker}»")
        }

        loopCheck("the same press sent again is not saved twice") {
            val r = runBlocking { client.sendRequest("گوشیِ آزمون", ahmad, "NOTE", "پیام", note = text) }
            loopNeed(r is LanResult.Ok, "got $r")
            loopNeed((r as LanResult.Ok).value.contains("پیش‌تر"), "the worker should hear it was already received: ${r.value}")
            loopNeed(runBlocking { requests() }.size == 1, "a resend created a second row")
        }

        loopCheck("a tailor cannot report another tailor's work") {
            runBlocking {
                val theirs = db.sewingAssignmentDao().insert(
                    SewingAssignment(orderId = randomUuid().toString(), orderCode = "AJ-1", tailorLabel = "[T2] محمود", qty = 4, unitWage = 30)
                )
                val before = requests().size
                val r = client.sendRequest("گوشیِ آزمون", ahmad, "SEWING_DONE", "AJ-1", refId = theirs, amount = 4)
                loopNeed(r is LanResult.Err && r.message.contains("به نامِ شما نیست"), "got $r")
                loopNeed(requests().size == before, "a refused request was still saved")

                // ردیفی که از سرور نگذشته باشد (مثلاً از نسخهٔ قدیم) هم سرِ تأیید رد می‌شود.
                val sneaked = db.syncRequestDao().insert(
                    SyncRequest(deviceName = "x", worker = ahmad, type = "SEWING_DONE", summary = "AJ-1", refId = theirs, amount = 4)
                )
                val why = repo.approveRequest(sneaked)
                loopNeed(why != null && why.contains("محمود"), "approval did not refuse: $why")
                loopNeed(db.sewingAssignmentDao().getById(theirs)?.status == "SEWING", "the other tailor's work was closed")
                loopNeed(db.tailorWageDao().pendingList().none { it.assignmentId == theirs }, "a wage was posted")
                repo.rejectRequest(sneaked)
            }
        }

        loopCheck("a tailor's own work completes through approval") {
            runBlocking {
                // `orderId` باید شکلِ UUID داشته باشد؛ تمام کردنِ کار آن را می‌خوانَد.
                val mine = db.sewingAssignmentDao().insert(
                    SewingAssignment(orderId = randomUuid().toString(), orderCode = "AJ-2", tailorLabel = ahmad, qty = 5, unitWage = 40)
                )
                val r = client.sendRequest("گوشیِ آزمون", ahmad, "SEWING_DONE", "AJ-2", refId = mine, amount = 5)
                loopNeed(r is LanResult.Ok, "got $r")
                val req = requests().first { it.type == "SEWING_DONE" && it.refId == mine }
                val why = repo.approveRequest(req.id)
                loopNeed(why == null, "approval failed: $why")
                loopNeed(db.sewingAssignmentDao().getById(mine)?.status == "DONE", "assignment still open")
                loopNeed(db.tailorWageDao().pendingList().any { it.assignmentId == mine && it.amount == 200L }, "no 5 x 40 wage")
            }
        }

        loopCheck("a second partial report of the same job is kept, not merged") {
            runBlocking {
                val job = db.sewingAssignmentDao().insert(
                    SewingAssignment(orderId = randomUuid().toString(), orderCode = "AJ-3", tailorLabel = ahmad, qty = 10, unitWage = 10)
                )
                // ۵ از ۱۰، و پیش از تأیید باز ۵ از ۱۰ — شاید واقعاً ۵ تای دیگر.
                repeat(2) {
                    val r = client.sendRequest("گوشیِ آزمون", ahmad, "SEWING_DONE", "AJ-3", refId = job, amount = 5)
                    loopNeed(r is LanResult.Ok && !r.value.contains("پیش‌تر"), "report ${it + 1}: $r")
                }
                val rows = requests().filter { it.type == "SEWING_DONE" && it.refId == job }
                loopNeed(rows.size == 2, "expected two rows for two partial reports, got ${rows.size}")
                rows.forEach { repo.rejectRequest(it.id) }
            }
        }

        loopCheck("attendance takes the time the button was pressed, not the approval time") {
            runBlocking {
                val r = client.sendRequest("گوشیِ آزمون", ahmad, "ATTENDANCE_IN", "درخواستِ ثبتِ ورود")
                loopNeed(r is LanResult.Ok, "got $r")
                val req = requests().first { it.type == "ATTENDANCE_IN" }
                // کارفرما سه ساعت بعد تأیید می‌کند.
                val pressed = req.createdAt - 3 * 3_600_000L
                db.syncRequestDao().update(req.copy(createdAt = pressed))
                val why = repo.approveRequest(req.id)
                loopNeed(why == null, "approval failed: $why")
                val open = db.attendanceDao().findOpen("احمد")
                loopNeed(open != null, "no open shift for «احمد»")
                loopNeed(open!!.checkIn == pressed, "checkIn ${open.checkIn}, pressed $pressed - the approval time leaked in")
            }
        }

        loopCheck("an OUT approved before its IN brings the IN with it") {
            runBlocking {
                val now = System.currentTimeMillis()
                val inId = db.syncRequestDao().insert(
                    SyncRequest(deviceName = "x", worker = "[T3] بلال", type = "ATTENDANCE_IN", summary = "ورود", createdAt = now - 2 * 3_600_000L)
                )
                val outId = db.syncRequestDao().insert(
                    SyncRequest(deviceName = "x", worker = "[T3] بلال", type = "ATTENDANCE_OUT", summary = "خروج", createdAt = now - 3_600_000L)
                )
                val why = repo.approveRequest(outId)
                loopNeed(why == null, "approval failed: $why")
                val last = db.attendanceDao().findLast("بلال")
                loopNeed(
                    last != null && last.checkIn == now - 2 * 3_600_000L && last.checkOut == now - 3_600_000L,
                    "shift is $last"
                )
                loopNeed(db.syncRequestDao().getById(inId)?.status == "APPROVED", "the IN was left pending")
            }
        }

        loopCheck("a refused approval leaves nothing behind (the transaction rolls back)") {
            runBlocking {
                val now = System.currentTimeMillis()
                db.attendanceDao().insert(AttendanceRecord(employee = "کریم", checkIn = now - 5 * 3_600_000L))
                // دو خروج: اولی بازه را می‌بندد، دومی دیگر بازه‌ای ندارد.
                val out1 = db.syncRequestDao().insert(
                    SyncRequest(deviceName = "x", worker = "[T4] کریم", type = "ATTENDANCE_OUT", summary = "خروج", createdAt = now - 2 * 3_600_000L)
                )
                val out2 = db.syncRequestDao().insert(
                    SyncRequest(deviceName = "x", worker = "[T4] کریم", type = "ATTENDANCE_OUT", summary = "خروج", createdAt = now - 3_600_000L)
                )
                val why = repo.approveRequest(out2)
                loopNeed(why != null, "the second OUT should be refused")
                loopNeed(db.attendanceDao().findOpen("کریم") != null, "the first OUT's write survived the refusal")
                loopNeed(db.syncRequestDao().getById(out1)?.status == "PENDING", "out1 was marked approved anyway")
                loopNeed(db.syncRequestDao().getById(out2)?.status == "PENDING", "out2 left pending")
            }
        }

        loopCheck("a truncated body is refused and nothing is saved") {
            val before = runBlocking { requests() }.size
            val body = """{"type":"NOTE","worker":"$ahmad","note":"نیمه"}""".toByteArray(Charsets.UTF_8)
            val head = "POST /request HTTP/1.1\r\nX-Workshop-Code: $code\r\nContent-Length: ${body.size + 40}\r\n\r\n"
            val status = rawExchange(port, head.toByteArray(Charsets.ISO_8859_1) + body, closeOutput = true)
            loopNeed(status.startsWith("HTTP/1.1 400"), "got «$status»")
            loopNeed(runBlocking { requests() }.size == before, "half a request was saved")
        }

        loopCheck("an oversized body is refused with 413 before it is read") {
            val head = "POST /request HTTP/1.1\r\nX-Workshop-Code: $code\r\n" +
                "Content-Length: ${LanWire.MAX_BODY_BYTES + 1}\r\n\r\n"
            val status = rawExchange(port, head.toByteArray(Charsets.ISO_8859_1), closeOutput = false)
            loopNeed(status.startsWith("HTTP/1.1 413"), "got «$status»")
        }

        loopCheck("a redirect is not followed, so the code never leaves for another host") {
            // چیزی روی نشانیِ «کارگاه» که هر درخواست را با ۳۰۲ به میزبانِ
            // دیگری حواله می‌دهد. میزبانِ دوم هم روی loopback است تا آزمون
            // از این ماشین بیرون نرود؛ loopback هم مثلِ نشانیِ اینترنتی
            // بیرون از شبکهٔ کارگاه است، و اگر مشتری دنبالِ حواله برود،
            // رمز را همان‌جا تحویل می‌دهد.
            val loop = InetAddress.getLoopbackAddress()
            ServerSocket(0, 5, loop).use { elsewhere ->
                ServerSocket(0, 5, loop).use { trap ->
                    val trapThread = thread(isDaemon = true) {
                        runCatching {
                            trap.accept().use { s ->
                                s.soTimeout = 5_000
                                val reader = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                                while (reader.readLine()?.isNotEmpty() == true) Unit
                                s.getOutputStream().apply {
                                    write(
                                        ("HTTP/1.1 302 Found\r\n" +
                                            "Location: http://127.0.0.1:${elsewhere.localPort}/board\r\n" +
                                            "Content-Type: application/json\r\n" +
                                            "Content-Length: 11\r\nConnection: close\r\n\r\n{\"ok\":true}")
                                            .toByteArray(Charsets.ISO_8859_1)
                                    )
                                    flush()
                                }
                            }
                        }
                    }
                    val started = System.nanoTime()
                    val r = runBlocking { LanClient("127.0.0.1", code, trap.localPort, allowLoopback = true).board() }
                    val ms = (System.nanoTime() - started) / 1_000_000
                    trapThread.join(2_000)
                    // اگر مشتری دنبال رفته بود، اتصالش در صفِ این سوکت منتظر است.
                    elsewhere.soTimeout = 300
                    val leaked = runCatching {
                        elsewhere.accept().use { s ->
                            s.soTimeout = 1_000
                            String(s.getInputStream().readNBytes(512), Charsets.ISO_8859_1)
                        }
                    }.getOrNull()
                    loopNeed(leaked == null, "the client followed the redirect and sent: ${leaked?.lineSequence()?.take(4)?.joinToString(" | ")}")
                    loopNeed(r is LanResult.Err, "a redirect was taken as an answer: $r")
                    loopNeed(ms < 2_000, "took $ms ms")
                }
            }
        }

        loopCheck("stop and start twice on the same port") {
            repeat(2) { round ->
                server.stop()
                loopNeed(server.start(code), "restart ${round + 1} could not bind port $port")
                val r = runBlocking { client.ping() }
                loopNeed(r is LanResult.Ok, "after restart ${round + 1}: $r")
            }
        }

        // ---------------- LanHub: روشن‌شدنِ خودکار فقط در کارگاه ----------------
        val workshop = LanNetwork("192.168.77.0/24", "192.168.77.1", "a4:2b:b0:02:34:56")
        val cafe = workshop.copy(gatewayMac = "10:fe:ed:00:00:01") // همان مدلِ مودم، همان نشانی
        val hubCode = "551177"
        val hubSettings = FileSettings(File(dir, "hub-settings"))
        LanPrefs.setMode(hubSettings, DeviceMode.MAIN)
        LanPrefs.setCode(hubSettings, hubCode)
        LanPrefs.setNetwork(hubSettings, workshop.encode())
        val host = FakeHost(db, mapOf("192.168.77.7" to workshop))
        val licence = CompletableDeferred<Unit>()
        val hubPort = freeLoopbackPort()
        val hub = LanHub(
            InetAddress.getLoopbackAddress(), hubPort,
            licenceLoaded = { licence.await() }, offIntervalMs = 100, onIntervalMs = 100,
        )
        val hubClient = LanClient("127.0.0.1", hubCode, hubPort, allowLoopback = true)
        val extraHubs = mutableListOf<LanHub>()

        loopCheck("auto-start waits for the licence, then binds on the workshop network") {
            hub.autoStart(hubSettings, host)
            Thread.sleep(400)
            // پیش از خوانده شدنِ لایسنس «نوشتن مجاز است» پیش‌فرض است؛ سرور نباید باز باشد.
            loopNeed(!listening(hubPort), "the port was taken before the licence was read")
            loopNeed(hub.state.value.off == HubOff.WAITING, "state ${hub.state.value}")
            licence.complete(Unit)
            loopNeed(waitFor(3_000) { hub.state.value.serving }, "not serving after the licence: ${hub.state.value}")
            loopNeed(hub.boundPort == hubPort, "bound ${hub.boundPort}, wanted $hubPort")
            loopNeed(runBlocking { hubClient.board() } is LanResult.Ok, "the auto-started hub did not answer")
        }

        loopCheck("a second auto-start in the same process changes nothing (a phone rotation must not undo the owner)") {
            hub.autoStart(hubSettings, host)
            loopNeed(hub.state.value.serving, "a second autoStart disturbed a serving hub: ${hub.state.value}")
        }

        loopCheck("the hub counts a phone that reached it once, a ping counts, a wrong code does not") {
            // شمارِ کاشیِ صفحهٔ اول («امروز چند گوشی رسید») از همین می‌آید.
            // loopback یک نشانی است: درخواستِ رمزدارِ بندِ اول همان یک گوشی است.
            val seen = hub.seen.value
            loopNeed(seen.lastOk.keys == setOf("127.0.0.1"), "after one right-code request the hub saw ${seen.lastOk.keys}")
            val first = seen.lastAt ?: 0L
            Thread.sleep(5)
            val wrong = runBlocking { LanClient("127.0.0.1", "000000", hubPort, allowLoopback = true).board() }
            loopNeed(wrong is LanResult.Err, "a wrong code was answered: $wrong")
            loopNeed(hub.seen.value.lastAt == first, "a wrong code was counted as a phone")
            Thread.sleep(5)
            // اسکنِ QR روی گوشیِ کارگر فقط `/ping` می‌فرستد؛ همان باید دیده شود.
            loopNeed(runBlocking { hubClient.ping() } is LanResult.Ok, "ping failed")
            val after = hub.seen.value
            loopNeed(
                after.lastOk.size == 1 && (after.lastAt ?: 0L) > first,
                "a ping was not counted, or one phone became two: ${after.lastOk}"
            )
        }

        loopCheck("at a cafe with the same router model the hub shuts, and opens again back at the workshop") {
            host.nets = mapOf("192.168.77.7" to cafe)
            loopNeed(waitFor(3_000) { hub.state.value.off == HubOff.OTHER_NETWORK }, "still ${hub.state.value} at the cafe")
            loopNeed(!listening(hubPort), "the port stayed open at the cafe")
            host.nets = mapOf("192.168.77.7" to workshop)
            loopNeed(waitFor(3_000) { hub.state.value.serving }, "did not come back at the workshop: ${hub.state.value}")
            loopNeed(runBlocking { hubClient.ping() } is LanResult.Ok, "back at the workshop but not answering")
        }

        loopCheck("Wi-Fi gone for a moment: a serving hub keeps listening") {
            host.nets = emptyMap()
            Thread.sleep(400)
            loopNeed(hub.state.value.serving, "a Wi-Fi blink shut the hub: ${hub.state.value}")
            host.nets = mapOf("192.168.77.7" to workshop)
        }

        loopCheck("a new code swaps in place: same port, the old code refused, the new one accepted") {
            LanPrefs.setCode(hubSettings, "662288")
            loopNeed(hub.changeCode("662288"), "changeCode refused")
            loopNeed(hub.boundPort == hubPort, "the port moved to ${hub.boundPort}")
            val old = runBlocking { hubClient.board() }
            loopNeed(old is LanResult.Err && old.message == "رمزِ اتصال درست نیست.", "old code: $old")
            val fresh = runBlocking { LanClient("127.0.0.1", "662288", hubPort, allowLoopback = true).board() }
            loopNeed(fresh is LanResult.Ok, "new code: $fresh")
        }

        loopCheck("the owner's stop sticks") {
            hub.stop()
            loopNeed(!listening(hubPort), "stop() left the port open")
            hub.autoStart(hubSettings, host)
            Thread.sleep(400)
            loopNeed(hub.state.value.off == HubOff.STOPPED && !listening(hubPort), "auto-start undid the owner's stop: ${hub.state.value}")
        }

        loopCheck("a manual start makes this network the workshop") {
            val there = LanNetwork("10.9.8.0/24", "10.9.8.1")
            host.nets = mapOf("10.9.8.20" to there)
            loopNeed(hub.start(hubSettings, host), "manual start failed: ${hub.state.value}")
            loopNeed(
                waitFor(3_000) { LanNetwork.decode(LanPrefs.network(hubSettings)) == there },
                "remembered «${LanPrefs.network(hubSettings)}», wanted ${there.encode()}"
            )
            hub.stop()
            host.nets = mapOf("192.168.77.7" to workshop)
            LanPrefs.setNetwork(hubSettings, workshop.encode())
        }

        loopCheck("auto-start at a cafe never binds") {
            val p2 = freeLoopbackPort()
            val away = LanHub(InetAddress.getLoopbackAddress(), p2, licenceLoaded = {}, offIntervalMs = 100, onIntervalMs = 100)
            extraHubs += away
            away.autoStart(hubSettings, FakeHost(db, mapOf("192.168.77.7" to cafe)))
            loopNeed(waitFor(3_000) { away.state.value.off == HubOff.OTHER_NETWORK }, "state ${away.state.value}")
            loopNeed(!listening(p2), "the hub bound at the cafe")
        }

        loopCheck("a hub upgraded from 1.9.0 (no network remembered) waits for the owner instead of guessing") {
            val fresh = FileSettings(File(dir, "old-hub-settings"))
            LanPrefs.setMode(fresh, DeviceMode.MAIN)
            LanPrefs.setCode(fresh, hubCode)
            val p3 = freeLoopbackPort()
            val old = LanHub(InetAddress.getLoopbackAddress(), p3, licenceLoaded = {}, offIntervalMs = 100, onIntervalMs = 100)
            extraHubs += old
            old.autoStart(fresh, FakeHost(db, mapOf("192.168.77.7" to workshop)))
            loopNeed(waitFor(3_000) { old.state.value.off == HubOff.NEW_NETWORK }, "state ${old.state.value}")
            loopNeed(!listening(p3), "an unknown network was trusted")
        }

        loopCheck("after a restore, the next opening of the app shares again (a phone's process outlives «close the app»)") {
            hub.stopForRestore()
            loopNeed(!listening(hubPort), "stopForRestore left the port open")
            hub.autoStart(hubSettings, host)
            loopNeed(waitFor(3_000) { hub.state.value.serving }, "not serving after restore and reopening: ${hub.state.value}")
            loopNeed(runBlocking { hubClient.ping() } is LanResult.Ok, "serving but not answering")
        }

        loopCheck("a device that is not the main one never looks at the network (no route/arp at every launch)") {
            val plain = FileSettings(File(dir, "plain-settings"))
            val probe = FakeHost(db, mapOf("192.168.77.7" to workshop))
            val p4 = freeLoopbackPort()
            val idle = LanHub(InetAddress.getLoopbackAddress(), p4, licenceLoaded = {}, offIntervalMs = 100, onIntervalMs = 100)
            extraHubs += idle
            idle.autoStart(plain, probe)
            loopNeed(waitFor(3_000) { idle.state.value.off == HubOff.NOT_MAIN }, "state ${idle.state.value}")
            loopNeed(probe.looked == 0, "a device that is not the main one looked at the network ${probe.looked} times")
            loopNeed(!listening(p4), "a device that is not the main one bound")
        }

        hub.stop()
        extraHubs.forEach { it.stop() }

        // ---------------- قراردادِ ۲: کامپیوترِ کارگاه و گوشی‌ها ----------------
        loopFailed += runHubLoop(dir)
    } finally {
        server.stop()
        runCatching { db.closeConnection() }
        dir.deleteRecursively()
    }

    println("=".repeat(52))
    if (loopFailed == 0) {
        println("lan loop verified")
        kotlin.system.exitProcess(0)
    }
    println("$loopFailed lan loop checks FAILED")
    kotlin.system.exitProcess(1)
}

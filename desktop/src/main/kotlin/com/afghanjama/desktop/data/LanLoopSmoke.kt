package com.afghanjama.desktop.data

import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.HostAddress
import com.afghanjama.lan.LanClient
import com.afghanjama.lan.LanResult
import com.afghanjama.lan.LanServer
import com.afghanjama.lan.LanWire
import com.afghanjama.util.randomUuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.Charset
import java.nio.file.Files
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
    val server = LanServer(db, InetAddress.getLoopbackAddress(), port)
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

package com.afghanjama.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.use
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.afghanjama.data.ShiftPolicy
import com.afghanjama.data.entities.BreakTime
import com.afghanjama.data.repo.Repo
import com.afghanjama.desktop.data.DeskPrefs
import com.afghanjama.desktop.data.FileSettings
import com.afghanjama.desktop.data.openDatabase
import com.afghanjama.desktop.platform.DeskReminderLoop
import com.afghanjama.desktop.platform.DesktopFileExport
import com.afghanjama.desktop.platform.TelHandler
import com.afghanjama.ui.theme.LightColors
import com.afghanjama.ui.vm.NewSaleViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime

/*
 * دودآزماییِ پنجرهٔ کامپیوتر: آنچه میانِ دو نفر و دو اجرای برنامه می‌ماند.
 *
 * **چرا جدا.** `screenSmoke` می‌گوید صفحه‌ها رسم می‌شوند؛ این می‌گوید پنجره
 * چه چیزی را **نگه می‌دارد** و چه چیزی را نه:
 *
 *   ۱. یادآورِ گفته‌شده پس از بستن و باز کردنِ برنامه دوباره گفته نمی‌شود،
 *      و پشتِ صفحهٔ ورود اصلاً گفته نمی‌شود.
 *   ۲. فاکتورِ نیمه‌کارهٔ یک نفر پس از «خروج» یا قفلِ خودکار به نفرِ بعدی
 *      نمی‌رسد.
 *   ۳. خروجیِ اکسل BOM دارد — بی آن، اکسلِ ویندوز دری را جویده نشان می‌دهد.
 *   ۴. پرسشِ «برنامهٔ تماس هست؟» سقفِ زمان دارد و پروسهٔ گیرکرده کشته می‌شود.
 *
 * هیچ بندی به دفتر یا تنظیماتِ واقعی دست نمی‌زند: همه روی پوشهٔ موقت.
 */

private class DeskFailure(msg: String) : RuntimeException(msg)

private fun deskNeed(cond: Boolean, msg: String) {
    if (!cond) throw DeskFailure(msg)
}

private fun deskCheck(name: String, body: () -> Unit): Boolean =
    try {
        body()
        println("OK    $name")
        true
    } catch (t: Throwable) {
        println("FAIL  $name")
        println("        ${t::class.java.name}: ${t.message}")
        false
    }

private class SmokeStore : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

fun main() {
    println("desk smoke - KhayatYar desktop")
    println("=".repeat(52))
    val dir = Files.createTempDirectory("khayatyar-desk").toFile()
    var ok = true

    val db = openDatabase(File(dir, "desk.db"))
    val repo = Repo(db)
    val prefsDir = File(dir, "prefs")
    val employee = "کارمندِ آزمون"

    try {
        // ── ۱. یادآور ─────────────────────────────────────────────────
        val checkIn = runBlocking {
            repo.checkIn(employee)
            repo.observeAttendance().first().single().checkIn
        }
        // ده دقیقه مانده به پایانِ شیفت، و سه دقیقه پس از وقتِ چای.
        val now = checkIn + ShiftPolicy.SHIFT_MS - 10 * 60_000L
        val tea = LocalDateTime.of(2026, 10, 20, 10, 3)
        runBlocking { repo.upsertBreakTime(BreakTime(title = "چای", hour = 10, minute = 0)) }

        ok = deskCheck("no reminder is spoken, read or stored behind the login screen") {
            val settings = FileSettings(prefsDir)
            DeskReminderLoop.signedIn.value = false
            val before = DeskNotices.items.value.size
            val due = runBlocking { DeskReminderLoop.check(repo, settings, now, tea) }
            deskNeed(due.isEmpty(), "nothing may be due while signed out, got ${due.map { it.key }}")
            deskNeed(DeskNotices.items.value.size == before, "no notice (with an employee's name) may appear while signed out")
            deskNeed(DeskPrefs.shownReminders(settings, now).isEmpty(), "nothing may be marked shown while signed out")
        } && ok

        ok = deskCheck("a reminder is spoken once, and not again after the app restarts") {
            DeskReminderLoop.signedIn.value = true
            val first = runBlocking { DeskReminderLoop.check(repo, FileSettings(prefsDir), now, tea) }
            deskNeed(first.size == 2, "the shift end and the tea break should be due, got ${first.map { it.key }}")
            deskNeed(DeskNotices.items.value.any { employee in it.text }, "the shift reminder should be on screen")

            // نمونهٔ تازهٔ تنظیمات = خواندن دوباره از دیسک، یعنی همان چیزی که
            // پس از بستن و باز کردنِ برنامه اتفاق می‌افتد.
            val again = runBlocking { DeskReminderLoop.check(repo, FileSettings(prefsDir), now + 60_000L, tea.plusMinutes(1)) }
            deskNeed(again.isEmpty(), "a reminder already spoken must not come back after a restart, got ${again.map { it.key }}")
        } && ok

        ok = deskCheck("shown reminders are forgotten after their lifetime, so the file stays small") {
            val settings = FileSettings(prefsDir)
            deskNeed(DeskPrefs.shownReminders(settings, now).size == 2, "both keys should be on disk")
            val later = now + DeskPrefs.REMINDER_SHOWN_TTL_MS + 1
            deskNeed(DeskPrefs.shownReminders(settings, later).isEmpty(), "keys older than the lifetime must be dropped")
            DeskPrefs.markRemindersShown(settings, listOf("break|99|1"), later)
            val kept = DeskPrefs.shownReminders(FileSettings(prefsDir), later)
            deskNeed(kept.keys == setOf("break|99|1"), "writing must prune the expired keys, kept ${kept.keys}")
            // نامی با جداکننده‌های خودِ فایل هم سالم برمی‌گردد.
            val odd = "shift|نام\tبا\nخط|1"
            DeskPrefs.markRemindersShown(settings, listOf(odd), later)
            deskNeed(odd in DeskPrefs.shownReminders(FileSettings(prefsDir), later), "a key with a tab or newline must round-trip")
        } && ok

        // ── ۲. جلسه ───────────────────────────────────────────────────
        ok = deskCheck("a half-built invoice does not survive logout or auto-lock") {
            var signedIn by mutableStateOf(true)
            var seen: NewSaleViewModel? = null
            val app = SmokeStore()
            ImageComposeScene(width = 400, height = 300, density = Density(1f)) {
                MaterialTheme(colorScheme = LightColors) {
                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Rtl,
                        LocalViewModelStoreOwner provides app,
                    ) {
                        SignedInGate(signedIn = signedIn, login = {}) {
                            seen = viewModel { NewSaleViewModel(repo) }
                        }
                    }
                }
            }.use { scene ->
                fun frame() {
                    Snapshot.sendApplyNotifications()
                    scene.render()
                }
                frame()
                val first = seen ?: error("the signed-in content was not composed")
                deskNeed(DeskReminderLoop.signedIn.value, "signing in should wake the reminders")
                first.addLine()
                first.setReceived("500")
                deskNeed(first.ui.value.lines.size == 2, "setup: the draft should have two lines")
                frame()
                deskNeed(seen === first, "the same session must keep its invoice across frames")

                // «خروج از حساب» و قفلِ خودکار هر دو همین‌اند: `authVm.logout()`.
                signedIn = false
                frame()
                deskNeed(!DeskReminderLoop.signedIn.value, "reminders must stop when signed out")
                deskNeed(!first.viewModelScope.isActive, "the previous session's view models must be cleared")

                signedIn = true
                frame()
                val second = seen!!
                deskNeed(second !== first, "the next person must get a fresh invoice, not the previous one")
                deskNeed(second.ui.value.lines.size == 1, "the fresh invoice should have one empty line")
                deskNeed(second.ui.value.receivedText.isEmpty(), "the fresh invoice must not carry the previous amount")
            }
        } && ok

        // ── ۳. خروجیِ اکسل ────────────────────────────────────────────
        ok = deskCheck("the Excel export starts with a UTF-8 BOM and keeps the Dari text") {
            val text = "\"نام\",\"مبلغ\"\r\n\"خیاطِ کابل\",\"۱۲۰۰\"\r\n"
            val f = File(dir, "export.csv")
            DesktopFileExport.writeText(f, text)
            val bytes = f.readBytes()
            deskNeed(bytes.size >= 3, "the file is empty")
            deskNeed(
                bytes.copyOfRange(0, 3).contentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())),
                "the first three bytes must be EF BB BF, got ${bytes.take(3).joinToString { "%02X".format(it) }}"
            )
            deskNeed(String(bytes, 3, bytes.size - 3, Charsets.UTF_8) == text, "the text after the BOM must be the text, once")
        } && ok

        // ── ۴. پرسشِ «برنامهٔ تماس» ──────────────────────────────────
        ok = deskCheck("a hung system query is cut off at its timeout and its process killed") {
            val win = System.getProperty("os.name").orEmpty().lowercase().contains("win")
            val hang = if (win) listOf("ping", "-n", "30", "127.0.0.1") else listOf("sleep", "30")
            val started = System.nanoTime()
            var proc: Process? = null
            val r = TelHandler.exitsOk(hang, 400) { proc = it }
            val tookMs = (System.nanoTime() - started) / 1_000_000
            deskNeed(r == null, "a query past its timeout must report no answer, got $r")
            deskNeed(tookMs < 4_000, "the timeout must hold, took $tookMs ms")
            // همان پروسه، نه «هیچ فرزندی»: روی ویندوز JVM خودش یک conhost.exe
            // به‌عنوانِ فرزند دارد که ربطی به این پرسش ندارد.
            val p = proc ?: error("the query never started")
            deskNeed(!p.isAlive, "the hung process (pid ${p.pid()}) must be killed")
            deskNeed(p.toHandle().descendants().noneMatch { it.isAlive }, "nothing the hung process started may outlive it")

            val yes = if (win) listOf("cmd", "/c", "exit", "0") else listOf("true")
            val no = if (win) listOf("cmd", "/c", "exit", "1") else listOf("false")
            deskNeed(TelHandler.exitsOk(yes, 5_000) == true, "a query that succeeds must say so")
            deskNeed(TelHandler.exitsOk(no, 5_000) == false, "a query that fails must say so")
        } && ok

        // ── ۵. شماره در خبر، چپ‌به‌راست ───────────────────────────────
        ok = deskCheck("a phone number in a notice is kept apart and drawn left-to-right") {
            // یادآورهای بندهای بالا کنار می‌روند تا این بند به آن‌ها بسته نباشد.
            DeskNotices.dismissSticky()
            DeskNotices.show("شماره کپی شد:", ltr = "+93 (0) 700 123 456")
            val n = DeskNotices.items.value.last()
            deskNeed(n.ltr == "+93 (0) 700 123 456" && "+93" !in n.text, "the number must travel apart from the Dari text")
            ImageComposeScene(width = 700, height = 200, density = Density(1f)) {
                MaterialTheme(colorScheme = LightColors, typography = vazirTypography()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        NoticeHost()
                    }
                }
            }.use { it.render() }
        } && ok

        // ── ۶. خطای خروجی گفته می‌شود ────────────────────────────────
        ok = deskCheck("a failed export is told in Dari, not swallowed") {
            DeskNotices.dismissSticky()
            val before = DeskNotices.items.value.map { it.id }.toSet()
            ImageComposeScene(width = 200, height = 100, density = Density(1f)) {
                val save = DesktopFileExport().rememberTextSaver("text/csv") { error("دفتر خوانده نشد") }
                androidx.compose.runtime.LaunchedEffect(Unit) { save("gozaresh.csv") }
            }.use { scene ->
                scene.render()
                val deadline = System.currentTimeMillis() + 5_000
                while (System.currentTimeMillis() < deadline &&
                    DeskNotices.items.value.none { it.id !in before && it.text.startsWith("خروجی ساخته نشد") }
                ) Thread.sleep(50)
            }
            val told = DeskNotices.items.value.filter { it.id !in before }.map { it.text }
            deskNeed(told.any { it.startsWith("خروجی ساخته نشد") && "دفتر خوانده نشد" in it }, "the failure should be told, got $told")
        } && ok
    } finally {
        DeskReminderLoop.signedIn.value = false
        db.close()
        dir.deleteRecursively()
    }

    println("=".repeat(52))
    println(if (ok) "desk window verified" else "desk smoke FAILED")
    kotlin.system.exitProcess(if (ok) 0 else 1)
}

package com.afghanjama.desktop.data

import com.afghanjama.data.Figure
import com.afghanjama.data.HubDigest
import com.afghanjama.data.SampleWorkshop
import com.afghanjama.data.repo.Repo
import com.afghanjama.prefs.BackupPrefs
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.ActionAlerts
import com.afghanjama.ui.vm.ActionCenterUi
import com.afghanjama.ui.vm.hubDigestFlow
import com.afghanjama.util.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files

/*
 * دودآزماییِ داشبورد روی دفترِ واقعیِ Room (موقت، با کارگاهِ نمونه).
 *
 * دو چیز که آزمونِ واحد نمی‌تواند ببیند، چون Room در `:app/test` نیست:
 *
 *  1. **کارت و هشدار یک حرف می‌زنند.** داشبورد و «مرکزِ هشدار» هر دو از همان
 *     جریان‌ها می‌خوانند؛ اینجا روی یک دفتر سنجیده می‌شود که هر جا هشدار هست
 *     کارتش هم چیزی دارد و برعکس (طرح، C risks).
 *  2. **«الان» بی ثبتِ تازه جلو می‌رود.** با ساعتِ دستی ده روز جلو برده
 *     می‌شود و هشدارِ «معطل» باید بی هیچ نوشتنی در دفتر پیدا شود (بازبینی،
 *     بندِ ۱۸).
 *
 * فایلِ دفتر و تنظیمات در پوشهٔ موقت‌اند، نه دفترِ کارگاه.
 */

private var failed = 0

private fun check(name: String, body: () -> Unit) {
    try {
        body()
        println("OK    $name")
    } catch (t: Throwable) {
        failed++
        println("FAIL  $name")
        println("        ${t::class.java.name}: ${t.message}")
    }
}

private fun need(cond: Boolean, msg: String) {
    if (!cond) throw IllegalStateException(msg)
}

/** چکیده وقتی همهٔ منبع‌هایش رسیده‌اند. */
private suspend fun settled(repo: Repo, dir: File, now: Long): HubDigest.Digest =
    withTimeout(30_000) {
        hubDigestFlow(repo, FileSettings(dir), access = { Access.MANAGER }, now = flowOf(now)).first { d ->
            listOf(d.alerts, d.requests, d.today, d.pipeline, d.atRisk, d.money, d.health, d.debts, d.materials, d.products, d.belowCost)
                .none { it is Figure.Unknown && it.reason == HubDigest.READING }
        }
    }

/** همان هشدارهای «مرکزِ هشدار»، با همان «آخرین پشتیبانِ» تنظیماتِ همین پوشه. */
private suspend fun alerts(repo: Repo, dir: File, now: Long): ActionCenterUi =
    withTimeout(30_000) {
        ActionAlerts(repo, { Access.MANAGER }, flowOf(BackupPrefs.lastAuto(FileSettings(dir))), flowOf(now)).ui.first()
    }

fun main() {
    println("dashboard smoke - KhayatYar desktop")
    println("=".repeat(52))
    val dir = Files.createTempDirectory("khayatyar-dash").toFile()
    val db = openDatabase(File(dir, "dash.db"))
    val repo = Repo(db)

    try {
        check("the dashboard of an empty ledger settles without a single invented zero") {
            runBlocking {
                val d = settled(repo, dir, nowMillis())
                need(d.atRisk is Figure.Unknown, "no delivery history: at-risk must be unknown, got ${d.atRisk}")
                need(d.health is Figure.Unknown, "an empty ledger has no financial picture, got ${d.health}")
            }
        }

        runBlocking { SampleWorkshop.create(repo) }
        val now = nowMillis()

        check("every card agrees with its alert on the sample workshop") {
            runBlocking {
                val d = settled(repo, dir, now)
                val a = alerts(repo, dir, now)
                val ids = a.alerts.map { it.id }.toSet()
                fun agree(alert: String, card: Boolean, what: String) =
                    need((alert in ids) == card, "$what: alert «$alert» ${alert in ids} but card $card")

                need((d.alerts as Figure.Known).value.total == a.total, "alert total ${a.total} vs card ${(d.alerts as Figure.Known).value.total}")
                agree("late_forecast", (d.atRisk as? Figure.Known)?.value?.isNotEmpty() == true, "at risk")
                agree("debt_follow_up", (d.debts as Figure.Known).value.any { !it.contactedToday }, "debt follow-up")
                agree("restock_bestsellers", (d.products as Figure.Known).value.restock.isNotEmpty(), "best sellers")
                agree("runout_soon", (d.materials as Figure.Known).value.any { it.days is Figure.Known }, "materials")
                agree("pending_requests", (d.requests as Figure.Known).value.isNotEmpty(), "phone requests")
                println("        ${a.total} alerts; pipeline ${(d.pipeline as Figure.Known).value.counts}")
            }
        }

        check("time moves on without a write: an order becomes stuck ten days later") {
            runBlocking {
                val later = now + 10 * 86_400_000L
                val before = alerts(repo, dir, now).alerts.any { it.id == "stuck_orders" }
                val after = alerts(repo, dir, later).alerts.any { it.id == "stuck_orders" }
                need(!before || after, "stuck before but not after?")
                need(after, "ten days on, some open order must be stuck")
                val d = settled(repo, dir, later)
                need((d.pipeline as Figure.Known).value.stuck > 0, "the pipeline card must count the stuck orders too")
            }
        }

        check("columns follow the work area: 4 / 3 / 2 / 1, priorities fill the top row, columns stay level") {
            val want = mapOf(1650 to 4, 1500 to 4, 1170 to 3, 1100 to 3, 830 to 2, 760 to 2, 600 to 1)
            want.forEach { (w, c) -> need(com.afghanjama.desktop.dashColumns(w) == c, "width $w should give $c columns") }
            // مهم‌ترین کارت (اولویتِ ۰) در هر شمارِ ستون، ستونِ ۰ (راست‌ترین) است.
            for (c in 1..4) need(com.afghanjama.desktop.dashColumn(0, c) == 0, "the top-priority card must stay the first (rightmost) column at $c")
            // پخشِ نوبتی: ردیفِ بالا مهم‌ترین‌هاست، نه یک ستونِ بلند.
            need((0..5).map { com.afghanjama.desktop.dashColumn(it, 3) } == listOf(0, 1, 2, 0, 1, 2), "three columns deal priorities across the top row")
            need((0..3).map { com.afghanjama.desktop.dashColumn(it, 2) } == listOf(0, 1, 0, 1), "two columns alternate by priority")
            need((0..4).all { com.afghanjama.desktop.dashColumn(it, 1) == 0 }, "one column stacks everything")
            // هم‌قدی: با ۱۷ کارت و ۳ ستون، شمارِ ستون‌ها حداکثر یکی فرق می‌کند.
            for (cols in 1..4) {
                val counts = IntArray(cols)
                for (i in 0 until 17) counts[com.afghanjama.desktop.dashColumn(i, cols)]++
                need(counts.max() - counts.min() <= 1, "$cols columns must stay within one card of each other, got ${counts.toList()}")
            }
        }

        check("each card follows its section's access: a seller sees no money or alerts") {
            val sales = com.afghanjama.ui.vm.Access.forRole(com.afghanjama.ui.vm.UserRole.SALES)
            val hidden = listOf(com.afghanjama.ui.nav.DeskSection.Finance, com.afghanjama.ui.nav.DeskSection.Payroll, com.afghanjama.ui.nav.DeskSection.ActionCenter, com.afghanjama.ui.nav.DeskSection.PurchasePlan)
            hidden.forEach { need(!com.afghanjama.desktop.dashVisible(it, true, sales), "a seller must not see the ${it.name} card") }
            need(com.afghanjama.desktop.dashVisible(com.afghanjama.ui.nav.DeskSection.FinishedWarehouse, true, sales), "a seller sees the product cards")
            need(!com.afghanjama.desktop.dashVisible(com.afghanjama.ui.nav.DeskSection.WorkshopLink, false, Access.MANAGER), "the extra gate (phone requests) still applies")
            com.afghanjama.ui.nav.DeskSection.entries.forEach { need(com.afghanjama.desktop.dashVisible(it, true, Access.MANAGER), "the manager sees every card") }
        }

        check("search matches Dari across ی/ک and spacing, and an empty query finds nothing") {
            need(com.afghanjama.desktop.paletteMatch("كريم رحيمي", "کریم"), "Arabic ي/ك must match Persian ی/ک")
            need(!com.afghanjama.desktop.paletteMatch("کریم", "  "), "blank query must not match everything")
            need(com.afghanjama.desktop.freshness(now, now + 30_000) == "همین حالا", "under a minute is «همین حالا»")
            need(com.afghanjama.desktop.freshness(now, now + 5 * 60_000) == "۵ دقیقه پیش", "five minutes ago")
        }

        check("the digest moves with the clock it is given") {
            runBlocking {
                val clock = MutableStateFlow(now)
                val flow = hubDigestFlow(repo, FileSettings(dir), access = { Access.MANAGER }, now = clock)
                val first = withTimeout(30_000) { flow.first { it.at == now } }
                need(first.at == now, "first digest at the given now")
                clock.value = now + 60_000
                val next = withTimeout(30_000) { hubDigestFlow(repo, FileSettings(dir), access = { Access.MANAGER }, now = clock).first { it.at == now + 60_000 } }
                need(next.at == now + 60_000, "the digest must follow the tick")
            }
        }
    } finally {
        db.close()
        dir.deleteRecursively()
    }

    println("=".repeat(52))
    println(if (failed == 0) "dashboard verified" else "$failed dashboard check(s) FAILED")
    kotlin.system.exitProcess(if (failed == 0) 0 else 1)
}

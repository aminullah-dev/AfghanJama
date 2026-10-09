package com.afghanjama

import com.afghanjama.data.Db
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.Tailor
import com.afghanjama.data.repo.Repo
import com.afghanjama.licence.LicenceFacts
import com.afghanjama.licence.LicenceGate
import com.afghanjama.licence.LicencePolicy.DAY_MILLIS
import com.afghanjama.licence.LicenceRefused
import com.afghanjama.licence.LicenceState
import com.afghanjama.licence.LicenceText
import com.afghanjama.licence.Licensing
import com.afghanjama.licence.Lnm1
import com.afghanjama.licence.MainLicence
import com.afghanjama.licence.ReadOnlyReason
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LanPrefs
import com.afghanjama.prefs.LicencePrefs
import com.afghanjama.prefs.Settings
import com.afghanjama.util.randomUuid
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * لایسنس در لایهٔ سرویس — همان‌جا که پروتکل می‌گوید.
 *
 * `Repo` واقعی است؛ فقط دیتابیس جایش را به یک ضبط‌کننده داده که هر
 * صدا زدنِ DAO را می‌نویسد. پس «هیچ چیز نوشته نشد» یک ادعای آزموده است،
 * نه حدس: اگر درِ لایسنس بعد از اولین نوشتن بسته می‌شد، این آزمون
 * می‌دید.
 */
class LicenceEnforcementTest {

    private val t0 = Instant.parse("2026-10-08T10:00:00Z").toEpochMilliseconds()
    private val testKey = Lnm1.pemToDer(LicenceVectors.TEST_PUBLIC_PEM)

    /** شناسهٔ سکویی که کدش همان `BDZ2-PWKS-XGAW-3YGB`ِ نمونه‌هاست. */
    private val deviceId = "test-device-1"

    @Before
    fun clean() = LicenceGate.resetForTest()

    @After
    fun reset() = LicenceGate.resetForTest()

    // ------------------------------------------------------------
    // ابزار
    // ------------------------------------------------------------

    private class MemSettings : Settings {
        val m = mutableMapOf<String, Any>()
        private fun k(f: String, k: String) = "$f/$k"
        override fun getString(file: String, key: String, def: String) = m[k(file, key)] as? String ?: def
        override fun putString(file: String, key: String, value: String) { m[k(file, key)] = value }
        override fun getBoolean(file: String, key: String, def: Boolean) = m[k(file, key)] as? Boolean ?: def
        override fun putBoolean(file: String, key: String, value: Boolean) { m[k(file, key)] = value }
        override fun getLong(file: String, key: String, def: Long) = m[k(file, key)] as? Long ?: def
        override fun putLong(file: String, key: String, value: Long) { m[k(file, key)] = value }
        override fun remove(file: String, key: String) { m.remove(k(file, key)) }
    }

    /**
     * دیتابیسِ ضبط‌کننده: هر متدِ هر DAO صدا زده شود، نامش در [calls]
     * می‌نشیند. چند جواب که `Repo` واقعاً عددش را لازم دارد از [answers]
     * می‌آید.
     */
    private class RecordingDb {
        val calls = mutableListOf<String>()
        val answers = mutableMapOf<String, Any?>("countCreatedSince" to 0)

        private fun dao(type: Class<*>): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, m, _ ->
            val name = "${type.simpleName}.${m.name}"
            calls += name
            when {
                answers.containsKey(m.name) -> answers[m.name]
                m.returnType.name == "kotlinx.coroutines.flow.Flow" -> flowOf(emptyList<Any>())
                else -> null
            }
        }

        val db: Db = Proxy.newProxyInstance(Db::class.java.classLoader, arrayOf(Db::class.java)) { _, m, args ->
            when {
                // تراکنش: بدنه همان‌جا اجرا می‌شود.
                m.name == "atomic" -> {
                    @Suppress("UNCHECKED_CAST")
                    val block = args[0] as (kotlin.coroutines.Continuation<Any?>) -> Any?
                    @Suppress("UNCHECKED_CAST")
                    block(args[1] as kotlin.coroutines.Continuation<Any?>)
                }
                m.name.endsWith("Dao") -> dao(m.returnType)
                m.returnType == Int::class.javaPrimitiveType -> 0
                m.returnType == Void.TYPE -> null
                else -> null
            }
        } as Db

        val writes get() = calls.filter { c ->
            listOf("insert", "update", "upsert", "delete", "set", "add").any { c.substringAfter('.').startsWith(it) }
        }
    }

    private fun installTrialOver() = LicenceGate.install(
        LicenceFacts(
            licence = null, machineCode = "BDZ2-PWKS-XGAW-3YGB",
            firstRunMillis = t0, lastSeenMillis = t0,
        )
    ).also { LicenceGate.clock = { t0 + 20 * DAY_MILLIS } }

    private fun order() = Order(
        id = randomUuid(), orderCode = "AJ-2026-000099", shortCode = "99", designTitle = "پیراهن", qty = 1,
        fabricType = "", fabricColor = "", size = "L", fabricUnit = "متر", fabricAmount = 0.0,
        fabricPrice = 0, workCost = 0, customerName = "", customerPhone = "", status = "CUTTING",
    )

    private inline fun refused(block: () -> Unit): LicenceRefused {
        try {
            block()
        } catch (e: LicenceRefused) {
            return e
        }
        fail("باید رد می‌شد")
        throw AssertionError()
    }

    // ------------------------------------------------------------
    // درِ نوشتن در Repo
    // ------------------------------------------------------------

    @Test
    fun `read-only refuses a new record before anything is written`() = runBlocking {
        installTrialOver()
        val rec = RecordingDb()
        val repo = Repo(rec.db)
        val e = refused { repo.addTailor(Tailor(code = "T1", name = "کریم")) }
        assertEquals(ReadOnlyReason.TRIAL_OVER, e.status.reason)
        assertTrue("هیچ DAOی صدا زده نشد: ${rec.calls}", rec.calls.isEmpty())
        val msg = LicenceGate.refusal.value
        assertNotNull("پیام برای پنجرهٔ مشترک", msg)
        assertTrue(msg!!, msg.contains("تنظیمات ← لایسنس"))
        assertTrue("می‌گوید چه هنوز کار می‌کند", msg.contains("پشتیبان"))
    }

    @Test
    fun `read-only refuses money, stock and order changes alike`() = runBlocking {
        installTrialOver()
        val rec = RecordingDb()
        val repo = Repo(rec.db)
        refused { repo.recordManualLedger("CUSTOMER", "احمد", 500, isPayment = false, paySource = "WALLET", note = "") }
        refused { repo.changeMaterialStock("پارچه", "متر", 2.0) }
        refused { repo.createOrder(order()) }
        refused { repo.updateOrder(order()) }
        refused { repo.deleteOrder(order()) }
        refused { repo.checkIn("کریم") }
        refused { repo.resetOperationalData() }
        assertTrue("هیچ نوشتنی: ${rec.writes}", rec.writes.isEmpty())
    }

    @Test
    fun `read-only still reads everything`() = runBlocking {
        installTrialOver()
        val rec = RecordingDb()
        val repo = Repo(rec.db)
        // فهرست‌ها، جست‌وجو، و آنچه چاپ و PDF و پشتیبان از آن می‌خوانند.
        repo.observeAllOrders()
        repo.observeAudit()
        repo.getOrder(randomUuid())
        repo.saleLinesByCode("FR-1")
        repo.auditLedgerEntries()
        assertTrue("فقط خواندن: ${rec.writes}", rec.writes.isEmpty())
        assertTrue(rec.calls.isNotEmpty())
        assertTrue("خروجی‌ها نشانِ آزمایشی می‌خورند", LicenceGate.watermark())
    }

    @Test
    fun `before the first check nothing is blocked`() = runBlocking {
        val rec = RecordingDb()
        Repo(rec.db).addTailor(Tailor(code = "T1", name = "کریم"))
        assertTrue(rec.writes.isNotEmpty())
        assertFalse(LicenceGate.watermark())
    }

    // ------------------------------------------------------------
    // سقفِ سفارش — فقط بر ساختن
    // ------------------------------------------------------------

    @Test
    fun `the trial cap is checked against the book when an order is created`() = runBlocking {
        LicenceGate.install(LicenceFacts(null, "BDZ2-PWKS-XGAW-3YGB", t0, t0, ordersSinceTrialStart = 0))
        LicenceGate.clock = { t0 + DAY_MILLIS }
        val rec = RecordingDb()
        rec.answers["countCreatedSince"] = 20
        val e = refused { Repo(rec.db).createOrder(order()) }
        assertEquals(ReadOnlyReason.TRIAL_CAP, e.status.reason)
        assertEquals(listOf("OrderDao.countCreatedSince"), rec.calls)
    }

    @Test
    fun `under the cap, editing and other records are untouched by it`() = runBlocking {
        LicenceGate.install(LicenceFacts(null, "BDZ2-PWKS-XGAW-3YGB", t0, t0, ordersSinceTrialStart = 19))
        LicenceGate.clock = { t0 + DAY_MILLIS }
        val rec = RecordingDb()
        val repo = Repo(rec.db)
        repo.updateOrder(order())
        repo.addTailor(Tailor(code = "T1", name = "کریم"))
        assertEquals(listOf("OrderDao.update", "MasterDataDao.insertTailor"), rec.writes)
        assertEquals(1, LicenceGate.current()!!.ordersLeft)
    }

    @Test
    fun `order slot helper counts from the trial start`() = runBlocking {
        LicenceGate.install(LicenceFacts(null, "X", t0, t0))
        LicenceGate.clock = { t0 }
        var since = 0L
        LicenceGate.requireOrderSlot { since = it; 3 }
        assertEquals(t0, since)
        assertEquals(17, LicenceGate.current()!!.ordersLeft)
    }

    // ------------------------------------------------------------
    // دستگاهِ کارگر
    // ------------------------------------------------------------

    @Test
    fun `worker devices are exempt and never counted`() = runBlocking {
        LicenceGate.install(LicenceFacts(null, "X", t0, t0, ordersSinceTrialStart = 99, mode = DeviceMode.WORKER))
        LicenceGate.clock = { t0 + 100 * DAY_MILLIS }
        val rec = RecordingDb()
        rec.answers["countCreatedSince"] = 99
        Repo(rec.db).addTailor(Tailor(code = "T1", name = "کریم"))
        assertTrue(rec.writes.isNotEmpty())
        var asked = false
        LicenceGate.requireOrderSlot { asked = true; 99 }
        assertFalse("روی کارگر سفارش شمرده نمی‌شود", asked)
    }

    @Test
    fun `a worker stops writing when its main device reports read-only`() = runBlocking {
        LicenceGate.install(LicenceFacts(null, "X", t0, t0, mode = DeviceMode.WORKER))
        LicenceGate.clock = { t0 }
        assertTrue(LicenceGate.canWrite())
        LicenceGate.update { it.copy(mainReport = MainLicence(LicenceState.READ_ONLY, false, t0)) }
        val rec = RecordingDb()
        val e = refused { Repo(rec.db).addTailor(Tailor(code = "T1", name = "کریم")) }
        assertEquals(ReadOnlyReason.MAIN_DEVICE, e.status.reason)
        assertTrue(rec.calls.isEmpty())
    }

    // ------------------------------------------------------------
    // سرویس: تنظیمات، دفتر، فعال‌سازی
    // ------------------------------------------------------------

    private fun licensing(s: Settings, repo: Repo?, now: () -> Long, id: String? = deviceId) =
        Licensing(s, repo, publicKeyDer = testKey, platformId = { id }, clock = now)

    @Test
    fun `activation lifts every limit in place`() = runBlocking {
        val s = MemSettings()
        var now = t0
        val rec = RecordingDb()
        val repo = Repo(rec.db)
        val lic = licensing(s, repo, { now })
        lic.refresh()
        now = t0 + 20 * DAY_MILLIS
        assertEquals(LicenceState.READ_ONLY, lic.refresh().state)
        refused { repo.addTailor(Tailor(code = "T1", name = "کریم")) }

        val done = lic.activate(LicenceVectors.key("khayatyar", "valid_perpetual"))
        done as Licensing.Activation.Done
        assertEquals(LicenceState.LICENSED, done.status.state)
        assertNull("کلیدِ همین دستگاه است", done.warning)
        assertFalse(LicenceGate.watermark())

        // همان Repo، همان دفتر، بی نصبِ دوباره:
        rec.calls.clear()
        repo.addTailor(Tailor(code = "T1", name = "کریم"))
        assertEquals(listOf("MasterDataDao.insertTailor"), rec.writes)
        // و کلید ماند:
        assertTrue(LicencePrefs.key(s).startsWith("LNM1."))
    }

    @Test
    fun `a bad key changes nothing and says why`() = runBlocking {
        val s = MemSettings()
        val lic = licensing(s, null, { t0 })
        lic.refresh()
        val r = lic.activate(LicenceVectors.key("khayatyar", "tampered_payload"))
        r as Licensing.Activation.Rejected
        assertEquals(LicenceText.problem(Lnm1.Problem.BAD_SIGNATURE), r.message)
        assertEquals("", LicencePrefs.key(s))
        assertEquals(LicenceState.TRIAL, LicenceGate.current()!!.state)
    }

    @Test
    fun `a key for another machine is accepted into the 30 day grace with a warning`() = runBlocking {
        val s = MemSettings()
        var now = t0
        val lic = licensing(s, null, { now }, id = "another-computer")
        val done = lic.activate(LicenceVectors.key("khayatyar", "valid_perpetual")) as Licensing.Activation.Done
        assertEquals(LicenceState.GRACE_MACHINE, done.status.state)
        assertNotNull(done.warning)
        // مهلت از اولین ناجوری شمرده می‌شود و با هر بار خواندن از نو شروع نمی‌شود.
        now = t0 + 31 * DAY_MILLIS
        val later = lic.refresh()
        assertEquals(LicenceState.READ_ONLY, later.state)
        assertEquals(ReadOnlyReason.MACHINE_GRACE_OVER, later.reason)
    }

    @Test
    fun `reinstalling does not restart the trial when the book is restored`() = runBlocking {
        // نصبِ اول: دوره شروع شد و لنگرش در دفتر نشست.
        val rec = RecordingDb()
        licensing(MemSettings(), Repo(rec.db), { t0 }).refresh()
        assertTrue("لنگر در دفتر نوشته شد", rec.calls.contains("AuditDao.insert"))

        // نصبِ دوباره بیست روز بعد: تنظیمات خالی، ولی دفترِ بازیابی‌شده لنگر را دارد.
        val restored = RecordingDb()
        restored.answers["firstAt"] = t0
        restored.answers["countCreatedSince"] = 0
        val fresh = MemSettings()
        val s = licensing(fresh, Repo(restored.db), { t0 + 20 * DAY_MILLIS }).refresh()
        assertEquals(LicenceState.READ_ONLY, s.state)
        assertEquals(ReadOnlyReason.TRIAL_OVER, s.reason)
        assertEquals(t0, LicencePrefs.firstRun(fresh))
    }

    @Test
    fun `an upgrade from a version without licensing starts a fresh trial`() = runBlocking {
        // دفترِ قدیمی صدها سفارش دارد ولی هیچ‌کدام پس از شروعِ دوره نیست.
        val rec = RecordingDb()
        rec.answers["firstAt"] = null
        rec.answers["countCreatedSince"] = 0
        val s = licensing(MemSettings(), Repo(rec.db), { t0 }).refresh()
        assertEquals(LicenceState.TRIAL, s.state)
        assertEquals(20, s.ordersLeft)
    }

    @Test
    fun `the last seen time is remembered so a rollback is caught after restart`() = runBlocking {
        val s = MemSettings()
        licensing(s, null, { t0 + 10 * DAY_MILLIS }).refresh()
        assertEquals(t0 + 10 * DAY_MILLIS, LicencePrefs.lastSeen(s))
        val back = licensing(s, null, { t0 + 2 * DAY_MILLIS }).refresh()
        assertTrue(back.clockRolledBack)
        assertEquals("دیرترین زمان پایین نمی‌آید", t0 + 10 * DAY_MILLIS, LicencePrefs.lastSeen(s))
    }

    @Test
    fun `an unreadable platform id falls back to one stable stored id`() = runBlocking {
        val s = MemSettings()
        val a = licensing(s, null, { t0 }, id = null).machine()
        val b = licensing(s, null, { t0 }, id = "  ").machine()
        assertTrue(a.fallback)
        assertEquals(a.code, b.code)
        val real = licensing(s, null, { t0 }).machine()
        assertFalse(real.fallback)
        assertEquals("BDZ2-PWKS-XGAW-3YGB", real.code)
    }

    @Test
    fun `a worker remembers what the main device reported`() = runBlocking {
        val s = MemSettings()
        LanPrefs.setMode(s, DeviceMode.WORKER)
        licensing(s, null, { t0 }).refresh()
        assertTrue(LicenceGate.canWrite())
        LicenceGate.onMainReport!!(MainLicence(LicenceState.READ_ONLY, false, t0))
        assertFalse(LicenceGate.canWrite())
        // پس از راه‌اندازیِ دوباره هم یادش هست.
        LicenceGate.resetForTest()
        licensing(s, null, { t0 }).refresh()
        assertFalse(LicenceGate.canWrite())
    }

    @Test
    fun `switching a device to worker mode lifts its own trial limits`() = runBlocking {
        val s = MemSettings()
        licensing(s, null, { t0 }).refresh()
        licensing(s, null, { t0 + 30 * DAY_MILLIS }).refresh()
        assertFalse(LicenceGate.canWrite())
        LicenceGate.update { it.copy(mode = DeviceMode.WORKER) }
        assertTrue(LicenceGate.canWrite())
    }
}

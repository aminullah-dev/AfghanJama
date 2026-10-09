package com.afghanjama

import com.afghanjama.licence.Licence
import com.afghanjama.licence.LicenceFacts
import com.afghanjama.licence.LicencePolicy
import com.afghanjama.licence.LicencePolicy.DAY_MILLIS
import com.afghanjama.licence.LicenceState
import com.afghanjama.licence.LicenceStatus
import com.afghanjama.licence.MainLicence
import com.afghanjama.licence.ReadOnlyReason
import com.afghanjama.prefs.DeviceMode
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * هر حالتِ پروتکل و هر گذر بینشان — با دادهٔ خالص.
 *
 * [LicencePolicy.evaluate] ساعت نمی‌خوانَد؛ «اکنون» داده می‌شود. پس
 * دورهٔ آزمایشیِ ۱۴ روزه، مهلتِ ۳۰ روزه و عقب‌کشیدنِ ساعت همه در چند
 * میلی‌ثانیه آزموده می‌شوند.
 */
class LicencePolicyTest {

    private val utc = TimeZone.UTC
    private val t0 = Instant.parse("2026-10-08T10:00:00Z").toEpochMilliseconds()
    private val here = "BDZ2-PWKS-XGAW-3YGB"
    private val elsewhere = "Y5XX-H0AY-2634-X0AQ"

    private fun licence(machine: String = here, expires: LocalDate? = null) = Licence(
        id = "KY-2026-0001", customer = "خیاطیِ نمونه", machine = machine,
        issued = LocalDate(2026, 10, 8), expires = expires, edition = "standard", features = listOf("*"),
    )

    private fun facts(
        licence: Licence? = null,
        firstRun: Long = t0,
        lastSeen: Long = t0,
        orders: Int = 0,
        mismatchSince: Long? = null,
        mode: DeviceMode = DeviceMode.STANDALONE,
        main: MainLicence? = null,
    ) = LicenceFacts(
        licence = licence, machineCode = here, firstRunMillis = firstRun, lastSeenMillis = lastSeen,
        machineMismatchSinceMillis = mismatchSince, ordersSinceTrialStart = orders, mode = mode, mainReport = main,
    )

    private fun eval(f: LicenceFacts, now: Long): LicenceStatus = LicencePolicy.evaluate(f, now, utc)

    private fun at(day: String) = Instant.parse("${day}T12:00:00Z").toEpochMilliseconds()

    // ---------------- دورهٔ آزمایشی ----------------

    @Test
    fun `a fresh install is a 14 day trial with 20 orders`() {
        val s = eval(facts(), t0)
        assertEquals(LicenceState.TRIAL, s.state)
        assertEquals(14, s.daysLeft)
        assertEquals(20, s.ordersLeft)
        assertTrue(s.canWrite)
        assertTrue("روی کاغذ نشانِ آزمایشی", s.watermark)
        assertTrue(s.needsBanner)
    }

    @Test
    fun `trial counts down and ends exactly after 14 days`() {
        assertEquals(1, eval(facts(), t0 + 13 * DAY_MILLIS + 1).daysLeft)
        assertEquals(LicenceState.TRIAL, eval(facts(), t0 + 14 * DAY_MILLIS - 1).state)
        val over = eval(facts(), t0 + 14 * DAY_MILLIS)
        assertEquals(LicenceState.READ_ONLY, over.state)
        assertEquals(ReadOnlyReason.TRIAL_OVER, over.reason)
        assertFalse(over.canWrite)
        assertTrue(over.watermark)
    }

    @Test
    fun `the 20th order is allowed, then the trial is read-only`() {
        val s19 = eval(facts(orders = 19), t0)
        assertEquals(LicenceState.TRIAL, s19.state)
        assertEquals(1, s19.ordersLeft)
        val s20 = eval(facts(orders = 20), t0)
        assertEquals(LicenceState.READ_ONLY, s20.state)
        assertEquals(ReadOnlyReason.TRIAL_CAP, s20.reason)
    }

    // ---------------- لایسنس ----------------

    @Test
    fun `a perpetual licence for this machine is licensed with no watermark`() {
        val s = eval(facts(licence()), t0 + 400 * DAY_MILLIS)
        assertEquals(LicenceState.LICENSED, s.state)
        assertNull(s.daysLeft)
        assertTrue(s.canWrite)
        assertFalse(s.watermark)
        assertFalse(s.needsBanner)
    }

    @Test
    fun `a licence lifts the trial cap and the trial end`() {
        val s = eval(facts(licence(), orders = 500), t0 + 90 * DAY_MILLIS)
        assertEquals(LicenceState.LICENSED, s.state)
    }

    @Test
    fun `any-machine licence works on every machine`() {
        assertEquals(LicenceState.LICENSED, eval(facts(licence(machine = "*")), t0).state)
    }

    @Test
    fun `expiry notices start 30 days before the last day`() {
        val lic = licence(expires = LocalDate(2026, 12, 31))
        val far = eval(facts(lic), at("2026-11-01"))
        assertEquals(61, far.daysLeft)
        assertFalse(far.needsBanner)
        val near = eval(facts(lic), at("2026-12-02"))
        assertEquals(30, near.daysLeft)
        assertTrue(near.needsBanner)
        val last = eval(facts(lic), at("2026-12-31"))
        assertEquals(LicenceState.LICENSED, last.state)
        assertEquals("آخرین روز هم شمرده می‌شود", 1, last.daysLeft)
    }

    @Test
    fun `after the last day there is a 30 day expiry grace, then read-only`() {
        val lic = licence(expires = LocalDate(2026, 12, 31))
        val g1 = eval(facts(lic), at("2027-01-01"))
        assertEquals(LicenceState.GRACE_EXPIRY, g1.state)
        assertEquals(30, g1.daysLeft)
        assertTrue("مهلت کامل کار می‌کند", g1.canWrite)
        assertFalse("مهلت نشانِ «بدون لایسنس» نمی‌خورد", g1.watermark)
        val gLast = eval(facts(lic), at("2027-01-30"))
        assertEquals(LicenceState.GRACE_EXPIRY, gLast.state)
        assertEquals(1, gLast.daysLeft)
        val over = eval(facts(lic), at("2027-01-31"))
        assertEquals(LicenceState.READ_ONLY, over.state)
        assertEquals(ReadOnlyReason.EXPIRY_GRACE_OVER, over.reason)
        assertEquals("لایسنس هنوز نشان داده می‌شود", "KY-2026-0001", over.licence?.id)
    }

    @Test
    fun `a licence for another machine gets 30 days from the first mismatch`() {
        val lic = licence(machine = elsewhere)
        val first = eval(facts(lic, mismatchSince = null), t0)
        assertEquals(LicenceState.GRACE_MACHINE, first.state)
        assertEquals(30, first.daysLeft)
        assertTrue(first.canWrite)
        val later = eval(facts(lic, mismatchSince = t0), t0 + 29 * DAY_MILLIS)
        assertEquals(LicenceState.GRACE_MACHINE, later.state)
        assertEquals(1, later.daysLeft)
        val over = eval(facts(lic, mismatchSince = t0), t0 + 30 * DAY_MILLIS)
        assertEquals(LicenceState.READ_ONLY, over.state)
        assertEquals(ReadOnlyReason.MACHINE_GRACE_OVER, over.reason)
    }

    // ---------------- عقب‌کشیدنِ ساعت ----------------

    @Test
    fun `turning the clock back does not extend the trial`() {
        // روزِ ۲۰ دیده شد، بعد ساعت به روزِ ۲ برگشت.
        val seen = t0 + 20 * DAY_MILLIS
        val s = eval(facts(lastSeen = seen), t0 + 2 * DAY_MILLIS)
        assertTrue(s.clockRolledBack)
        assertEquals(LicenceState.READ_ONLY, s.state)
        assertEquals(ReadOnlyReason.TRIAL_OVER, s.reason)
        assertTrue("دلیل و راه روی بنر", s.needsBanner)
    }

    @Test
    fun `turning the clock back does not extend an expiry grace`() {
        val lic = licence(expires = LocalDate(2026, 12, 31))
        val s = eval(facts(lic, lastSeen = at("2027-02-15")), at("2026-12-01"))
        assertTrue(s.clockRolledBack)
        assertEquals(LicenceState.READ_ONLY, s.state)
    }

    @Test
    fun `a rollback never locks a licensed workshop out`() {
        val s = eval(facts(licence(), lastSeen = t0 + 300 * DAY_MILLIS), t0)
        assertTrue(s.clockRolledBack)
        assertEquals(LicenceState.LICENSED, s.state)
        assertTrue(s.canWrite)
    }

    @Test
    fun `a small clock correction is not a rollback`() {
        val s = eval(facts(lastSeen = t0 + 20 * 60 * 60 * 1000L), t0)
        assertFalse(s.clockRolledBack)
        assertEquals(LicenceState.TRIAL, s.state)
    }

    // ---------------- دستگاهِ کارگر ----------------

    @Test
    fun `a worker phone needs no licence of its own`() {
        // بی‌لایسنس، بعد از ماه‌ها، با صد سفارش: باز هم کار می‌کند.
        val s = eval(facts(mode = DeviceMode.WORKER, orders = 100), t0 + 200 * DAY_MILLIS)
        assertTrue(s.coveredByMain)
        assertTrue(s.canWrite)
        assertEquals(LicenceState.LICENSED, s.state)
    }

    @Test
    fun `a worker follows a read-only main device`() {
        val main = MainLicence(LicenceState.READ_ONLY, writable = false, seenAtMillis = t0)
        val s = eval(facts(mode = DeviceMode.WORKER, main = main), t0)
        assertEquals(LicenceState.READ_ONLY, s.state)
        assertEquals(ReadOnlyReason.MAIN_DEVICE, s.reason)
        assertFalse(s.canWrite)
    }

    @Test
    fun `a worker of a trial main prints the trial watermark too`() {
        val main = MainLicence(LicenceState.TRIAL, writable = true, seenAtMillis = t0)
        val s = eval(facts(mode = DeviceMode.WORKER, main = main), t0)
        assertTrue(s.canWrite)
        assertTrue(s.watermark)
    }

    @Test
    fun `a main device licence covers the worker without a watermark`() {
        val main = MainLicence(LicenceState.LICENSED, writable = true, seenAtMillis = t0)
        val s = eval(facts(mode = DeviceMode.WORKER, main = main), t0)
        assertFalse(s.watermark)
        assertFalse(s.needsBanner)
    }

    @Test
    fun `the main device itself is licensed like a standalone one`() {
        assertEquals(LicenceState.TRIAL, eval(facts(mode = DeviceMode.MAIN), t0).state)
        assertEquals(
            ReadOnlyReason.TRIAL_OVER,
            eval(facts(mode = DeviceMode.MAIN), t0 + 15 * DAY_MILLIS).reason
        )
    }
}

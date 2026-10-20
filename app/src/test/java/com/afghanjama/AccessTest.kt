package com.afghanjama

import com.afghanjama.ui.nav.Routes
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.AppUser
import com.afghanjama.ui.vm.CurrentAccess
import com.afghanjama.ui.vm.Feature
import com.afghanjama.ui.vm.Permissions
import com.afghanjama.ui.vm.RouteAccess
import com.afghanjama.ui.vm.UserRole
import com.afghanjama.ui.vm.UserStore
import com.afghanjama.ui.vm.WorkshopLinkAccess
import com.afghanjama.ui.vm.AlertSeverity
import com.afghanjama.ui.vm.pendingRequestsAlert
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * دسترسیِ هر نفر — تیک‌هایی که مدیر می‌زند.
 *
 * هر کس فقط بخش‌هایی را باز می‌کند که تیکش را دارد؛ صفحه‌ای که در
 * نقشه نیست فقط مالِ مدیر است؛ و دستگاهی که کاربرِ شخصی ندارد همان
 * رفتارِ نقشِ قدیمی را نگه می‌دارد.
 */
class AccessTest {

    @After
    fun reset() {
        CurrentAccess.value = Access.NONE
    }

    private val cutter = Access(false, setOf(Feature.CUTTING, Feature.BOARD))

    @Test
    fun `the manager opens everything`() {
        listOf(Routes.FINANCE, Routes.USERS, Routes.SELF_TEST, Routes.SAMPLE_WORKSHOP, "customer_detail/5")
            .forEach { assertTrue(it, RouteAccess.canOpen(Access.MANAGER, it)) }
    }

    @Test
    fun `a cutter opens cutting but not sewing or money`() {
        assertTrue(RouteAccess.canOpen(cutter, Routes.CUTTING))
        assertTrue(RouteAccess.canOpen(cutter, Routes.BOARD))
        assertFalse(RouteAccess.canOpen(cutter, Routes.SEWING))
        assertFalse(RouteAccess.canOpen(cutter, Routes.FINANCE))
        assertFalse(RouteAccess.canOpen(cutter, Routes.LEDGER))
        assertFalse(RouteAccess.canOpen(cutter, Routes.CUSTOMERS))
    }

    @Test
    fun `everyone who logged in reaches home, settings and their own work`() {
        listOf(Routes.HOME, Routes.SETTINGS, Routes.GUIDE, Routes.MY_WORK, Routes.SEARCH, "order_detail/AJ-1")
            .forEach { assertTrue(it, RouteAccess.canOpen(Access.NONE, it)) }
    }

    @Test
    fun `pages not in the map belong to the manager only`() {
        val everything = Access(false, Feature.entries.toSet())
        assertFalse(RouteAccess.canOpen(everything, Routes.USERS))
        assertFalse(RouteAccess.canOpen(everything, Routes.SELF_TEST))
        assertFalse(RouteAccess.canOpen(everything, Routes.SAMPLE_WORKSHOP))
        assertFalse(RouteAccess.canOpen(everything, "some_future_page"))
    }

    @Test
    fun `a route with an argument needs the same tick as its page`() {
        val seller = Access(false, setOf(Feature.CUSTOMERS))
        assertTrue(RouteAccess.canOpen(seller, "customer_detail/12"))
        assertFalse(RouteAccess.canOpen(cutter, "customer_detail/12"))
    }

    @Test
    fun `a device set up with an old role keeps what it had`() {
        val sales = Access.forRole(UserRole.SALES)
        assertTrue(RouteAccess.canOpen(sales, Routes.FINISHED_SALES))
        assertTrue(RouteAccess.canOpen(sales, Routes.CUSTOMERS))
        assertFalse(RouteAccess.canOpen(sales, Routes.FINANCE))
        val sewing = Access.forRole(UserRole.SEWING)
        assertTrue(RouteAccess.canOpen(sewing, Routes.CUTTING))
        assertTrue(RouteAccess.canOpen(sewing, Routes.SEWING))
        assertFalse(RouteAccess.canOpen(sewing, Routes.REVIEW))
        assertTrue(Access.forRole(UserRole.MANAGER).isManager)
    }

    @Test
    fun `sensitive actions follow the ticks, and reset stays with the manager`() {
        CurrentAccess.value = Access(false, setOf(Feature.RETURN_SALE))
        assertTrue(Permissions.canReturnSale(UserRole.SALES))
        assertFalse(Permissions.canEditOrder(UserRole.SALES))
        assertFalse(Permissions.canResetData(UserRole.SALES))
        // مدیر همیشه — هر چه در CurrentAccess باشد
        assertTrue(Permissions.canEditOrder(UserRole.MANAGER))
    }

    @Test
    fun `nothing is allowed before anyone logs in`() {
        CurrentAccess.value = Access.NONE
        assertFalse(Permissions.canBackup(UserRole.SALES))
        assertFalse(Permissions.canSeeCustomers(UserRole.SALES))
    }

    @Test
    fun `users survive being stored and read back`() {
        val users = listOf(
            AppUser("u1", "احمد نوری", "pbkdf2\$x\$1\$aa\$bb", setOf(Feature.CUTTING, Feature.SEWING)),
            AppUser("u2", "زهرا\tاحمدی", "pbkdf2\$x\$1\$cc\$dd", setOf(Feature.REVIEW))
        )
        val back = UserStore.decode(UserStore.encode(users))
        assertEquals(2, back.size)
        assertEquals(users[0], back[0])
        // تب در نام سطر را نمی‌شکند
        assertEquals("زهرا احمدی", back[1].name)
        assertEquals(setOf(Feature.REVIEW), back[1].features)
    }

    @Test
    fun `a tick from a newer version or a broken line is ignored, not fatal`() {
        val raw = "u1\tعلی\thash\tCUTTING,FLYING\nbroken line\n\nu2\tبی‌رمز\t\tSEWING"
        val back = UserStore.decode(raw)
        assertEquals(1, back.size)
        assertEquals(setOf(Feature.CUTTING), back[0].features)
    }

    // ---------------- اشتراکِ کارگاه روی دستگاهِ اصلی ----------------

    private val requestTypes = listOf("ATTENDANCE_IN", "ATTENDANCE_OUT", "SEWING_DONE", "NOTE")

    @Test
    fun `the manager decides every worker request and runs the hub`() {
        requestTypes.forEach { assertTrue(it, WorkshopLinkAccess.canDecide(Access.MANAGER, it)) }
        assertTrue(WorkshopLinkAccess.seesRequests(Access.MANAGER))
        assertTrue(WorkshopLinkAccess.canRunHub(Access.MANAGER))
    }

    @Test
    fun `a seller at the hub computer cannot approve, reject, or touch the hub`() {
        // تا ۱.۹.۱ هر کسی که پشتِ دستگاهِ اصلی وارد شده بود تأیید می‌کرد —
        // و تأییدِ «تمام شد» کارمزد و سندِ دفترِ روزنامه می‌نشاند.
        val seller = Access(false, setOf(Feature.SALES, Feature.CUSTOMERS))
        requestTypes.forEach { assertFalse(it, WorkshopLinkAccess.canDecide(seller, it)) }
        assertFalse("no badge for someone who cannot act on it", WorkshopLinkAccess.seesRequests(seller))
        assertFalse(WorkshopLinkAccess.canRunHub(seller))
    }

    @Test
    fun `a tailor cannot approve a finished job - not even with the sewing tick`() {
        val tailor = Access(false, setOf(Feature.SEWING, Feature.CUTTING, Feature.BOARD))
        assertFalse(WorkshopLinkAccess.canDecide(tailor, "SEWING_DONE"))
        assertFalse(WorkshopLinkAccess.seesRequests(tailor))
        listOf(UserRole.SEWING, UserRole.SALES, UserRole.REVIEW, UserRole.PURCHASE).forEach { role ->
            val a = Access.forRole(role)
            requestTypes.forEach { assertFalse("$role / $it", WorkshopLinkAccess.canDecide(a, it)) }
            assertFalse("$role runs the hub", WorkshopLinkAccess.canRunHub(a))
        }
    }

    @Test
    fun `attendance and wages each approve their own kind of request`() {
        val clerk = Access(false, setOf(Feature.ATTENDANCE))
        assertTrue(WorkshopLinkAccess.canDecide(clerk, "ATTENDANCE_IN"))
        assertTrue(WorkshopLinkAccess.canDecide(clerk, "ATTENDANCE_OUT"))
        assertTrue(WorkshopLinkAccess.canDecide(clerk, "NOTE"))
        assertFalse("a finished job posts a wage", WorkshopLinkAccess.canDecide(clerk, "SEWING_DONE"))
        assertTrue(WorkshopLinkAccess.seesRequests(clerk))

        val payer = Access(false, setOf(Feature.SETTLE_WAGES))
        assertTrue(WorkshopLinkAccess.canDecide(payer, "SEWING_DONE"))
        assertTrue(WorkshopLinkAccess.canDecide(payer, "NOTE"))
        assertFalse(WorkshopLinkAccess.canDecide(payer, "ATTENDANCE_IN"))
        assertTrue(WorkshopLinkAccess.seesRequests(payer))
    }

    @Test
    fun `running the hub stays with the manager even with every tick`() {
        // روشن، خاموش، رمزِ تازه و قطعِ اشتراک — رمز تنها قفلِ قراردادِ ۱ است.
        val everything = Access(false, Feature.entries.toSet())
        assertFalse(WorkshopLinkAccess.canRunHub(everything))
        requestTypes.forEach { assertTrue(it, WorkshopLinkAccess.canDecide(everything, it)) }
        assertFalse("an unknown request type", WorkshopLinkAccess.canDecide(everything, "PAY_ME"))
    }

    @Test
    fun `the pending-requests alert reaches only those who can approve, and leads to the workshop link`() {
        // همان قاعدهٔ نشانِ نوارِ کنار: هشداری که کسی نمی‌تواند رویش کاری کند
        // فقط یاد می‌دهد مرکزِ هشدار را نخوانند.
        val a = pendingRequestsAlert(3, Access.MANAGER)
        requireNotNull(a)
        assertEquals("pending_requests", a.id)
        assertEquals(AlertSeverity.WARN, a.severity)
        assertEquals(Routes.WORKSHOP_LINK, a.route)
        assertTrue(a.title, a.title.contains("۳"))
        assertTrue(pendingRequestsAlert(1, Access(false, setOf(Feature.ATTENDANCE))) != null)
        assertTrue(pendingRequestsAlert(1, Access(false, setOf(Feature.SETTLE_WAGES))) != null)

        val auditor = Access(false, setOf(Feature.AUDIT, Feature.SALES))
        assertEquals("an auditor sees the alert centre but cannot approve", null, pendingRequestsAlert(5, auditor))
        assertEquals(null, pendingRequestsAlert(5, Access.NONE))
        assertEquals("nothing waiting, no alert", null, pendingRequestsAlert(0, Access.MANAGER))
    }

    @Test
    fun `nobody logged in decides nothing`() {
        requestTypes.forEach { assertFalse(it, WorkshopLinkAccess.canDecide(Access.NONE, it)) }
        assertFalse(WorkshopLinkAccess.seesRequests(Access.NONE))
        assertFalse(WorkshopLinkAccess.canRunHub(Access.NONE))
    }

    @Test
    fun `a person is never treated as the manager`() {
        Feature.entries.forEach { f ->
            val u = AppUser("u", "x", "h", setOf(f))
            assertNotEquals(UserRole.MANAGER, u.legacyRole())
            assertFalse(u.access.isManager)
        }
    }
}

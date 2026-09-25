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

    @Test
    fun `a person is never treated as the manager`() {
        Feature.entries.forEach { f ->
            val u = AppUser("u", "x", "h", setOf(f))
            assertNotEquals(UserRole.MANAGER, u.legacyRole())
            assertFalse(u.access.isManager)
        }
    }
}

package com.afghanjama

import com.afghanjama.ui.nav.DeskGroup
import com.afghanjama.ui.nav.DeskSection
import com.afghanjama.ui.nav.Routes
import com.afghanjama.ui.nav.allowedFor
import com.afghanjama.ui.nav.deskSectionForRoute
import com.afghanjama.ui.nav.icon
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.Feature
import com.afghanjama.ui.vm.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نوارِ کنارِ کامپیوتر: گروه‌ها، آیکن‌ها، و اینکه چه کسی چه می‌بیند.
 *
 * از ۱.۱۰ نوار گروه‌بندی شد و فهرستش از `:desktop` به `:core` آمد. دو چیز
 * نباید با این جابه‌جایی عوض شود: هر بند جایی در نوار دارد (گروه و آیکن)،
 * و **دسترسی‌ها بند به بند همان ۱.۹.۱ می‌مانند** — جدولِ پایین از
 * `Shell.kt`ِ ۱.۹.۱ رونویسی شده، نه از کدِ تازه.
 */
class SectionGroupTest {

    /** آنچه هر بند در ۱.۹.۱ می‌خواست. `null` = همه؛ `MANAGER` = فقط مدیر. */
    private sealed class Need {
        object All : Need()
        object ManagerOnly : Need()
        data class Tick(val f: Feature) : Need()
    }

    private val v191: Map<DeskSection, Need> = mapOf(
        // «وضعیت»ِ ۱.۹.۱ برای همه بود؛ «خانه» جایش را گرفت.
        DeskSection.Dashboard to Need.All,
        DeskSection.Assistant to Need.Tick(Feature.FINANCE),
        DeskSection.Home to Need.All,
        DeskSection.OrderSearch to Need.All,
        DeskSection.MyWork to Need.All,
        DeskSection.WorkshopLink to Need.All,
        DeskSection.QuoteCalc to Need.All,
        DeskSection.Guide to Need.All,
        DeskSection.Licence to Need.All,
        DeskSection.About to Need.All,
        // تازه در ۱.۱۰ — مثلِ گوشی (`RouteAccess.ALWAYS`) برای همه؛ هر کارتش
        // تیکِ خودش را می‌خواهد.
        DeskSection.Settings to Need.All,
        DeskSection.ActionCenter to Need.Tick(Feature.AUDIT),
        DeskSection.Audit to Need.Tick(Feature.AUDIT),
        DeskSection.Board to Need.Tick(Feature.BOARD),
        DeskSection.WorkshopLoad to Need.Tick(Feature.BOARD),
        DeskSection.Inventory to Need.Tick(Feature.ORDERS),
        DeskSection.ProductionOrder to Need.Tick(Feature.ORDERS),
        DeskSection.Cutting to Need.Tick(Feature.CUTTING),
        DeskSection.Sewing to Need.Tick(Feature.SEWING),
        DeskSection.Review to Need.Tick(Feature.REVIEW),
        DeskSection.DeliveryQueue to Need.Tick(Feature.DELIVERY),
        DeskSection.Warehouse to Need.Tick(Feature.WAREHOUSE),
        DeskSection.StockLedger to Need.Tick(Feature.WAREHOUSE),
        DeskSection.FinishedWarehouse to Need.Tick(Feature.SALES),
        DeskSection.NewSale to Need.Tick(Feature.SALES),
        DeskSection.Procurement to Need.Tick(Feature.PROCUREMENT),
        DeskSection.PurchasePlan to Need.Tick(Feature.PROCUREMENT),
        DeskSection.PurchaseReturn to Need.Tick(Feature.PROCUREMENT),
        DeskSection.DailyTrade to Need.Tick(Feature.FINANCE),
        DeskSection.Finance to Need.Tick(Feature.FINANCE),
        DeskSection.MoneyMove to Need.Tick(Feature.FINANCE),
        DeskSection.Documents to Need.Tick(Feature.FINANCE),
        DeskSection.Reports to Need.Tick(Feature.FINANCE),
        DeskSection.Analytics to Need.Tick(Feature.FINANCE),
        DeskSection.Journal to Need.Tick(Feature.FINANCE),
        DeskSection.Recurring to Need.Tick(Feature.FINANCE),
        DeskSection.Customers to Need.Tick(Feature.CUSTOMERS),
        DeskSection.FollowUp to Need.Tick(Feature.CUSTOMERS),
        DeskSection.Ledger to Need.Tick(Feature.LEDGER),
        DeskSection.Payroll to Need.Tick(Feature.PAYROLL),
        DeskSection.Performance to Need.Tick(Feature.PAYROLL),
        DeskSection.Attendance to Need.Tick(Feature.ATTENDANCE),
        DeskSection.MasterData to Need.Tick(Feature.MASTER),
        DeskSection.Backup to Need.Tick(Feature.BACKUP),
        DeskSection.ShopProfile to Need.ManagerOnly,
        DeskSection.SelfTest to Need.ManagerOnly,
        DeskSection.SampleShop to Need.ManagerOnly,
        DeskSection.Users to Need.ManagerOnly,
    )

    private fun expected(s: DeskSection, a: Access): Boolean = when (val n = v191.getValue(s)) {
        Need.All -> true
        Need.ManagerOnly -> a.isManager
        is Need.Tick -> a.has(n.f)
    }

    @Test
    fun `every section is in the 1-9-1 access table`() {
        assertEquals(DeskSection.entries.toSet(), v191.keys)
    }

    @Test
    fun `every section has a group and an icon`() {
        DeskSection.entries.forEach { s ->
            assertNotNull("${s.name} بی‌گروه", s.group)
            assertNotNull("${s.name} بی‌آیکن", s.icon)
            assertTrue("${s.name} بی‌نام", s.title.isNotBlank())
        }
    }

    @Test
    fun `every group has at least one section`() {
        DeskGroup.entries.forEach { g ->
            assertTrue("گروهِ خالی: ${g.title}", DeskSection.entries.any { it.group == g })
        }
    }

    /**
     * نوار سرگروه را وقتی می‌کشد که گروه عوض شود. اگر بندهای یک گروه
     * پشتِ‌سرِ‌هم نباشند یا گروه‌ها به ترتیبِ [DeskGroup] نیایند، یک سرگروه
     * دو بار در نوار می‌آمد.
     */
    @Test
    fun `sections of a group are contiguous and groups come in order`() {
        val seen = DeskSection.entries.map { it.group }.fold(mutableListOf<DeskGroup>()) { acc, g ->
            if (acc.lastOrNull() != g) acc.add(g)
            acc
        }
        assertEquals("ترتیبِ گروه‌ها در فهرست", DeskGroup.entries.toList(), seen)
    }

    @Test
    fun `titles are unique`() {
        val titles = DeskSection.entries.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
    }

    /**
     * نامِ بند در نوار همان عنوانِ صفحه است (و همان کاشیِ خانهٔ گوشی) — تا
     * ۱.۱۱ این یازده تا دو نام داشتند و هر دو هم‌زمان دیده می‌شدند.
     */
    @Test
    fun `sidebar titles match the screen titles`() {
        val screen = mapOf(
            DeskSection.ActionCenter to "مرکز هشدار",
            DeskSection.Inventory to "خط تولید",
            DeskSection.OrderSearch to "پیگیری سفارش",
            DeskSection.Procurement to "خرید مواد خام",
            DeskSection.PurchaseReturn to "برگشت از خرید",
            DeskSection.Customers to "مشتریان",
            DeskSection.Payroll to "حقوق کارکنان",
            DeskSection.Performance to "کارنامهٔ کارکنان",
            DeskSection.Ledger to "دفتر کل",
            DeskSection.Audit to "رویدادها",
            DeskSection.MasterData to "اطلاعات پایه",
        )
        screen.forEach { (s, title) -> assertEquals(s.name, title, s.title) }
    }

    /** همان جایی که کارفرما «کارگاهِ وصل» را پیدا می‌کند. */
    @Test
    fun `workshop sharing stays in today, among the first three`() {
        assertEquals(DeskGroup.TODAY, DeskSection.WorkshopLink.group)
        assertTrue(DeskSection.entries.indexOf(DeskSection.WorkshopLink) < 3)
        // از ۱.۱۲ صفحهٔ اول داشبورد است و «خانه» درست پس از آن.
        assertEquals(DeskSection.Dashboard, DeskSection.entries.first())
        assertEquals(DeskSection.Home, DeskSection.entries[1])
    }

    @Test
    fun `access is unchanged from 1-9-1 for every role`() {
        UserRole.entries.forEach { role ->
            val a = Access.forRole(role)
            DeskSection.entries.forEach { s ->
                assertEquals("$role → ${s.name}", expected(s, a), s.allowedFor(a))
            }
        }
    }

    /** هر تیکِ تنها، و دسترسیِ خالی — همان قاعده، بند به بند. */
    @Test
    fun `access is unchanged for single ticks and for no ticks`() {
        val cases = Feature.entries.map { Access(false, setOf(it)) } + Access.NONE
        cases.forEach { a ->
            DeskSection.entries.forEach { s ->
                assertEquals("${a.features} → ${s.name}", expected(s, a), s.allowedFor(a))
            }
        }
    }

    /**
     * هشدارِ پشتیبان به «تنظیمات» می‌برد. تا ۱.۹ این مسیر روی کامپیوتر
     * `null` بود و کلیک هیچ نمی‌کرد.
     */
    @Test
    fun `alert routes reach a section on the computer`() {
        assertEquals(DeskSection.Settings, deskSectionForRoute(Routes.SETTINGS))
        assertEquals(DeskSection.WorkshopLink, deskSectionForRoute(Routes.WORKSHOP_LINK))
        assertEquals(DeskSection.MoneyMove, deskSectionForRoute(Routes.PAY))
        assertEquals(null, deskSectionForRoute("no_such_route"))
    }
}

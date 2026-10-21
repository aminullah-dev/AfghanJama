package com.afghanjama

import com.afghanjama.prefs.Settings
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.AppUser
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.CurrentAccess
import com.afghanjama.ui.vm.Feature
import com.afghanjama.ui.vm.UserRole
import com.afghanjama.ui.vm.UserStore
import com.afghanjama.ui.vm.LoginPerson
import com.afghanjama.ui.vm.PinLockout
import com.afghanjama.ui.vm.UsersViewModel
import com.afghanjama.ui.vm.roleLabel
import com.afghanjama.util.PinHash
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ورود با رمزِ هر نفر — رمز می‌گوید کیست و چه می‌بیند.
 *
 * تا دیروز یک رمز برای دستگاه بود و نقش را هر کس در صفحهٔ ورود خودش
 * انتخاب می‌کرد؛ پس «دوخت» با انتخابِ «مدیر» همه‌کاره می‌شد.
 */
class PersonLoginTest {

    private class MemSettings : Settings {
        private val s = mutableMapOf<String, String>()
        private val b = mutableMapOf<String, Boolean>()
        override fun getString(file: String, key: String, def: String) = s["$file/$key"] ?: def
        override fun putString(file: String, key: String, value: String) { s["$file/$key"] = value }
        override fun getBoolean(file: String, key: String, def: Boolean) = b["$file/$key"] ?: def
        override fun putBoolean(file: String, key: String, value: Boolean) { b["$file/$key"] = value }
        override fun getLong(file: String, key: String, def: Long) = def
        override fun putLong(file: String, key: String, value: Long) = Unit
        override fun remove(file: String, key: String) { s.remove("$file/$key"); b.remove("$file/$key") }
    }

    private val cutter = AppUser("u1", "سمیع", PinHash.hash("2222"), setOf(Feature.CUTTING))

    private fun device(): MemSettings = MemSettings().also {
        it.putString("auth_prefs", "pin", PinHash.hash("1111"))
        it.putString("auth_prefs", "role", UserRole.MANAGER.name)
        UserStore.save(it, listOf(cutter))
    }

    @After
    fun reset() {
        CurrentAccess.value = Access.NONE
    }

    @Test
    fun `the manager pin opens everything`() {
        val vm = AuthViewModel(device())
        vm.login("1111")
        assertTrue(vm.ui.value.isLoggedIn)
        assertNull(vm.ui.value.userId)
        assertTrue(vm.ui.value.access.isManager)
    }

    @Test
    fun `a person's pin logs that person in with only their ticks`() {
        val vm = AuthViewModel(device())
        vm.login("2222")
        val ui = vm.ui.value
        assertTrue(ui.isLoggedIn)
        assertEquals("u1", ui.userId)
        assertEquals("سمیع", ui.userName)
        assertFalse(ui.access.isManager)
        assertTrue(ui.access.has(Feature.CUTTING))
        assertFalse(ui.access.has(Feature.FINANCE))
        assertEquals(ui.access, CurrentAccess.value)
    }

    @Test
    fun `a wrong pin logs nobody in`() {
        val vm = AuthViewModel(device())
        vm.login("9999")
        assertFalse(vm.ui.value.isLoggedIn)
        assertTrue(vm.ui.value.isError)
    }

    @Test
    fun `reopening the app keeps the same person, and a deleted person is logged out`() {
        val s = device()
        AuthViewModel(s).login("2222")
        assertEquals("u1", AuthViewModel(s).ui.value.userId)

        UserStore.save(s, emptyList())
        val after = AuthViewModel(s).ui.value
        assertFalse(after.isLoggedIn)
        assertEquals(Access.NONE, CurrentAccess.value)
    }

    @Test
    fun `logging out forgets the person`() {
        val s = device()
        val vm = AuthViewModel(s)
        vm.login("2222")
        vm.logout()
        assertFalse(vm.ui.value.isLoggedIn)
        assertNull(vm.ui.value.userId)
        assertEquals(Access.NONE, CurrentAccess.value)
        // و ورودِ بعدی با رمزِ مدیر، مدیر است نه همان نفر
        vm.login("1111")
        assertTrue(vm.ui.value.access.isManager)
    }

    @Test
    fun `a person changes their own pin, never to someone else's`() {
        val s = device()
        val vm = AuthViewModel(s)
        vm.login("2222")
        vm.changePin("2222", "1111")          // رمزِ مدیر
        assertTrue(vm.ui.value.isError)
        vm.changePin("2222", "3333")
        assertFalse(vm.ui.value.isError)
        assertTrue(PinHash.verify("3333", UserStore.find(s, "u1")!!.pinHash))
        // رمزِ دستگاه دست نخورد
        assertTrue(AuthViewModel.verifyPin(s, "1111"))
    }

    // ─────────────────────── کاشیِ هر نفر (۱.۱۲)

    /** دفترِ خالی: هر جریانی فهرستِ خالی می‌دهد (برای پیشنهادِ نامِ کاربران). */
    private fun emptyRepo(): com.afghanjama.data.repo.Repo {
        val loader = com.afghanjama.data.Db::class.java.classLoader
        val dao = java.lang.reflect.InvocationHandler { _, m, _ ->
            if (kotlinx.coroutines.flow.Flow::class.java.isAssignableFrom(m.returnType)) kotlinx.coroutines.flow.flowOf(emptyList<Any>()) else null
        }
        val db = java.lang.reflect.Proxy.newProxyInstance(loader, arrayOf(com.afghanjama.data.Db::class.java)) { _, m, _ ->
            if (m.name.endsWith("Dao")) java.lang.reflect.Proxy.newProxyInstance(loader, arrayOf(m.returnType), dao) else null
        } as com.afghanjama.data.Db
        return com.afghanjama.data.repo.Repo(db)
    }

    /** ساعتِ دستی، تا قفل بی‌انتظارِ واقعی آزموده شود. */
    private var clock = 1_000_000L

    private fun vmOf(s: Settings) = AuthViewModel(s, clock = { clock })

    @Test
    fun `the login screen lists the owner first, then each person with their role`() {
        val s = device().also { it.putString("auth_prefs", "user_name", "کریم") }
        val people = vmOf(s).ui.value.people
        assertEquals(listOf(LoginPerson.OWNER_ID, "u1"), people.map { it.id })
        assertEquals("کریم", people[0].name)
        assertEquals("مدیر", people[0].role)
        assertTrue(people[0].isManager)
        assertEquals("سمیع", people[1].name)
        assertFalse(people[1].isManager)
    }

    @Test
    fun `a tile opens only with that person's own pin`() {
        val vm = vmOf(device())
        vm.select("u1")
        vm.loginAs("u1", "1111")              // رمزِ مدیر روی کاشیِ سمیع
        assertFalse(vm.ui.value.isLoggedIn)
        assertTrue(vm.ui.value.isError)
        vm.loginAs("u1", "2222")
        assertTrue(vm.ui.value.isLoggedIn)
        assertEquals("u1", vm.ui.value.userId)
        assertFalse(vm.ui.value.access.isManager)
    }

    @Test
    fun `the owner tile opens with the owner pin and is the manager`() {
        val vm = vmOf(device())
        vm.loginAs(LoginPerson.OWNER_ID, "2222") // رمزِ سمیع روی کاشیِ مدیر
        assertFalse(vm.ui.value.isLoggedIn)
        vm.loginAs(LoginPerson.OWNER_ID, "1111")
        assertTrue(vm.ui.value.isLoggedIn)
        assertTrue(vm.ui.value.access.isManager)
    }

    @Test
    fun `five wrong pins lock the tile, the lock survives a restart, and grows`() {
        val s = device()
        val vm = vmOf(s)
        repeat(PinLockout.FREE_TRIES - 1) { vm.loginAs("u1", "9999") }
        assertEquals(0L, vm.ui.value.lockedUntil)
        vm.loginAs("u1", "9999")
        val until = vm.ui.value.lockedUntil
        assertEquals(clock + PinLockout.FIRST_LOCK_MS, until)

        // بسته و باز کردنِ برنامه قفل را باز نمی‌کند — حتی با رمزِ درست.
        val again = vmOf(s)
        again.loginAs("u1", "2222")
        assertFalse(again.ui.value.isLoggedIn)
        assertTrue(again.ui.value.isError)

        // پس از قفل، اشتباهِ بعدی دو برابر.
        clock = until + 1
        again.loginAs("u1", "9999")
        assertEquals(clock + 2 * PinLockout.FIRST_LOCK_MS, again.ui.value.lockedUntil)
        clock += 2 * PinLockout.FIRST_LOCK_MS + 1
        again.loginAs("u1", "2222")
        assertTrue(again.ui.value.isLoggedIn)
        // ورودِ درست شمار را صفر کرد.
        assertEquals(0, PinLockout.state(s, "u1").fails)
    }

    @Test
    fun `one locked tile does not lock the others`() {
        val vm = vmOf(device())
        repeat(PinLockout.FREE_TRIES) { vm.loginAs("u1", "9999") }
        assertTrue(vm.ui.value.lockedUntil > clock)
        vm.loginAs(LoginPerson.OWNER_ID, "1111")
        assertTrue(vm.ui.value.isLoggedIn)
    }

    @Test
    fun `the lock never exceeds its cap, even when the clock is moved back`() {
        assertEquals(PinLockout.MAX_LOCK_MS, PinLockout.lockFor(40))
        val s = device()
        s.putString("auth_prefs", "fails_u1", "9|${clock + 365L * 86_400_000L}")
        assertEquals(clock + PinLockout.MAX_LOCK_MS, PinLockout.lockedUntil(s, "u1", clock))
    }

    @Test
    fun `the pin-only path is throttled too`() {
        val vm = vmOf(device())
        repeat(PinLockout.FREE_TRIES) { vm.login("9999") }
        vm.login("1111")
        assertFalse(vm.ui.value.isLoggedIn)
    }

    @Test
    fun `logout returns to the list of people`() {
        val vm = vmOf(device())
        vm.select("u1")
        vm.loginAs("u1", "2222")
        vm.logout()
        val ui = vm.ui.value
        assertFalse(ui.isLoggedIn)
        assertNull(ui.selectedId)
        assertEquals(2, ui.people.size)
        assertEquals(Access.NONE, CurrentAccess.value)

        // قفلِ خودکار هم «خروج» است: اگر کسی کاشی‌اش را زده و رفته، نفرِ بعدی
        // فهرست را می‌بیند، نه صفحهٔ رمزِ او را.
        vm.select("u1")
        vm.logout()
        assertNull(vm.ui.value.selectedId)
    }

    @Test
    fun `the first person on a fresh device is the manager, no role is asked`() {
        val s = MemSettings()
        // نقشی که صفحهٔ ورودِ ۱.۱۱ پیش از رمز می‌گذاشت، دیگر اثری ندارد.
        s.putString("auth_prefs", "role", UserRole.SALES.name)
        val vm = vmOf(s)
        assertTrue(vm.ui.value.people.isEmpty())
        vm.setUserName("کریم")
        vm.setupPin("1379")
        assertTrue(vm.ui.value.isLoggedIn)
        assertTrue(vm.ui.value.access.isManager)
        assertEquals(UserRole.MANAGER, vm.ui.value.role)
        // و پس از آن، رمزِ دستگاه دوباره «گذاشته» نمی‌شود.
        vm.logout()
        vm.setupPin("0000")
        assertTrue(vm.ui.value.isError)
        assertTrue(AuthViewModel.verifyPin(s, "1379"))
    }

    @Test
    fun `an old device set up with another role keeps it, nobody silently becomes manager`() {
        val s = device().also { it.putString("auth_prefs", "role", UserRole.SEWING.name) }
        val vm = vmOf(s)
        assertEquals("کارگر", vm.ui.value.people.first().role)
        vm.loginAs(LoginPerson.OWNER_ID, "1111")
        assertFalse(vm.ui.value.access.isManager)
    }

    @Test
    fun `only a manager can change someone's role`() {
        val s = device()
        // کسی که مدیر نیست — صفحه را هم نمی‌بیند، ولی قاعده در خودِ مدل است.
        val notManager = UsersViewModel(s, emptyRepo(), access = { Access(false, setOf(Feature.MASTER)) })
        assertFalse(notManager.save("u1", "سمیع", "", setOf(Feature.FINANCE)))
        assertEquals(UsersViewModel.NOT_MANAGER, notManager.message.value)
        assertEquals(setOf(Feature.CUTTING), UserStore.find(s, "u1")!!.features)
        notManager.delete("u1")
        assertTrue(UserStore.find(s, "u1") != null)

        val manager = UsersViewModel(s, emptyRepo(), access = { Access.MANAGER })
        assertTrue(manager.save("u1", "سمیع", "", Feature.PRESETS.first { it.first == "خیاط" }.second))
        assertEquals("خیاط", UserStore.find(s, "u1")!!.roleLabel)
        // و کاشیِ ورود نقشِ تازه را نشان می‌دهد.
        assertEquals("خیاط", vmOf(s).ui.value.people.first { it.id == "u1" }.role)
    }

    @Test
    fun `a person's role follows their ticks`() {
        fun r(vararg f: Feature) = AppUser("x", "x", "h", f.toSet()).roleLabel
        assertEquals("فروشنده", r(Feature.SALES, Feature.CUSTOMERS))
        assertEquals("برشکار", r(Feature.CUTTING, Feature.BOARD))
        assertEquals("حسابدار", r(Feature.FINANCE, Feature.SALES))
        assertEquals("کارگر", r(Feature.SEWING, Feature.REVIEW))
        assertEquals("کارمند", r(Feature.AUDIT))
    }
}

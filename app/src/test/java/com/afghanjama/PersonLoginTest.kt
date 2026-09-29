package com.afghanjama

import com.afghanjama.prefs.Settings
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.AppUser
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.CurrentAccess
import com.afghanjama.ui.vm.Feature
import com.afghanjama.ui.vm.UserRole
import com.afghanjama.ui.vm.UserStore
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
}

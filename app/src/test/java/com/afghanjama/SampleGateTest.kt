package com.afghanjama

import com.afghanjama.data.PersonEdit
import com.afghanjama.data.SampleWorkshop
import com.afghanjama.data.SampleWorkshop.Gate
import com.afghanjama.prefs.Settings
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.util.PinHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * شروعِ اجباری با کارگاهِ نمونه، و قفلش.
 *
 * کارگاهِ خالی اول نمونه را می‌سازد؛ از لحظه‌ای که کارگاه چیزی دارد،
 * ساختنِ دوباره رمزِ ورود می‌خواهد — چون دوباره ساختن کارگاه را به نمونه
 * برنمی‌گرداند، نسخهٔ دیگری از دادهٔ ساختگی را روی کارِ واقعی می‌ریزد.
 */
class SampleGateTest {

    /** همان فایل و کلیدی که `AuthViewModel` و آزمونِ شبیه‌ساز می‌نویسند. */
    private class MemSettings(pinStored: String) : Settings {
        private val strings = mutableMapOf("auth_prefs/pin" to pinStored)
        override fun getString(file: String, key: String, def: String) = strings["$file/$key"] ?: def
        override fun putString(file: String, key: String, value: String) { strings["$file/$key"] = value }
        override fun getBoolean(file: String, key: String, def: Boolean) = def
        override fun putBoolean(file: String, key: String, value: Boolean) = Unit
        override fun getLong(file: String, key: String, def: Long) = def
        override fun putLong(file: String, key: String, value: Long) = Unit
        override fun remove(file: String, key: String) { strings.remove("$file/$key") }
    }

    @Test
    fun `an empty workshop must start from the sample, without a pin`() {
        assertEquals(Gate.FIRST_RUN, SampleWorkshop.gate(hasData = false, unlocked = false))
    }

    @Test
    fun `a workshop with data is locked until the pin is given`() {
        assertEquals(Gate.LOCKED, SampleWorkshop.gate(hasData = true, unlocked = false))
        assertEquals(Gate.OPEN, SampleWorkshop.gate(hasData = true, unlocked = true))
    }

    @Test
    fun `nothing is decided before the workshop has been asked`() {
        // نه دروازهٔ شروع و نه دکمه: پیش از جواب، هیچ‌کدام درست نیست.
        assertEquals(Gate.CHECKING, SampleWorkshop.gate(hasData = null, unlocked = false))
        assertEquals(Gate.CHECKING, SampleWorkshop.gate(hasData = null, unlocked = true))
    }

    @Test
    fun `the unlock pin is the device login pin`() {
        val s = MemSettings(PinHash.hash("1379"))
        assertTrue(AuthViewModel.verifyPin(s, "1379"))
        assertFalse(AuthViewModel.verifyPin(s, "1378"))
        assertFalse(AuthViewModel.verifyPin(s, ""))
    }

    @Test
    fun `a pin stored by an older version still unlocks`() {
        // گوشیِ قدیمی رمز را خام نوشته بود؛ صفحهٔ ورود بازش می‌کند، قفلِ
        // نمونه هم باید.
        assertTrue(AuthViewModel.verifyPin(MemSettings("1379"), "1379"))
    }

    @Test
    fun `personalising people explains why a rename or delete was refused`() {
        assertEquals("", PersonEdit.DONE.message("مشتری"))
        assertEquals("این نام را مشتریِ دیگری دارد.", PersonEdit.NAME_TAKEN.message("مشتری"))
        // آدمِ کارگاهِ نمونه تا دفتر پاک نشده سابقه دارد؛ پیام باید راهش را بگوید.
        assertTrue(PersonEdit.HAS_HISTORY.message("کارمند").contains("پاک‌کردن کارها و حساب‌ها"))
    }

    @Test
    fun `with no pin set nothing unlocks`() {
        assertFalse(AuthViewModel.verifyPin(MemSettings(""), ""))
        assertFalse(AuthViewModel.verifyPin(MemSettings(""), "1234"))
    }
}

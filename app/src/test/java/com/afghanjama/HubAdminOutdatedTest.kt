package com.afghanjama

import com.afghanjama.ui.vm.HubAdminViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نشانِ «به‌روز کنید» کنارِ هر گوشی در «دستگاه‌ها».
 *
 * **خطری که این آزمون نگه می‌دارد:** مقایسهٔ متنی «1.9.1» را از «1.11.0»
 * بزرگ‌تر می‌داند، و آن روز همهٔ گوشی‌های ۱.۹ بی نشان می‌ماندند و گوشی‌های
 * تازه نشانِ «به‌روز کنید» می‌گرفتند.
 */
class HubAdminOutdatedTest {

    private fun old(phone: String, here: String = "1.11.0") = HubAdminViewModel.outdated(phone, here)

    @Test
    fun `numbers are compared as numbers, not text`() {
        assertTrue(old("1.9.1"))
        assertTrue(old("1.10.0"))
        assertFalse(old("1.11.0"))
        assertFalse(old("1.12.0"))
        assertFalse(old("2.0.0"))
    }

    @Test
    fun `a test-build suffix does not make a phone look outdated`() {
        assertFalse(old("1.11.0-debug"))
        assertTrue(old("1.10.0-debug"))
    }

    @Test
    fun `an unreadable version never shows the badge`() {
        assertFalse(old(""))
        assertFalse(old("نامعلوم"))
        assertFalse(old("1.x.0"))
        assertFalse(old("1.10.0", here = "dev"))
    }

    @Test
    fun `a shorter version is older only when everything else matches`() {
        assertTrue(old("1.11", here = "1.11.0"))
        assertFalse(old("1.12", here = "1.11.0"))
    }
}

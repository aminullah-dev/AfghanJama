package com.afghanjama

import com.afghanjama.platform.SystemActions
import com.afghanjama.platform.messageThen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «پیام فرستاده شد» فقط وقتی ثبت می‌شود که پیام واقعاً رفته باشد.
 *
 * تا ۱.۹ روی کامپیوتر متن بی‌صدا کپی می‌شد و پیگیریِ طلب همان را «پیام
 * فرستاده شد» ثبت می‌کرد — بدهکار هیچ پیامی نگرفته بود و از فهرستِ امروز
 * پایین می‌رفت.
 */
class MessageThenTest {

    private class Fake(private val opens: Boolean?) : SystemActions {
        var shared = 0
        override fun shareText(title: String, text: String) { shared++ }
        override fun dial(phone: String) = Unit
        override fun messageTo(phone: String, text: String, title: String): Boolean =
            opens ?: super.messageTo(phone, text, title)
    }

    @Test
    fun `nothing is recorded when no app opened`() {
        var recorded = 0
        val sent = Fake(opens = false).messageThen("0700123456", "متن", "یادآوری") { recorded++ }
        assertFalse(sent)
        assertEquals(0, recorded)
    }

    @Test
    fun `it is recorded once when the app opened`() {
        var recorded = 0
        val sent = Fake(opens = true).messageThen("0700123456", "متن", "یادآوری") { recorded++ }
        assertTrue(sent)
        assertEquals(1, recorded)
    }

    /** پیش‌فرضِ گوشی و آیفون: پنجرهٔ اشتراک، مثلِ ۱.۹. */
    @Test
    fun `the default opens the share sheet and counts as sent`() {
        val f = Fake(opens = null)
        var recorded = 0
        assertTrue(f.messageThen("0700123456", "متن", "یادآوری") { recorded++ })
        assertEquals(1, f.shared)
        assertEquals(1, recorded)
    }
}

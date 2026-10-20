package com.afghanjama

import com.afghanjama.lan.HostAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * وارسیِ نشانیِ گوشیِ اصلی.
 *
 * **از یک گزارشِ واقعی آمد:** کاربر `10.0.0101` را وارد کرده بود — یک
 * نقطه کم — و اپ پیام داد «مطمئن شوید هر دو گوشی روی وای‌فای کارگاه‌اند».
 * کاربر رفت سراغِ وای‌فای، در حالی که ایراد در همان یک نقطه بود.
 *
 * بدتر: همان نشانی روی دو سکو دو رفتار دارد. جاوای رومیزی شکلِ
 * سه‌بخشیِ قدیمی را می‌بخشد، اندروید نمی‌بخشد. یعنی چیزی که روی ویندوز
 * کار می‌کرد روی گوشی نمی‌کرد.
 */
class HostAddressTest {

    private fun bad(raw: String): String {
        val r = HostAddress.check(raw)
        assertTrue("«$raw» باید رد شود ولی قبول شد", r is HostAddress.Result.Bad)
        return (r as HostAddress.Result.Bad).reason
    }

    @Test
    fun `the reported address is rejected with a useful reason`() {
        val why = bad("10.0.0101")
        assertTrue("پیام باید از بخش‌ها بگوید، نه از وای‌فای: $why", why.contains("بخش"))
        assertNull(HostAddress.normalize("10.0.0101"))
    }

    @Test
    fun `a correct address passes`() {
        assertEquals("10.0.0.101", HostAddress.normalize("10.0.0.101"))
        assertEquals("192.168.1.7", HostAddress.normalize("192.168.1.7"))
        assertEquals("172.16.0.1", HostAddress.normalize("172.16.0.1"))
        assertEquals("172.31.255.254", HostAddress.normalize("172.31.255.254"))
        assertEquals("100.64.0.9", HostAddress.normalize("100.64.0.9"))
    }

    /**
     * مرزِ متنِ ساده حالا در کد است، نه در `network_security_config.xml`.
     *
     * آن فایل متنِ ساده را برای همه باز کرد، چون سه «دامنه»ی IPاش هرگز
     * با نشانیِ واقعیِ هیچ دستگاهی جور نمی‌شد. پس این وارسی تنها چیزی است
     * که نمی‌گذارد مشتریِ کارگاه به نشانیِ اینترنتی `http` بزند.
     * `0.0.0.0` و `255.255.255.255` تا دیروز «درست» بودند؛ هیچ‌کدام نشانیِ
     * دستگاهِ اصلی نیست.
     */
    @Test
    fun `addresses outside the workshop network are refused`() {
        for (raw in listOf(
            "8.8.8.8", "0.0.0.0", "255.255.255.255", "127.0.0.1", "169.254.1.1",
            "172.15.0.1", "172.32.0.1", "100.63.255.255", "100.128.0.1", "192.169.1.1",
        )) {
            assertEquals(raw, HostAddress.NOT_WORKSHOP, bad(raw))
            assertFalse(raw, HostAddress.isPrivate(raw))
        }
    }

    @Test
    fun `private ranges are exactly the workshop ones`() {
        for (raw in listOf(
            "10.0.0.0", "10.255.255.255", "172.16.0.0", "172.31.255.255",
            "192.168.0.0", "192.168.255.255", "100.64.0.0", "100.127.255.255",
        )) {
            assertTrue(raw, HostAddress.isPrivate(raw))
        }
        // نه شکلِ نیمه، نه صفرِ ابتدایی، نه نامِ میزبان
        for (raw in listOf("192.168.1", "192.168.01.7", "kargah.local", "", "10.0.0.256")) {
            assertFalse(raw, HostAddress.isPrivate(raw))
        }
    }

    /** loopback شبکهٔ کارگاه نیست؛ فقط آزمونِ دودی با پرچمِ جدا به آن می‌رسد. */
    @Test
    fun `loopback is recognised but never private`() {
        assertTrue(HostAddress.isLoopback("127.0.0.1"))
        assertFalse(HostAddress.isPrivate("127.0.0.1"))
        assertFalse(HostAddress.isLoopback("10.0.0.1"))
    }

    /**
     * گوشیِ اصلی نشانی را با رقمِ فارسی نشان می‌دهد. کاربری که همان را
     * تایپ کند باید کار کند — این تله را خودمان ساخته بودیم.
     */
    @Test
    fun `persian digits are accepted`() {
        assertEquals("10.0.0.101", HostAddress.normalize("۱۰.۰.۰.۱۰۱"))
        assertEquals("192.168.1.7", HostAddress.normalize("۱۹۲.۱۶۸.۱.۷"))
    }

    @Test
    fun `noise around the address is stripped`() {
        assertEquals("10.0.0.101", HostAddress.normalize("  http://10.0.0.101/  "))
        assertEquals("10.0.0.101", HostAddress.normalize("10.0.0.101:8080"))
        assertEquals("10.0.0.101", HostAddress.normalize("10. 0.0 .101"))
    }

    /**
     * صفرِ ابتدایی عمداً رد می‌شود: بعضی کتابخانه‌ها آن را مبنای هشت
     * می‌خوانند و `0101` می‌شود ۶۵ — یعنی اپ بی‌صدا به دستگاهِ دیگری
     * وصل می‌شود. سکوت اینجا از خطا بدتر است.
     */
    @Test
    fun `leading zero is refused, not guessed`() {
        val why = bad("10.0.0.0101")
        assertTrue("پیام باید صفرِ اضافه را نام ببرد: $why", why.contains("صفر"))
    }

    @Test
    fun `out of range and non numeric parts are refused`() {
        assertTrue(bad("10.0.0.256").contains("۲۵۵"))
        assertTrue(bad("10.0.0.abc").isNotBlank())
        assertTrue(bad("").isNotBlank())
        assertTrue(bad("10.0.0.").isNotBlank())
        assertTrue(bad("10.0.0.1.5").isNotBlank())
    }

    /** پاک‌سازیِ لحظهٔ تایپ نباید چیزی را رد کند — فقط تمیز کند. */
    @Test
    fun `clean never rejects, only tidies`() {
        assertEquals("10.0", HostAddress.clean("۱۰.۰"))
        assertEquals("", HostAddress.clean("   "))
    }
}

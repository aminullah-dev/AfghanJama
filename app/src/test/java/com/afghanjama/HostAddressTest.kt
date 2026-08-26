package com.afghanjama

import com.afghanjama.lan.HostAddress
import org.junit.Assert.assertEquals
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
        assertEquals("0.0.0.0", HostAddress.normalize("0.0.0.0"))
        assertEquals("255.255.255.255", HostAddress.normalize("255.255.255.255"))
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

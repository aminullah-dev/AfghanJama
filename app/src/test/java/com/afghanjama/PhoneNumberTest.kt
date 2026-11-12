package com.afghanjama

import com.afghanjama.util.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * شمارهٔ تلفن برای واتساپ و `tel:` روی کامپیوتر.
 *
 * پیامِ طلب با همین شماره باز می‌شود؛ شماره‌ای که اشتباه «درست» شود یعنی
 * یادآوریِ بدهی به آدمِ دیگری برود. پس هر چیزی که روشن نیست `null` است و
 * برنامه به کپی برمی‌گردد.
 */
class PhoneNumberTest {

    @Test
    fun `an Afghan mobile written with a leading zero gets +93`() {
        assertEquals("+93700123456", PhoneNumber.international("0700123456"))
        assertEquals("+93799123456", PhoneNumber.international("079 912 3456"))
        assertEquals("+93781234567", PhoneNumber.international("078-123-4567"))
    }

    @Test
    fun `Persian and Arabic-Indic digits are read`() {
        assertEquals("+93700123456", PhoneNumber.international("۰۷۰۰۱۲۳۴۵۶"))
        assertEquals("+93700123456", PhoneNumber.international("٠٧٠٠١٢٣٤٥٦"))
        assertEquals("+93700123456", PhoneNumber.international("۰۷۰۰ ۱۲۳ ۴۵۶"))
    }

    @Test
    fun `direction marks and brackets around the number do not matter`() {
        assertEquals("+93700123456", PhoneNumber.international("‏0700123456‎"))
        assertEquals("+93700123456", PhoneNumber.international("(0700) 123 456"))
    }

    @Test
    fun `international forms are kept`() {
        assertEquals("+93700123456", PhoneNumber.international("+93 700 123 456"))
        assertEquals("+93700123456", PhoneNumber.international("0093700123456"))
        assertEquals("+93700123456", PhoneNumber.international("93700123456"))
        assertEquals("+989121234567", PhoneNumber.international("+98 912 123 4567"))
    }

    /**
     * «+93 (0) 7…» — شکلِ کارت‌ویزیت. تا ۱.۱۰ صفرِ داخل پرانتز می‌ماند و
     * «+930700123456»ی نادرست ساخته می‌شد؛ واتساپ چنین شماره‌ای را باز
     * نمی‌کند یا به آدمِ دیگری می‌رسد.
     */
    @Test
    fun `the (0) trunk prefix after the country code is dropped`() {
        assertEquals("+93700123456", PhoneNumber.international("+93 (0) 700 123 456"))
        assertEquals("+93700123456", PhoneNumber.international("+93(0)700123456"))
        assertEquals("+93700123456", PhoneNumber.international("+93 ( 0 ) 700-123-456"))
        assertEquals("+93700123456", PhoneNumber.international("0093 (0) 700 123 456"))
        assertEquals("+93700123456", PhoneNumber.international("93 (0) 700 123 456"))
        assertEquals("+93700123456", PhoneNumber.international("+۹۳ (۰) ۷۰۰ ۱۲۳ ۴۵۶"))
        assertEquals("+442079460958", PhoneNumber.international("+44 (0) 20 7946 0958"))
        assertEquals("93700123456", PhoneNumber.forWhatsApp("+93 (0) 700 123 456"))
    }

    @Test
    fun `an Afghan trunk zero kept after +93 without brackets is dropped too`() {
        assertEquals("+93700123456", PhoneNumber.international("+93 0700 123 456"))
        assertEquals("+93700123456", PhoneNumber.international("0093 0700123456"))
        assertEquals("+93202101234", PhoneNumber.international("+93 020 210 1234"))
    }

    @Test
    fun `brackets that are not a lone trunk zero are not mistaken for one`() {
        // «(0700)» پیش‌شماره است، نه صفرِ داخلی.
        assertEquals("+93700123456", PhoneNumber.international("(0700) 123 456"))
        // «(07)» پس از کدِ کشور: رقم‌ها می‌مانند، و همان قاعدهٔ «+930…» رسیدگی می‌کند.
        assertEquals("+93700123456", PhoneNumber.international("+93 (07) 00123456"))
        // «(00)» صفرِ داخلی نیست، و «+93» با ده رقم پس از آن شمارهٔ افغانستان نیست.
        assertNull(PhoneNumber.international("+93 (00) 700 123 456"))
        assertNull(PhoneNumber.international("+93 7001234567"))
        // «(0)» بی کدِ کشور پیش از آن: صفر رقمی از شماره است و شماره همان می‌ماند.
        assertEquals("+93700123456", PhoneNumber.international("(0) 700 123 456"))
    }

    @Test
    fun `a mobile without its leading zero is still Afghan`() {
        assertEquals("+93700123456", PhoneNumber.international("700123456"))
    }

    @Test
    fun `a Kabul landline also becomes international`() {
        assertEquals("+93202101234", PhoneNumber.international("020 210 1234"))
    }

    @Test
    fun `unclear numbers are refused, not guessed`() {
        assertNull(PhoneNumber.international(""))
        assertNull(PhoneNumber.international("   "))
        assertNull(PhoneNumber.international("ندارد"))
        assertNull(PhoneNumber.international("0700 12345"))        // یک رقم کم
        assertNull(PhoneNumber.international("07001234567"))       // یک رقم زیاد
        assertNull(PhoneNumber.international("0900123456"))        // ۰۹ پیش‌شمارهٔ افغانستان نیست
        assertNull(PhoneNumber.international("12345"))
        assertNull(PhoneNumber.international("+93"))
        assertNull(PhoneNumber.international("07001234ab"))
        assertNull(PhoneNumber.international("0700+123456"))       // + فقط در آغاز
        assertNull(PhoneNumber.international("+0700123456"))      // پس از + صفر نمی‌آید
    }

    @Test
    fun `whatsApp form has no plus`() {
        assertEquals("93700123456", PhoneNumber.forWhatsApp("0700123456"))
        assertNull(PhoneNumber.forWhatsApp("ندارد"))
    }

    @Test
    fun `dialable turns Persian and Arabic digits into Latin`() {
        assertEquals("0700123456", PhoneNumber.dialable("\u06F0\u06F7\u06F0\u06F0\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6"))
        assertEquals("0700123456", PhoneNumber.dialable("\u0660\u0667\u0660\u0660\u0661\u0662\u0663\u0664\u0665\u0666"))
    }

    @Test
    fun `dialable keeps a leading plus and drops separators`() {
        assertEquals("+93700123456", PhoneNumber.dialable(" +93 (700) 123-456 "))
        assertEquals("0700123456", PhoneNumber.dialable("0700.123.456"))
        assertEquals("700", PhoneNumber.dialable("7+00"))   // + only at the start
    }

    @Test
    fun `dialable of nothing dialable is empty`() {
        assertEquals("", PhoneNumber.dialable(""))
        assertEquals("", PhoneNumber.dialable("+"))
        assertEquals("", PhoneNumber.dialable("\u0646\u062F\u0627\u0631\u062F"))
    }
}

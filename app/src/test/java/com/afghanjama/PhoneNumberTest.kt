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
}

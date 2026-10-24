package com.afghanjama

import com.afghanjama.util.pad2
import com.afghanjama.util.toFixed
import com.afghanjama.util.toHex2
import com.afghanjama.util.withThousands
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * قالب‌بندیِ عدد بدون `String.format`. خروجی روی فاکتورِ چاپی و کلیدِ
 * گزارش‌های ماهانه می‌نشیند، پس هر کاراکتر باید ثابت بماند.
 */
class FormatTest {

    @Test fun pad2_pads_single_digits_only() {
        assertEquals("00", 0.pad2())
        assertEquals("09", 9.pad2())
        assertEquals("10", 10.pad2())
        assertEquals("123", 123.pad2())
        assertEquals("-1", (-1).pad2())
    }

    @Test fun withThousands_groups_by_three() {
        assertEquals("0", 0L.withThousands())
        assertEquals("999", 999L.withThousands())
        assertEquals("1,000", 1000L.withThousands())
        assertEquals("12,345", 12345L.withThousands())
        assertEquals("123,456", 123456L.withThousands())
        assertEquals("1,234,567", 1234567L.withThousands())
    }

    @Test fun withThousands_keeps_sign_outside_the_groups() {
        assertEquals("-1", (-1L).withThousands())
        assertEquals("-100", (-100L).withThousands())
        assertEquals("-1,000", (-1000L).withThousands())
        assertEquals("-123,456", (-123456L).withThousands())
    }

    @Test fun withThousands_handles_long_extremes() {
        assertEquals("9,223,372,036,854,775,807", Long.MAX_VALUE.withThousands())
        assertEquals("-9,223,372,036,854,775,808", Long.MIN_VALUE.withThousands())
    }

    @Test fun toFixed_rounds_half_away_from_zero() {
        assertEquals("0.3", 0.25.toFixed(1))
        assertEquals("-0.3", (-0.25).toFixed(1))
        assertEquals("3", 2.5.toFixed(0))
        assertEquals("-3", (-2.5).toFixed(0))
    }

    @Test fun toFixed_pads_fraction_and_whole_part() {
        assertEquals("0.00", 0.0.toFixed(2))
        assertEquals("0.05", 0.05.toFixed(2))
        assertEquals("1.50", 1.5.toFixed(2))
        assertEquals("12.346", 12.3456.toFixed(3))
        assertEquals("7", 7.0.toFixed(0))
    }

    @Test fun toFixed_float_matches_double() {
        assertEquals("2.5", 2.5f.toFixed(1))
        assertEquals("0.50", 0.5f.toFixed(2))
    }

    @Test fun toHex2_is_two_lowercase_digits_for_any_byte() {
        assertEquals("00", 0.toByte().toHex2())
        assertEquals("0a", 10.toByte().toHex2())
        assertEquals("7f", 127.toByte().toHex2())
        assertEquals("80", (-128).toByte().toHex2())
        assertEquals("ff", (-1).toByte().toHex2())
    }
}

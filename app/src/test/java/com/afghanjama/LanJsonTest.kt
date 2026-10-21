package com.afghanjama

import com.afghanjama.lan.LanJson
import com.afghanjama.lan.jInt
import com.afghanjama.lan.jLong
import com.afghanjama.lan.jObjects
import com.afghanjama.lan.jStr
import com.afghanjama.lan.jStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSONِ مشترکِ قراردادِ ۲ — دری، عدد، تودرتو، و ردِ هر چیزِ ناجور.
 */
class LanJsonTest {

    @Test
    fun `Dari, quotes, control characters and emoji-free symbols round-trip`() {
        val v = mapOf(
            "متن" to "خیاط احمد گفت: \"آستین‌ها\" دوباره دوخته شد.\nخطِ دوم\t\\",
            "n" to 12L,
            "neg" to -5L,
            "big" to 9_007_199_254_740_993L,
            "d" to 1.5,
            "t" to true,
            "f" to false,
            "z" to null,
            "list" to listOf(1L, "دو", listOf<Any?>(), mapOf<String, Any?>()),
            "nul" to "a\u0000b\u001fc\u2028",
        )
        val text = LanJson.write(v)
        assertEquals(v, LanJson.parse(text))
        assertTrue("U+2028 is escaped for safety", text.contains("\\u2028"))
    }

    @Test
    fun `escapes in input are understood, including surrogate pairs`() {
        val o = LanJson.parseObject("{\"a\":\"\\u0627\\u062d\\u0645\\u062f\",\"b\":\"\\ud83d\\ude00\",\"c\":\"\\/\"}")!!
        assertEquals("احمد", o.jStr("a"))
        assertEquals("\uD83D\uDE00", o.jStr("b"))
        assertEquals("/", o.jStr("c"))
    }

    @Test
    fun `anything malformed is refused whole`() {
        val bad = listOf(
            "", "{", "{\"a\":1,}", "[1,]", "{\"a\":1}{", "{\"a\" 1}", "{a:1}", "\"abc", "01", "1.", "-",
            "{\"a\":1,\"a\":2}", "\"\\x\"", "\"\\u12\"", "tru", "nul", "1e", "[\"\u0001\"]", "{\"a\":NaN}",
        )
        for (b in bad) {
            assertTrue("«$b» was accepted", runCatching { LanJson.parse(b) }.isFailure)
            assertNull(LanJson.parseObject(b))
        }
    }

    @Test
    fun `nesting deeper than the limit is refused`() {
        val deep = "[".repeat(LanJson.MAX_DEPTH + 2) + "]".repeat(LanJson.MAX_DEPTH + 2)
        assertTrue(runCatching { LanJson.parse(deep) }.isFailure)
        val ok = "[".repeat(10) + "]".repeat(10)
        assertTrue(runCatching { LanJson.parse(ok) }.isSuccess)
    }

    @Test
    fun `readers take what fits and ignore what does not`() {
        val o = LanJson.parseObject("{\"i\":3,\"l\":3.0,\"s\":5,\"arr\":[{\"x\":1},2,\"y\"],\"ss\":[\"a\",1,\"b\"]}")!!
        assertEquals(3, o.jInt("i"))
        assertEquals(3L, o.jLong("l"))
        assertNull(o.jStr("s"))
        assertEquals(1, o.jObjects("arr").size)
        assertEquals(listOf("a", "b"), o.jStrings("ss"))
        assertNull(o.jInt("missing"))
    }

    @Test
    fun `a value that is not JSON cannot be written by accident`() {
        assertTrue(runCatching { LanJson.write(mapOf("x" to Any())) }.isFailure)
        assertTrue(runCatching { LanJson.write(Double.NaN) }.isFailure)
        assertTrue(runCatching { LanJson.write(mapOf(1 to "x")) }.isFailure)
    }
}

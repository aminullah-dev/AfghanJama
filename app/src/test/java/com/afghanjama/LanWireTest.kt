package com.afghanjama

import com.afghanjama.lan.LanWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * خواندنِ درخواستِ شبکه — بایت به بایت.
 *
 * **اشکالی که این آزمون نگه می‌دارد:** سرور بدنه را حرف به حرف می‌خواند
 * ولی `Content-Length` شمارِ بایت‌هاست. بدنهٔ دری (هر حرف دو بایت) هرگز
 * «کامل» نمی‌شد، سرور تا سقفِ سوکت منتظر می‌ماند، گوشی «وصل نشد» می‌گفت،
 * و بعد سرور همان درخواست را ثبت می‌کرد — کارگر دوباره می‌زد و دو تا
 * می‌شد.
 *
 * [Strict] پس از آخرین بایتِ درخواست هر خواندنی را خطا می‌داند؛ یعنی اگر
 * خواننده یک بایت هم بیشتر از `Content-Length` بخواهد (همان کاری که
 * خوانندهٔ حرفی می‌کرد)، آزمون قرمز می‌شود — نه اینکه مثلِ سوکتِ واقعی
 * ده ثانیه منتظر بماند.
 */
class LanWireTest {

    /** جریانی که بعد از [bytes] به‌جای انتظار، خطا می‌دهد. */
    private class Strict(private val bytes: ByteArray) : InputStream() {
        private var pos = 0
        override fun read(): Int {
            if (pos >= bytes.size) throw AssertionError("خواننده از پایانِ درخواست جلوتر رفت")
            return bytes[pos++].toInt() and 0xFF
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (pos >= bytes.size) throw AssertionError("خواننده از پایانِ درخواست جلوتر رفت")
            val n = minOf(len, bytes.size - pos)
            System.arraycopy(bytes, pos, b, off, n)
            pos += n
            return n
        }
    }

    private fun post(body: String, eol: String = "\r\n", extra: String = ""): ByteArray {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "POST /request HTTP/1.1${eol}Host: 192.168.1.7:8797$eol" +
            "X-Workshop-Code: 123456$eol" + extra +
            "Content-Type: application/json; charset=utf-8$eol" +
            "Content-Length: ${bytes.size}$eol$eol"
        return head.toByteArray(Charsets.ISO_8859_1) + bytes
    }

    private fun ok(r: LanWire.Read): LanWire.Request {
        assertTrue("باید خوانده می‌شد، شد: $r", r is LanWire.Read.Ok)
        return (r as LanWire.Read.Ok).request
    }

    @Test
    fun `a Dari body is read exactly by its byte length`() {
        val text = "خیاط احمد کارِ سفارشِ ۱۲ را تمام کرد و تحویل داد. ".repeat(10).take(300)
        assertEquals(300, text.length)
        val body = """{"type":"NOTE","note":"$text"}"""
        val bytes = body.toByteArray(Charsets.UTF_8)
        assertTrue("بدنهٔ دری باید بایتِ بیشتری از حرف داشته باشد", bytes.size > body.length + 200)

        val req = ok(LanWire.readRequest(Strict(post(body))))
        assertEquals("POST", req.method)
        assertEquals("/request", req.target)
        assertEquals("123456", req.header("x-workshop-code"))
        assertEquals("نامِ سرخط بزرگ و کوچک ندارد", "123456", req.header("X-WORKSHOP-CODE"))
        assertEquals(body, req.body)
    }

    @Test
    fun `bare LF line endings are read too`() {
        val req = ok(LanWire.readRequest(Strict(post("""{"note":"سلام"}""", eol = "\n"))))
        assertEquals("""{"note":"سلام"}""", req.body)
        assertEquals("123456", req.header("X-Workshop-Code"))
    }

    @Test
    fun `a GET without Content-Length has an empty body`() {
        val raw = "GET /my-work?worker=%5BT1%5D HTTP/1.1\r\nX-Workshop-Code: 1\r\n\r\n"
        val req = ok(LanWire.readRequest(Strict(raw.toByteArray(Charsets.ISO_8859_1))))
        assertEquals("GET", req.method)
        assertEquals("/my-work?worker=%5BT1%5D", req.target)
        assertEquals("", req.body)
    }

    /** بدنهٔ ناقص رد می‌شود — تا امروز همان نیمه ثبت می‌شد. */
    @Test
    fun `a truncated body is refused, not half saved`() {
        val full = post("""{"type":"NOTE","note":"یک پیامِ بلند"}""")
        val cut = full.copyOf(full.size - 5)
        val r = LanWire.readRequest(ByteArrayInputStream(cut))
        assertTrue("$r", r is LanWire.Read.Bad)
        assertEquals(400, (r as LanWire.Read.Bad).status)
        assertEquals(LanWire.TRUNCATED, r.message)
    }

    @Test
    fun `an oversized body is refused before reading it`() {
        val raw = "POST /request HTTP/1.1\r\nContent-Length: ${LanWire.MAX_BODY_BYTES + 1}\r\n\r\n"
        val r = LanWire.readRequest(Strict(raw.toByteArray(Charsets.ISO_8859_1)))
        assertEquals(LanWire.Read.Bad(413, LanWire.TOO_LARGE), r)
    }

    @Test
    fun `oversized headers are refused`() {
        val long = "X-Pad: " + "a".repeat(LanWire.MAX_HEADER_BYTES) + "\r\n"
        val r1 = LanWire.readRequest(ByteArrayInputStream(post("{}", extra = long)))
        assertEquals(LanWire.Read.Bad(413, LanWire.TOO_LARGE), r1)

        val many = (1..LanWire.MAX_HEADER_LINES + 1).joinToString("") { "X-H$it: v\r\n" }
        val r2 = LanWire.readRequest(ByteArrayInputStream(post("{}", extra = many)))
        assertEquals(LanWire.Read.Bad(413, LanWire.TOO_LARGE), r2)
    }

    @Test
    fun `a bad Content-Length is refused`() {
        for (v in listOf("abc", "-5")) {
            val raw = "POST /request HTTP/1.1\r\nContent-Length: $v\r\n\r\n"
            val r = LanWire.readRequest(ByteArrayInputStream(raw.toByteArray(Charsets.ISO_8859_1)))
            assertEquals("Content-Length: $v", LanWire.Read.Bad(400, LanWire.MALFORMED), r)
        }
    }

    @Test
    fun `a closed connection or a broken request line is not a request`() {
        assertEquals(LanWire.Read.Closed, LanWire.readRequest(ByteArrayInputStream(ByteArray(0))))
        val r = LanWire.readRequest(ByteArrayInputStream("GARBAGE\r\n\r\n".toByteArray()))
        assertEquals(LanWire.Read.Bad(400, LanWire.MALFORMED), r)
        // سرخط‌هایی که به خطِ خالی نمی‌رسند
        val r2 = LanWire.readRequest(ByteArrayInputStream("GET /ping HTTP/1.1\r\nHost: x\r\n".toByteArray()))
        assertEquals(LanWire.Read.Bad(400, LanWire.MALFORMED), r2)
    }
}

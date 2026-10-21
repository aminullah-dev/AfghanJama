package com.afghanjama

import com.afghanjama.lan.HostAddress
import com.afghanjama.lan.LanCrypto
import com.afghanjama.lan.LanPrims
import com.afghanjama.lan.Pairing
import com.afghanjama.lan.PairingV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QRِ «افزودنِ گوشی» (`kylink2:`) و رمزِ دستیِ ۱۲ رقمی — و اینکه QRِ قراردادِ
 * ۱ (`kylink:`) هنوز همان‌طور خوانده می‌شود.
 */
class PairingV2Test {

    private val hubId = "5f0c2a9e-1b7d-4c3e-9a61-0d2f8e7b4c11"
    private val link = PairingV2.Link("192.168.1.7", 8797, hubId, "K7QH3M2P4XR5TABC", LanPrims.random(16))

    @Test
    fun `a v2 link round-trips through the QR text`() {
        val text = PairingV2.encode(link)
        assertTrue(text.startsWith("kylink2:192.168.1.7:8797:$hubId:K7QH3M2P4XR5TABC:"))
        assertEquals(link, PairingV2.parse(text))
        assertEquals("upper-case scheme from a camera app", link, PairingV2.parse("KYLINK2:" + text.removePrefix("kylink2:")))
        assertEquals("surrounding spaces", link, PairingV2.parse("  $text\n"))
    }

    @Test
    fun `the secret never shows up when a link is printed`() {
        val s = link.toString()
        assertFalse(s.contains(LanCrypto.b32(link.secret)))
        assertTrue(s.contains("secret=…"))
    }

    @Test
    fun `a v2 link with anything wrong is refused`() {
        val good = PairingV2.encode(link)
        val parts = good.removePrefix("kylink2:").split(':')
        fun with(i: Int, v: String) = "kylink2:" + parts.toMutableList().also { it[i] = v }.joinToString(":")
        assertNull("public address", PairingV2.parse(with(0, "8.8.8.8")))
        assertNull("loopback", PairingV2.parse(with(0, "127.0.0.1")))
        assertNull("port zero", PairingV2.parse(with(1, "0")))
        assertNull("port too big", PairingV2.parse(with(1, "70000")))
        assertNull("hub id with a colon-free junk", PairingV2.parse(with(2, "a b")))
        assertNull("short pairing id", PairingV2.parse(with(3, "K7")))
        assertNull("secret of 15 bytes", PairingV2.parse(with(4, LanCrypto.b32(ByteArray(15)))))
        assertNull("secret not base32", PairingV2.parse(with(4, "!!!!")))
        assertNull("a field missing", PairingV2.parse(good.substringBeforeLast(':')))
        assertNull("an extra field", PairingV2.parse("$good:x"))
        assertNull("not ours", PairingV2.parse("https://example.com"))
    }

    @Test
    fun `the refusal names the address problem when only the address is wrong`() {
        val text = PairingV2.encode(link.copy(host = "8.8.8.8"))
        assertEquals(HostAddress.NOT_WORKSHOP, PairingV2.refusal(text))
        assertEquals(Pairing.NOT_PAIRING, PairingV2.refusal("hello"))
    }

    @Test
    fun `the v1 QR still parses exactly as before, and is not a v2 link`() {
        val v1 = Pairing.encode("192.168.1.7", "482913")
        assertEquals(Pairing.Link("192.168.1.7", "482913"), Pairing.parse(v1))
        assertNull(PairingV2.parse(v1))
        assertNull(Pairing.parse(PairingV2.encode(link)))
        assertTrue(PairingV2.looksLikeOurs(v1))
        assertTrue(PairingV2.looksLikeOurs(PairingV2.encode(link)))
        assertFalse(PairingV2.looksLikeOurs("WIFI:S:x;;"))
    }

    // ------------------------------------------------------------ رمزِ دستی

    @Test
    fun `a manual code is twelve digits, and every digit turns up`() {
        val seen = mutableSetOf<Char>()
        repeat(200) {
            val c = PairingV2.newManualCode()
            assertEquals(12, c.length)
            assertTrue(c.all { it in '0'..'9' })
            seen += c.toSet()
        }
        assertEquals(10, seen.size)
    }

    @Test
    fun `typed codes are read with Persian or Arabic digits, spaces and dashes`() {
        assertEquals("482913572046", PairingV2.normalizeManualCode("۴۸۲۹ ۱۳۵۷ ۲۰۴۶"))
        assertEquals("482913572046", PairingV2.normalizeManualCode("٤٨٢٩-١٣٥٧-٢٠٤٦"))
        assertEquals("482913572046", PairingV2.normalizeManualCode("4829 1357 2046"))
        assertNull("eleven digits", PairingV2.normalizeManualCode("48291357204"))
        assertNull("a letter", PairingV2.normalizeManualCode("4829 1357 204A"))
        assertEquals("4829 1357 2046", PairingV2.groupManualCode("482913572046"))
    }

    @Test
    fun `two manual codes in a row differ`() {
        assertNotNull(PairingV2.newManualCode())
        val codes = List(50) { PairingV2.newManualCode() }.toSet()
        assertEquals(50, codes.size)
    }
}

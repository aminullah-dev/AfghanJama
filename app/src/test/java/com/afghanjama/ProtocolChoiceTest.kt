package com.afghanjama

import com.afghanjama.lan.PingInfo
import com.afghanjama.lan.ProtocolChoice
import com.afghanjama.licence.LicenceFacts
import com.afghanjama.licence.LicencePolicy
import com.afghanjama.licence.LicenceState
import com.afghanjama.licence.LicenceText
import com.afghanjama.licence.ReadOnlyReason
import com.afghanjama.prefs.DeviceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * گوشیِ جفت‌شده با قراردادِ ۲ هرگز به رمزِ شش‌رقمی برنمی‌گردد؛ و دفترِ
 * گوشی‌ای که به کامپیوتر برده شده فقط‌خواندنی است، هر لایسنسی که باشد.
 */
class ProtocolChoiceTest {

    private fun ping(max: Int) = PingInfo("KhayatYar", 1, max, if (max >= 2) "hub" else null, null, false, null)

    @Test
    fun `a paired phone never steps down to protocol 1`() {
        assertEquals(ProtocolChoice.Use.V2, ProtocolChoice.decide(pairedV2 = true, ping = ping(2)))
        assertEquals(ProtocolChoice.Use.REFUSE_DOWNGRADE, ProtocolChoice.decide(pairedV2 = true, ping = ping(1)))
    }

    @Test
    fun `an old phone meets a new hub and is offered the upgrade, and with an old hub it stays on protocol 1`() {
        assertEquals(ProtocolChoice.Use.OFFER_UPGRADE, ProtocolChoice.decide(pairedV2 = false, ping = ping(2)))
        assertEquals(ProtocolChoice.Use.V1, ProtocolChoice.decide(pairedV2 = false, ping = ping(1)))
    }

    @Test
    fun `a moved ledger is read-only even with a valid trial, and says why in Dari`() {
        val t = 1_760_000_000_000L
        val facts = LicenceFacts(null, "X", t, t, mode = DeviceMode.WORKER, ledgerMoved = true)
        val s = LicencePolicy.evaluate(facts, t)
        assertEquals(LicenceState.READ_ONLY, s.state)
        assertEquals(ReadOnlyReason.LEDGER_MOVED, s.reason)
        assertFalse(s.canWrite)
        assertEquals(LicenceText.LEDGER_MOVED, LicenceText.reason(s))
        assertTrue(LicenceText.remedy(s)!!.contains("کارگاهِ وصل"))
        // بی آن پرچم همان گوشیِ پیرو است و نوشتن آزاد.
        assertTrue(LicencePolicy.evaluate(facts.copy(ledgerMoved = false), t).canWrite)
    }
}

package com.afghanjama

import com.afghanjama.lan.Ack
import com.afghanjama.lan.AckStatus
import com.afghanjama.lan.DebtLine
import com.afghanjama.lan.HubCommand
import com.afghanjama.lan.HubProtocol
import com.afghanjama.lan.HubRole
import com.afghanjama.lan.HubSnapshot
import com.afghanjama.lan.LanJson
import com.afghanjama.lan.LanPrims
import com.afghanjama.lan.OrderLine
import com.afghanjama.lan.OutboxWire
import com.afghanjama.lan.OwnerView
import com.afghanjama.lan.PairGrant
import com.afghanjama.lan.PairState
import com.afghanjama.lan.PhoneInfo
import com.afghanjama.lan.PingInfo
import com.afghanjama.lan.RequestLine
import com.afghanjama.lan.SyncAsk
import com.afghanjama.lan.SyncReply
import com.afghanjama.lan.WorkLine
import com.afghanjama.lan.WorkerView
import com.afghanjama.ui.vm.BoardRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * شکلِ پیام‌ها: هر نقش همان را می‌خوانَد که کامپیوتر نوشته، تصویرِ نقشِ
 * دیگر پذیرفته نمی‌شود، و هیچ کلیدی در گزارش نمی‌نشیند.
 */
class HubMessagesTest {

    private fun round(r: SyncReply): SyncReply? =
        SyncReply.parse(LanJson.parseObject(LanJson.write(r.toJson()))!!)

    private fun reply(snapshot: HubSnapshot?, role: HubRole) = SyncReply(
        serverTime = 5L, acks = listOf(Ack("k-00000001", AckStatus.REJECTED, "این کار به نامِ شما نیست.", 0)),
        cursor = "abc", snapshot = snapshot, licenceState = "TRIAL", licenceWritable = true,
        role = role, person = "[T1] احمد", deviceName = "گوشیِ احمد", hubId = "hub-0001-aaaa",
        hubName = "کارگاه", hubVersion = "1.11.0", addresses = listOf("192.168.1.7", "8.8.8.8"),
    )

    @Test
    fun `a worker snapshot round-trips, with the licence and without public addresses`() {
        val snap = HubSnapshot(
            HubRole.WORKER,
            worker = WorkerView(
                "[T1] احمد", listOf(WorkLine(4, "AJ-4", "D-9", 10, 35, 77)), 3,
                listOf(RequestLine(9, "k-1", "SEWING_DONE", "AJ-4 — ۵", "[T1] احمد", "گوشی", "PENDING", "", 1, 2, false)),
                insideSince = 100L,
            ),
            board = listOf(BoardRow("[T1] احمد", "AJ-4", "پیراهن", 10, 2, -1)),
        )
        val back = round(reply(snap, HubRole.WORKER))!!
        assertEquals(snap, back.snapshot)
        assertEquals("TRIAL", back.licenceState)
        assertEquals(listOf("192.168.1.7"), back.addresses)
        assertEquals(AckStatus.REJECTED, back.acks.single().status)
    }

    @Test
    fun `an owner snapshot round-trips`() {
        val o = OrderLine("AJ-7", "حمید", "دوخت", 10, 5)
        val snap = HubSnapshot(
            HubRole.OWNER,
            owner = OwnerView(emptyList(), listOf(o), emptyList(), listOf(o), listOf(o), 4, 2, listOf("احمد"),
                listOf(DebtLine("حمید", "0700000000", 1_500, "قول شکسته", false))),
        )
        assertEquals(snap, round(reply(snap, HubRole.OWNER))!!.snapshot)
    }

    @Test
    fun `a snapshot of another role is refused - a worker never accepts an owner view`() {
        val owner = HubSnapshot(HubRole.OWNER, owner = OwnerView(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0, 0, emptyList(), emptyList()))
        // پیامی که می‌گوید «تو کارگری» ولی تصویرِ صاحب‌کار دارد.
        val mixed = reply(owner, HubRole.WORKER)
        assertNull(round(mixed))
    }

    @Test
    fun `unchanged means no snapshot at all`() {
        val back = round(reply(null, HubRole.WALL))!!
        assertNull(back.snapshot)
        assertEquals(true, LanJson.parseObject(LanJson.write(reply(null, HubRole.WALL).toJson()))!!["unchanged"])
    }

    @Test
    fun `a sync ask with more than fifty items or a bad key is refused whole`() {
        val ok = SyncAsk(1, "", listOf(OutboxWire("k-00000001", 1, "NOTE", mapOf("text" to "سلام"))), "1.11.0", "android")
        assertEquals(ok, SyncAsk.parse(LanJson.parseObject(LanJson.write(ok.toJson()))!!))
        val many = ok.copy(outbox = (0..HubProtocol.MAX_OUTBOX_PER_SYNC).map { OutboxWire("k-%08d".format(it), 1, "NOTE", emptyMap()) })
        assertNull(SyncAsk.parse(LanJson.parseObject(LanJson.write(many.toJson()))!!))
        val bad = ok.copy(outbox = listOf(OutboxWire("../x", 1, "NOTE", emptyMap())))
        assertNull(SyncAsk.parse(LanJson.parseObject(LanJson.write(bad.toJson()))!!))
    }

    @Test
    fun `phone info is trimmed, and an unknown role is refused`() {
        val long = PhoneInfo("گ".repeat(500), "android", "1.11.0", HubRole.WORKER)
        assertEquals(PhoneInfo.NAME_LIMIT, PhoneInfo.parse(LanJson.parseObject(LanJson.write(long.toJson()))!!)!!.name.length)
        assertNull(PhoneInfo.parse(mapOf("name" to "x", "role" to "admin")))
    }

    @Test
    fun `a grant without a proper key is not an approval, and the key never prints`() {
        val key = LanPrims.random(32)
        val g = PairGrant(PairState.APPROVED, "hub", "کارگاه", 1, "dev-00000001", key, HubRole.WORKER, "[T1] احمد")
        val back = PairGrant.parse(LanJson.parseObject(LanJson.write(g.toJson()))!!)
        assertEquals(g, back)
        assertFalse(g.toString().contains(com.afghanjama.lan.LanCrypto.b64(key)))
        val noKey = g.toJson().toMutableMap().also { it["deviceKey"] = "AAAA" }
        assertNull(PairGrant.parse(noKey))
        // انتظار و رد کلید ندارند و کلیدی هم نمی‌نویسند.
        val wait = PairGrant(PairState.WAIT, "hub", "کارگاه", 1)
        assertFalse(LanJson.write(wait.toJson()).contains("deviceKey"))
    }

    @Test
    fun `ping fields from an old hub default safely`() {
        val old = PingInfo.parse(mapOf("ok" to true, "app" to "KhayatYar", "protocol" to 1L))!!
        assertEquals(1, old.protocolMin)
        assertEquals(1, old.protocolMax)
        assertFalse(old.pairingOpen)
        assertNull(PingInfo.parse(mapOf("ok" to false)))
    }

    @Test
    fun `each command belongs to one role, and only worker commands wait for approval`() {
        assertEquals(setOf(HubRole.WORKER), HubCommand.SEWING_DONE.roles)
        assertTrue(HubCommand.SEWING_DONE.queues)
        assertEquals(setOf(HubRole.OWNER), HubCommand.APPROVE_REQUEST.roles)
        assertFalse(HubCommand.APPROVE_REQUEST.queues)
        assertTrue(HubCommand.entries.none { HubRole.WALL in it.roles })
        assertNull(HubCommand.of("DELETE_EVERYTHING"))
    }
}

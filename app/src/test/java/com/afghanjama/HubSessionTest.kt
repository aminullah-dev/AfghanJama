package com.afghanjama

import com.afghanjama.lan.HubCommand
import com.afghanjama.lan.HubLinkPrefs
import com.afghanjama.lan.HubProtocol
import com.afghanjama.lan.HubRole
import com.afghanjama.lan.HubSession
import com.afghanjama.lan.LanCrypto
import com.afghanjama.lan.Outbox
import com.afghanjama.lan.OutboxItem
import com.afghanjama.lan.OutboxState
import com.afghanjama.lan.PairState
import com.afghanjama.lan.PairingV2
import com.afghanjama.lan.PhoneInfo
import com.afghanjama.lan.ProtocolChoice
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * جلسهٔ گوشی («کارگاهِ وصل») در برابرِ کامپیوترِ ساختگی: جفت شدن، صفِ
 * آفلاین، ساعت، دسترسیِ برداشته، پیدا کردنِ دوباره، و پایین نیامدن به
 * قراردادِ ۱.
 */
class HubSessionTest {

    private val hub = FakeHub("192.168.1.7")
    private val net = FakeNet(mapOf("192.168.1.7" to hub))
    private val settings = MemSettings()
    private val secrets = MemSecrets()
    private var ownIp: String? = "192.168.1.30"
    private val session = HubSession(
        settings, "android", net, clock = { hub.now }, store = { secrets }, ownIp = { ownIp }, appVersion = "1.11.0", pairRetryMs = 1,
    )
    private val info = PhoneInfo("گوشیِ احمد", "", "1.11.0", HubRole.WORKER)

    private fun pair() = runBlocking {
        val r = async { session.pairWithLink(PairingV2.encode(hub.link()), info) }
        delay(20)
        hub.decision = PairState.APPROVED
        assertNull(r.await())
    }

    @Test
    fun `pairing keeps the key only in the safe store`() {
        pair()
        assertTrue(session.state.value.paired)
        assertEquals(HubRole.WORKER, session.state.value.role)
        assertEquals("[T1] احمد", session.state.value.person)
        assertTrue(secrets.m[HubLinkPrefs.SECRET_NAME]!!.contentEquals(hub.deviceKey))
        assertFalse("the key leaked into settings", settings.dump().contains(LanCrypto.b64(hub.deviceKey)))
    }

    @Test
    fun `without a safe store the phone refuses to pair rather than store the key in plain`() = runBlocking {
        val bare = HubSession(MemSettings(), "android", net, clock = { hub.now }, store = { null })
        assertEquals(HubSession.NO_SAFE_STORE, bare.pairWithLink(PairingV2.encode(hub.link()), info))
        assertFalse(bare.state.value.paired)
    }

    @Test
    fun `a press while away waits in the queue and goes when the computer is back`() = runBlocking {
        pair()
        net.hubs.remove("192.168.1.7")
        ownIp = null // پیدا کردنِ دوباره هم چیزی نیابد
        assertNull(session.enqueue(HubCommand.ATTENDANCE_IN, emptyMap(), "ورود"))
        assertFalse(session.sync())
        assertEquals(1, session.state.value.pending)
        assertTrue(session.state.value.problem!!.startsWith("به کامپیوترِ کارگاه وصل نیست"))
        assertEquals(OutboxState.PENDING, session.state.value.outbox.single().state)
        net.hubs["192.168.1.7"] = hub
        assertTrue(session.retryNow())
        assertEquals(0, session.state.value.pending)
        assertEquals(OutboxState.SENT, session.state.value.outbox.single().state)
        assertEquals(1, hub.received.size)
    }

    @Test
    fun `a wrong phone clock is learned and the same sync goes through`() = runBlocking {
        pair()
        hub.clockOnce = true
        assertTrue(session.sync())
        assertNull(session.state.value.problem)
    }

    @Test
    fun `a revoked phone is told to pair again and keeps its queue`() = runBlocking {
        pair()
        session.enqueue(HubCommand.NOTE, mapOf("text" to "سلام"), "پیام")
        hub.revoked = true
        assertFalse(session.sync())
        assertTrue(session.state.value.needsPairing)
        assertEquals(1, session.state.value.pending)
    }

    @Test
    fun `a moved computer is found again on the phone's own subnet`() = runBlocking {
        pair()
        net.hubs.remove("192.168.1.7")
        net.hubs["192.168.1.42"] = hub
        assertFalse(session.sync())
        assertTrue("second failure finds it", session.sync())
        assertEquals("192.168.1.42", HubLinkPrefs.host(settings))
    }

    @Test
    fun `the downgrade guard speaks when ping says protocol 1 after failures`() = runBlocking {
        pair()
        // نشانی جواب می‌دهد ولی همگام‌سازی نه (کامپیوترِ ۱.۱۰ جایش نشسته).
        val old = FakeHub("192.168.1.7", hubId = hub.hubId)
        old.pingBody = "{\"ok\":true,\"app\":\"KhayatYar\",\"protocol\":1}"
        val oldNet = object : com.afghanjama.lan.HubTransport {
            override suspend fun exchange(
                host: String, port: Int, method: String, path: String, headers: Map<String, String>,
                body: String?, timeoutMs: Int, connectTimeoutMs: Int,
            ): com.afghanjama.lan.LanResult<com.afghanjama.lan.HttpReply> =
                if (path == "/ping") com.afghanjama.lan.LanResult.Ok(old.handle(method, path, headers, body))
                else com.afghanjama.lan.LanResult.Err(com.afghanjama.lan.HUB_UNREACHABLE)
        }
        val s2 = HubSession(settings, "android", oldNet, clock = { hub.now }, store = { secrets }, ownIp = { null })
        assertFalse(s2.sync())
        assertFalse(s2.sync())
        assertEquals(ProtocolChoice.DOWNGRADE_REFUSED, s2.state.value.problem)
        assertTrue(old.paths.all { it == "/ping" })
    }

    @Test
    fun `re-pairing as someone else orphans the old person's queue, visibly`() = runBlocking {
        pair()
        session.enqueue(HubCommand.NOTE, mapOf("text" to "x"), "پیام")
        val keep = HubLinkPrefs.outbox(settings).map { it.copy(person = "[T9] کسی دیگر") }
        HubLinkPrefs.saveOutbox(settings, keep)
        session.forget()
        assertFalse(session.state.value.paired)
        assertEquals("the queue survives forgetting", 1, session.state.value.outbox.size)
        hub.decision = PairState.WAIT
        pair()
        val item = session.state.value.outbox.single()
        assertEquals(OutboxState.ORPHANED, item.state)
        assertEquals(Outbox.ORPHANED_REASON, Outbox.label(item))
    }

    @Test
    fun `a role sends only its own commands`() {
        pair()
        assertNotNull(session.enqueue(HubCommand.APPROVE_REQUEST, mapOf("requestId" to 1L), "تأیید"))
        assertNull(session.enqueue(HubCommand.NOTE, mapOf("text" to "سلام"), "پیام"))
    }

    @Test
    fun `a damaged queue file is set aside, not silently trusted`() {
        settings.putString("hub_link", "outbox", "{broken")
        assertEquals(emptyList<OutboxItem>(), HubLinkPrefs.outbox(settings))
        assertEquals("{broken", settings.getString("hub_link", "outbox_damaged", ""))
    }

    @Test
    fun `the manual code path refuses the owner role and bad codes before any network call`() = runBlocking {
        assertEquals(
            com.afghanjama.lan.HubClient.OWNER_NEEDS_QR,
            session.pairWithCode("192.168.1.7", "482913572046", info.copy(wantRole = HubRole.OWNER)),
        )
        assertTrue(session.pairWithCode("192.168.1.7", "123", info)!!.contains("رقم"))
        assertTrue(hub.paths.isEmpty())
        assertEquals(HubProtocol.PORT, HubLinkPrefs.port(settings))
    }
}

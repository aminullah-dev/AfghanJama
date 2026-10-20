package com.afghanjama

import com.afghanjama.lan.AutoStartRule
import com.afghanjama.lan.DefaultRoute
import com.afghanjama.lan.HubOff
import com.afghanjama.lan.LanLink
import com.afghanjama.lan.LanNetwork
import com.afghanjama.lan.RouteTable
import com.afghanjama.prefs.DeviceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «شبکهٔ کارگاه» — اشتراک فقط روی شبکه‌ای خودش روشن می‌شود که دستگاهِ
 * اصلی وقتِ «اصلی» شدن رویش بود.
 *
 * **خطری که این آزمون نگه می‌دارد:** دستگاهِ اصلی اغلب لپ‌تاپ یا گوشیِ
 * خودِ کارفرماست. اگر سرور روی هر وای‌فایی خودش روشن شود، رمزِ شش‌رقمیِ
 * کارگاه جلوی هر غریبه‌ای در چایخانه است.
 */
class LanNetworkTest {

    private val workshop = LanNetwork("192.168.1.0/24", "192.168.1.1", "a4:2b:b0:02:34:56")

    // ---------------- زیرشبکه ----------------

    @Test
    fun `the subnet is the address with the host part cleared`() {
        assertEquals("192.168.1.0/24", LanNetwork.subnetOf("192.168.1.37", 24))
        assertEquals("10.20.0.0/16", LanNetwork.subnetOf("10.20.30.40", 16))
        assertEquals("172.16.0.0/12", LanNetwork.subnetOf("172.31.255.1", 12))
        assertEquals("100.64.0.0/10", LanNetwork.subnetOf("100.127.1.2", 10))
        assertEquals("192.168.43.1/32", LanNetwork.subnetOf("192.168.43.1", 32))
    }

    @Test
    fun `a nonsense prefix means this address only, never a guessed slash 24`() {
        assertEquals("192.168.1.37/32", LanNetwork.subnetOf("192.168.1.37", 0))
        assertEquals("192.168.1.37/32", LanNetwork.subnetOf("192.168.1.37", 128))
        assertNull(LanNetwork.subnetOf("192.168.1", 24))
        assertNull(LanNetwork.subnetOf("192.168.1.300", 24))
    }

    @Test
    fun `contains knows its own addresses`() {
        assertTrue(LanNetwork.contains("192.168.1.0/24", "192.168.1.1"))
        assertFalse(LanNetwork.contains("192.168.1.0/24", "192.168.2.1"))
        assertFalse(LanNetwork.contains("garbage", "192.168.1.1"))
    }

    // ---------------- ماندن در تنظیمات ----------------

    @Test
    fun `the remembered network survives settings, with or without the router`() {
        listOf(
            workshop,
            LanNetwork("10.0.0.0/8"),
            LanNetwork("192.168.43.0/24", "192.168.43.1"),
        ).forEach { assertEquals(it, LanNetwork.decode(it.encode())) }
    }

    @Test
    fun `nothing stored, or something broken, is not a network`() {
        listOf("", "garbage", "2|192.168.1.0/24||", "1|192.168.1.7/24||", "1|192.168.1.0||", "1|a|b|c")
            .forEach { assertNull("«$it» must not decode", LanNetwork.decode(it)) }
    }

    @Test
    fun `hardware addresses are read the way each system prints them`() {
        assertEquals("a4:2b:b0:02:34:56", LanNetwork.normalizeMac("a4:2b:b0:2:34:56")) // مک صفرِ اول را نمی‌نویسد
        assertEquals("a4:2b:b0:02:34:56", LanNetwork.normalizeMac("A4-2B-B0-02-34-56")) // ویندوز
        assertNull(LanNetwork.normalizeMac("00:00:00:00:00:00"))
        assertNull(LanNetwork.normalizeMac("ff-ff-ff-ff-ff-ff"))
        assertNull(LanNetwork.normalizeMac("(incomplete)"))
    }

    // ---------------- همان شبکه؟ ----------------

    @Test
    fun `the same subnet, router and router hardware is the workshop`() {
        assertTrue(workshop.sameAs(workshop))
    }

    @Test
    fun `a cafe with the same router model and address is not the workshop`() {
        val cafe = workshop.copy(gatewayMac = "10:fe:ed:00:00:01")
        assertFalse(workshop.sameAs(cafe))
    }

    @Test
    fun `another subnet or another router address is not the workshop`() {
        assertFalse(workshop.sameAs(workshop.copy(subnet = "192.168.0.0/24")))
        assertFalse(workshop.sameAs(workshop.copy(gateway = "192.168.1.254")))
    }

    @Test
    fun `what one side does not know does not decide`() {
        // گوشی نشانیِ سخت‌افزاریِ مودم را نمی‌داند؛ کامپیوتر گاهی هنوز در ARP ندارد.
        assertTrue(workshop.sameAs(workshop.copy(gatewayMac = null)))
        assertTrue(LanNetwork("192.168.1.0/24").sameAs(workshop))
        assertTrue(workshop.sameAs(LanNetwork("192.168.1.0/24")))
    }

    // ---------------- روشن شدنِ خودکار ----------------

    @Test
    fun `a main device on the workshop network starts`() {
        assertNull(AutoStartRule.decide(DeviceMode.MAIN, "482913", workshop, listOf(LanNetwork("10.8.0.0/24"), workshop)))
    }

    @Test
    fun `everything else stays off and says why`() {
        val now = listOf(workshop)
        assertEquals(HubOff.NOT_MAIN, AutoStartRule.decide(DeviceMode.STANDALONE, "482913", workshop, now))
        assertEquals(HubOff.NOT_MAIN, AutoStartRule.decide(DeviceMode.WORKER, "482913", workshop, now))
        assertEquals(HubOff.NO_CODE, AutoStartRule.decide(DeviceMode.MAIN, " ", workshop, now))
        // نصبِ پیش از ۱.۹.۱: هیچ شبکه‌ای به یاد نمانده — کارفرما یک بار «روشن» بزند.
        assertEquals(HubOff.NEW_NETWORK, AutoStartRule.decide(DeviceMode.MAIN, "482913", null, now))
        assertEquals(HubOff.NO_NETWORK, AutoStartRule.decide(DeviceMode.MAIN, "482913", workshop, emptyList()))
        assertEquals(
            HubOff.OTHER_NETWORK,
            AutoStartRule.decide(DeviceMode.MAIN, "482913", workshop, listOf(workshop.copy(gatewayMac = "10:fe:ed:00:00:01")))
        )
    }

    // ---------------- جدولِ مسیریابیِ هر سیستم ----------------

    @Test
    fun `mac netstat - the default route and its interface`() {
        val out = """
            Routing tables

            Internet:
            Destination        Gateway            Flags               Netif Expire
            default            192.168.1.1        UGScg                 en0
            default            link#18            UCSIg             bridge100      !
            127                127.0.0.1          UCS                   lo0
            192.168.1          link#11            UCS                   en0      !
        """.trimIndent()
        assertEquals(listOf(DefaultRoute("192.168.1.1", iface = "en0")), RouteTable.parseNetstat(out))
        // نسخه‌های قدیمیِ مک ستون‌های Refs و Use هم دارند.
        val old = "default            10.0.0.1           UGSc           12        0     en1"
        assertEquals(listOf(DefaultRoute("10.0.0.1", iface = "en1")), RouteTable.parseNetstat(old))
    }

    @Test
    fun `windows route print - only numeric rows, whatever the language`() {
        val out = """
            ===========================================================================
            IPv4-Routentabelle
            ===========================================================================
            Aktive Routen:
                 Netzwerkziel    Netzwerkmaske          Gateway    Schnittstelle Metrik
                      0.0.0.0          0.0.0.0      192.168.1.1     192.168.1.7     25
                    127.0.0.0        255.0.0.0   Auf Verbindung         127.0.0.1    331
            ===========================================================================
            Ständige Routen:
              Netzwerkadresse          Maske  Gatewayadresse  Metrik
                      0.0.0.0          0.0.0.0      192.168.1.1  Standard
        """.trimIndent()
        assertEquals(
            listOf(DefaultRoute("192.168.1.1", ifaceIp = "192.168.1.7"), DefaultRoute("192.168.1.1")),
            RouteTable.parseRoutePrint(out)
        )
    }

    @Test
    fun `linux ip route`() {
        val out = "default via 192.168.1.1 dev wlp2s0 proto dhcp src 192.168.1.7 metric 600\n" +
            "default dev tun0 scope link"
        assertEquals(
            listOf(DefaultRoute("192.168.1.1", iface = "wlp2s0", ifaceIp = "192.168.1.7")),
            RouteTable.parseIpRoute(out)
        )
    }

    @Test
    fun `arp answers on all three systems, and only for the asked address`() {
        assertEquals(
            "a4:2b:b0:02:34:56",
            RouteTable.parseArp("? (192.168.1.1) at a4:2b:b0:2:34:56 on en0 ifscope [ethernet]", "192.168.1.1")
        )
        assertEquals(
            "a4:2b:b0:02:34:56",
            RouteTable.parseArp(
                "\nInterface: 192.168.1.7 --- 0x5\n  Internet Address      Physical Address      Type\n" +
                    "  192.168.1.1           a4-2b-b0-02-34-56     dynamic\n",
                "192.168.1.1"
            )
        )
        assertEquals(
            "a4:2b:b0:02:34:56",
            RouteTable.parseArp("192.168.1.1 dev wlp2s0 lladdr a4:2b:b0:02:34:56 REACHABLE", "192.168.1.1")
        )
        assertNull(RouteTable.parseArp("? (192.168.1.1) -- no entry", "192.168.1.1"))
        assertNull(RouteTable.parseArp("  192.168.1.10          a4-2b-b0-02-34-56     dynamic", "192.168.1.1"))
    }

    @Test
    fun `the router is attached only to the card it is on, and only if it is inside that subnet`() {
        val wifi = LanLink("192.168.1.7", "en0", LanNetwork("192.168.1.0/24"))
        val cable = LanLink("10.0.0.5", "en5", LanNetwork("10.0.0.0/24"))
        val routes = listOf(DefaultRoute("192.168.1.1", iface = "en0"), DefaultRoute("172.20.0.1", iface = "en5"))
        val out = RouteTable.attach(listOf(wifi, cable), routes) { if (it == "192.168.1.1") "a4:2b:b0:02:34:56" else null }
        assertEquals(LanNetwork("192.168.1.0/24", "192.168.1.1", "a4:2b:b0:02:34:56"), out["192.168.1.7"])
        // کابلِ کارگاهِ بی‌اینترنت: مسیرِ «پیش‌فرضِ» ناجور به آن چسبانده نمی‌شود.
        assertEquals(LanNetwork("10.0.0.0/24"), out["10.0.0.5"])
    }
}

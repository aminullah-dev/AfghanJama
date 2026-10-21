package com.afghanjama.lan

/**
 * پاکت‌های قراردادِ ۲ — **هر دو سو از همین یک فایل.**
 *
 * گوشی پاکت می‌بندد و کامپیوتر باز می‌کند، و برعکس. اگر هر سو دادهٔ
 * همراه (AAD) را خودش می‌ساخت، روزی یکی یک «|» را جا می‌انداخت و هیچ
 * پاکتی باز نمی‌شد — یا بدتر، دو پیامِ متفاوت یک AAD می‌گرفتند. پس سازنده و
 * خواننده هر دو اینجا هستند و آزمونِ JVM رفت‌وبرگشتِ کامل را بی شبکه
 * می‌سنجد.
 *
 * هر شکستی `null` است — کامپیوتر به گوشی نمی‌گوید کدام بخش نادرست بود.
 */
object HubEnvelope {

    // ================================================================ جفت شدن

    /** آنچه گوشی پس از «سلام» در حافظه نگه می‌دارد تا جواب را باز کند. */
    class PairDraft(
        val body: String,
        val key: ByteArray,
    ) {
        override fun toString(): String = "PairDraft(…)"
    }

    /**
     * درخواستِ جفت شدن. [secret] رازِ QR (۱۶ بایت) یا رمزِ دستی (۱۲ رقم) است؛
     * روی شبکه نمی‌رود، فقط در کلید می‌نشیند.
     */
    fun pairRequest(hello: PairHello, via: PairVia, secret: ByteArray, info: PhoneInfo): PairDraft {
        val mine = LanPrims.p256KeyPair()
        val shared = LanPrims.p256Agree(mine, hello.hubPub)
            ?: throw IllegalStateException("کلیدِ عمومیِ کامپیوتر روی منحنی نیست")
        val key = LanCrypto.pairKey(
            shared, secret, HubProtocol.pairTranscript(hello.pairingId, via, hello.hubPub, mine.publicX963)
        )
        val box = LanCrypto.seal(
            key, LanJson.write(info.toJson()).encodeToByteArray(), HubProtocol.aadPairRequest(hello.pairingId, via)
        )
        val body = LanJson.write(
            mapOf(
                "pairingId" to hello.pairingId,
                "via" to via.wire,
                "phonePub" to LanCrypto.b64(mine.publicX963),
                "nonce" to LanCrypto.b64(box.nonce),
                "box" to LanCrypto.b64(box.box),
            )
        )
        return PairDraft(body, key)
    }

    /** درخواستِ جفت شدن، از چشمِ کامپیوتر. */
    class PairOpened(val via: PairVia, val info: PhoneInfo, val key: ByteArray, val phonePub: ByteArray) {
        override fun toString(): String = "PairOpened(via=$via, info=$info, key=…)"
    }

    /** آنچه از درخواستِ جفت شدن پیش از باز کردن خوانده می‌شود — برای اینکه کدام راز. */
    fun pairVia(body: Map<String, Any?>): PairVia? = PairVia.of(body.jStr("via"))

    /**
     * کامپیوتر درخواستِ جفت شدن را باز می‌کند. [hubPair] جفت‌کلیدِ گذرای همین
     * پنجره است و [secretFor] رازِ هر راه را می‌دهد (`null` اگر آن راه در این
     * پنجره نیست).
     */
    fun openPairRequest(
        body: Map<String, Any?>,
        pairingId: String,
        hubPair: EcKeyPair,
        secretFor: (PairVia) -> ByteArray?,
    ): PairOpened? {
        if (body.jStr("pairingId") != pairingId) return null
        val via = pairVia(body) ?: return null
        val secret = secretFor(via) ?: return null
        val phonePub = LanCrypto.unb64(body.jStr("phonePub"))?.takeIf { it.size == 65 } ?: return null
        val nonce = LanCrypto.unb64(body.jStr("nonce"))?.takeIf { it.size == LanCrypto.NONCE_BYTES } ?: return null
        val box = LanCrypto.unb64(body.jStr("box")) ?: return null
        val shared = LanPrims.p256Agree(hubPair, phonePub) ?: return null
        val key = LanCrypto.pairKey(
            shared, secret, HubProtocol.pairTranscript(pairingId, via, hubPair.publicX963, phonePub)
        )
        val plain = LanCrypto.open(key, nonce, box, HubProtocol.aadPairRequest(pairingId, via)) ?: return null
        val info = LanJson.parseObject(plain.decodeToString())?.let { PhoneInfo.parse(it) } ?: return null
        return PairOpened(via, info, key, phonePub)
    }

    /** پرسشِ «اجازه دادید؟»؛ nonceش در جواب بسته می‌شود. */
    class StatusAsk(val body: String, val nonce: ByteArray)

    fun pairStatusAsk(requestId: String, key: ByteArray, phoneNow: Long): StatusAsk {
        val box = LanCrypto.seal(
            key, LanJson.write(mapOf("t" to phoneNow)).encodeToByteArray(), HubProtocol.aadPairStatus(requestId)
        )
        val body = LanJson.write(
            mapOf("requestId" to requestId, "nonce" to LanCrypto.b64(box.nonce), "box" to LanCrypto.b64(box.box))
        )
        return StatusAsk(body, box.nonce)
    }

    /** کامپیوتر: پرسش از همان گوشی است؟ nonceِ پرسش را برای جواب برمی‌گرداند. */
    fun openPairStatus(body: Map<String, Any?>, requestId: String, key: ByteArray): ByteArray? {
        if (body.jStr("requestId") != requestId) return null
        val nonce = LanCrypto.unb64(body.jStr("nonce"))?.takeIf { it.size == LanCrypto.NONCE_BYTES } ?: return null
        val box = LanCrypto.unb64(body.jStr("box")) ?: return null
        LanCrypto.open(key, nonce, box, HubProtocol.aadPairStatus(requestId)) ?: return null
        return nonce
    }

    fun pairReply(grant: PairGrant, requestId: String, key: ByteArray, askNonce: ByteArray): String {
        val box = LanCrypto.seal(
            key, LanJson.write(grant.toJson()).encodeToByteArray(), HubProtocol.aadPairReply(requestId, askNonce)
        )
        return LanJson.write(mapOf("ok" to true, "nonce" to LanCrypto.b64(box.nonce), "box" to LanCrypto.b64(box.box)))
    }

    fun openPairReply(body: Map<String, Any?>, requestId: String, key: ByteArray, askNonce: ByteArray): PairGrant? {
        val nonce = LanCrypto.unb64(body.jStr("nonce"))?.takeIf { it.size == LanCrypto.NONCE_BYTES } ?: return null
        val box = LanCrypto.unb64(body.jStr("box")) ?: return null
        val plain = LanCrypto.open(key, nonce, box, HubProtocol.aadPairReply(requestId, askNonce)) ?: return null
        return LanJson.parseObject(plain.decodeToString())?.let { PairGrant.parse(it) }
    }

    // ================================================================ همگام‌سازی

    /** درخواستِ بسته‌شده، با nonceی که جواب به آن بسته است. */
    class Sealed(val headers: Map<String, String>, val body: String, val nonce: ByteArray)

    fun sealRequest(deviceId: String, deviceKey: ByteArray, path: String, time: Long, plain: Map<String, Any?>): Sealed {
        val keys = LanCrypto.directional(deviceKey)
        val nonce = LanPrims.random(LanCrypto.NONCE_BYTES)
        val box = LanPrims.gcmSeal(
            keys.request, nonce, LanJson.write(plain).encodeToByteArray(),
            HubProtocol.aadRequest(path, deviceId, time, nonce).encodeToByteArray(),
        )
        return Sealed(
            headers = mapOf(
                HubProtocol.HEADER_DEVICE to deviceId,
                HubProtocol.HEADER_TIME to time.toString(),
                HubProtocol.HEADER_NONCE to LanCrypto.b64(nonce),
            ),
            body = LanJson.write(mapOf("box" to LanCrypto.b64(box))),
            nonce = nonce,
        )
    }

    /** سرخط‌های یک درخواستِ پاکتی، پیش از دانستنِ کلید. */
    class Head(val deviceId: String, val time: Long, val nonce: ByteArray)

    private val DEVICE_ID = Regex("^[A-Za-z0-9-]{8,64}$")

    fun readHead(header: (String) -> String?): Head? {
        val id = header(HubProtocol.HEADER_DEVICE)?.trim()?.takeIf { DEVICE_ID.matches(it) } ?: return null
        val time = header(HubProtocol.HEADER_TIME)?.trim()?.toLongOrNull() ?: return null
        val nonce = LanCrypto.unb64(header(HubProtocol.HEADER_NONCE)?.trim())
            ?.takeIf { it.size == LanCrypto.NONCE_BYTES } ?: return null
        return Head(id, time, nonce)
    }

    /** کامپیوتر: پاکتِ درخواست با کلیدِ همین دستگاه باز می‌شود؟ */
    fun openRequest(head: Head, deviceKey: ByteArray, path: String, body: String): Map<String, Any?>? {
        val box = LanJson.parseObject(body)?.jStr("box")?.let { LanCrypto.unb64(it) } ?: return null
        val keys = LanCrypto.directional(deviceKey)
        val plain = LanPrims.gcmOpen(
            keys.request, head.nonce, box,
            HubProtocol.aadRequest(path, head.deviceId, head.time, head.nonce).encodeToByteArray(),
        ) ?: return null
        return LanJson.parseObject(plain.decodeToString())
    }

    fun sealResponse(head: Head, deviceKey: ByteArray, path: String, plain: Map<String, Any?>): String {
        val keys = LanCrypto.directional(deviceKey)
        val box = LanCrypto.seal(
            keys.response, LanJson.write(plain).encodeToByteArray(),
            HubProtocol.aadResponse(path, head.deviceId, head.nonce),
        )
        return LanJson.write(mapOf("ok" to true, "nonce" to LanCrypto.b64(box.nonce), "box" to LanCrypto.b64(box.box)))
    }

    /** گوشی: جواب با کلیدِ برگشت و به nonceِ همین درخواست بسته است؟ */
    fun openResponse(deviceId: String, deviceKey: ByteArray, path: String, requestNonce: ByteArray, body: String): Map<String, Any?>? {
        val o = LanJson.parseObject(body) ?: return null
        val nonce = LanCrypto.unb64(o.jStr("nonce"))?.takeIf { it.size == LanCrypto.NONCE_BYTES } ?: return null
        val box = LanCrypto.unb64(o.jStr("box")) ?: return null
        val keys = LanCrypto.directional(deviceKey)
        val plain = LanCrypto.open(keys.response, nonce, box, HubProtocol.aadResponse(path, deviceId, requestNonce))
            ?: return null
        return LanJson.parseObject(plain.decodeToString())
    }
}

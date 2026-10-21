package com.afghanjama.lan

import com.afghanjama.prefs.Settings

/**
 * آنچه گوشیِ «کارگاهِ وصل» (قراردادِ ۲) از جفت شدن نگه می‌دارد — جز کلید.
 *
 * **کلید اینجا نیست.** فقط در انبارِ امنِ سکو ([HubSecrets]): اندروید با
 * کلیدِ Keystore پیچیده، آیفون در Keychain. اینجا شناسهٔ دستگاه، نقش، نشانیِ
 * آخر، فاصلهٔ ساعت، آخرین تصویر و صفِ ارسال می‌ماند — هیچ‌کدام بی کلید به کاری
 * نمی‌آید.
 *
 * جدا از [com.afghanjama.prefs.LanPrefs]ِ قراردادِ ۱ است: گوشی‌ای که با
 * رمزِ شش‌رقمی وصل بود و حالا جفت می‌شود، تنظیمِ قدیمش دست نمی‌خورد تا اگر
 * جفت شدن نشد، همان قبلی کار کند.
 */
object HubLinkPrefs {
    private const val FILE = "hub_link"
    const val SECRET_NAME = "khayatyar.hub.device-key"

    private const val HUB_ID = "hub_id"
    private const val HUB_NAME = "hub_name"
    private const val HUB_VERSION = "hub_version"
    private const val DEVICE_ID = "device_id"
    private const val DEVICE_NAME = "device_name"
    private const val ROLE = "role"
    private const val PERSON = "person"
    private const val HOST = "host"
    private const val ALTS = "alternatives"
    private const val PORT = "port"
    private const val OFFSET = "clock_offset"
    private const val CURSOR = "cursor"
    private const val SNAPSHOT = "snapshot"
    private const val LAST_SYNC = "last_sync"
    private const val OUTBOX = "outbox"
    private const val OUTBOX_BAD = "outbox_damaged"

    fun paired(s: Settings): Boolean = s.getString(FILE, DEVICE_ID).isNotBlank()

    /** کلید و شناسه با هم؛ `null` اگر جفت نشده یا کلید در انبار نیست. */
    fun cred(s: Settings, store: SecretStore?): DeviceCred? {
        val id = s.getString(FILE, DEVICE_ID).ifBlank { return null }
        val key = store?.get(SECRET_NAME)?.takeIf { it.size == LanCrypto.KEY_BYTES } ?: return null
        return DeviceCred(
            deviceId = id,
            key = key,
            hubId = s.getString(FILE, HUB_ID),
            hubName = s.getString(FILE, HUB_NAME),
            role = HubRole.of(s.getString(FILE, ROLE)) ?: return null,
            person = s.getString(FILE, PERSON),
        )
    }

    /**
     * «اجازه» رسید: کلید **اول** در انبارِ امن؛ اگر نشد، هیچ چیزِ دیگری هم ذخیره
     * نمی‌شود و گوشی جفت‌نشده می‌ماند (نه اینکه کلید را جای ناامن بگذارد).
     */
    fun save(s: Settings, store: SecretStore?, grant: PairGrant, host: String, deviceName: String): Boolean {
        val key = grant.deviceKey ?: return false
        if (store == null || !store.put(SECRET_NAME, key)) return false
        s.putString(FILE, HUB_ID, grant.hubId)
        s.putString(FILE, HUB_NAME, grant.hubName)
        s.putString(FILE, ROLE, grant.role?.wire.orEmpty())
        s.putString(FILE, PERSON, grant.person)
        s.putString(FILE, HOST, host)
        s.putString(FILE, ALTS, grant.addresses.joinToString(","))
        s.putLong(FILE, PORT, grant.port.toLong())
        s.putLong(FILE, OFFSET, 0L)
        s.putString(FILE, CURSOR, "")
        s.putString(FILE, SNAPSHOT, "")
        s.putString(FILE, DEVICE_NAME, deviceName)
        // شناسه آخر از همه: [paired] فقط وقتی راست است که همهٔ بالا نشسته باشد.
        s.putString(FILE, DEVICE_ID, grant.deviceId.orEmpty())
        return true
    }

    /** «جدا کردنِ این گوشی». صفِ ارسال **می‌ماند** — هیچ کاری بی‌صدا دور ریخته نمی‌شود. */
    fun forget(s: Settings, store: SecretStore?) {
        store?.remove(SECRET_NAME)
        for (k in listOf(DEVICE_ID, HUB_ID, HUB_NAME, HUB_VERSION, ROLE, PERSON, HOST, ALTS, CURSOR, SNAPSHOT, DEVICE_NAME)) {
            s.remove(FILE, k)
        }
        s.remove(FILE, OFFSET)
        s.remove(FILE, LAST_SYNC)
    }

    fun hubName(s: Settings): String = s.getString(FILE, HUB_NAME)
    fun hubVersion(s: Settings): String = s.getString(FILE, HUB_VERSION)
    fun role(s: Settings): HubRole? = HubRole.of(s.getString(FILE, ROLE))
    fun person(s: Settings): String = s.getString(FILE, PERSON)
    fun deviceName(s: Settings): String = s.getString(FILE, DEVICE_NAME)
    fun host(s: Settings): String = s.getString(FILE, HOST)
    fun alternatives(s: Settings): List<String> = s.getString(FILE, ALTS).split(',').map { it.trim() }.filter { it.isNotEmpty() }
    fun port(s: Settings): Int = s.getLong(FILE, PORT, HubProtocol.PORT.toLong()).toInt().takeIf { it in 1..65535 } ?: HubProtocol.PORT
    fun offset(s: Settings): Long = s.getLong(FILE, OFFSET, 0L)
    fun cursor(s: Settings): String = s.getString(FILE, CURSOR)
    fun lastSync(s: Settings): Long? = s.getLong(FILE, LAST_SYNC, 0L).takeIf { it > 0 }

    fun setHost(s: Settings, host: String) = s.putString(FILE, HOST, host)
    fun setOffset(s: Settings, ms: Long) = s.putLong(FILE, OFFSET, ms)

    /** تصویرِ تازه و چکیده‌اش. */
    fun saveSnapshot(s: Settings, snapshot: HubSnapshot, cursor: String) {
        s.putString(FILE, SNAPSHOT, LanJson.write(snapshot.toJson()))
        s.putString(FILE, CURSOR, cursor)
    }

    fun snapshot(s: Settings): HubSnapshot? {
        val role = role(s) ?: return null
        return LanJson.parseObject(s.getString(FILE, SNAPSHOT))?.let { HubSnapshot.parse(it, role) }
    }

    /** آنچه هر جوابِ کامپیوتر دربارهٔ خودش می‌گوید. */
    fun saveReply(s: Settings, r: SyncReply, at: Long) {
        s.putLong(FILE, LAST_SYNC, at)
        s.putString(FILE, HUB_NAME, r.hubName)
        s.putString(FILE, HUB_VERSION, r.hubVersion)
        s.putString(FILE, ROLE, r.role.wire)
        s.putString(FILE, PERSON, r.person)
        if (r.addresses.isNotEmpty()) s.putString(FILE, ALTS, r.addresses.joinToString(","))
    }

    /**
     * صفِ ارسال. فایلِ خراب **خالی خوانده نمی‌شود**: متنش کنار گذاشته می‌شود
     * ([OUTBOX_BAD]) و صف از نو شروع می‌شود، تا دستِ کم دیده شود چه بود.
     */
    fun outbox(s: Settings): List<OutboxItem> {
        val raw = s.getString(FILE, OUTBOX)
        return Outbox.decode(raw) ?: run {
            s.putString(FILE, OUTBOX_BAD, raw)
            s.putString(FILE, OUTBOX, "")
            emptyList()
        }
    }

    fun saveOutbox(s: Settings, items: List<OutboxItem>) = s.putString(FILE, OUTBOX, Outbox.encode(items))
}

/**
 * گوشی با این کامپیوتر چه قراردادی حرف بزند — **و هرگز پایین نیاید.**
 *
 * گوشی‌ای که با قراردادِ ۲ جفت شده، با کامپیوتری که خودش را «فقط قراردادِ ۱»
 * معرفی کند به رمزِ شش‌رقمی برنمی‌گردد. ممکن است کامپیوتر واقعاً قدیمی شده
 * باشد (بازگشت به نسخهٔ پیش)، یا کسی روی وای‌فای جوابِ `/ping` را عوض کرده
 * باشد تا گوشی به راهِ بی‌رمزِ قدیم برود. در هر دو حالت گوشی می‌گوید چه شده و
 * چیزی نمی‌فرستد.
 */
object ProtocolChoice {

    enum class Use {
        /** قراردادِ ۲. */
        V2,

        /** قراردادِ ۱ (گوشیِ جفت‌نشده با کامپیوترِ قدیم). */
        V1,

        /** جفت‌شده با قراردادِ ۲، ولی کامپیوتر فقط ۱ می‌گوید: هیچ. */
        REFUSE_DOWNGRADE,

        /** گوشیِ قراردادِ ۱ و کامپیوترِ تازه: «یک بار دوباره وصل شوید». */
        OFFER_UPGRADE,
    }

    fun decide(pairedV2: Boolean, ping: PingInfo): Use = when {
        pairedV2 && ping.protocolMax >= 2 -> Use.V2
        pairedV2 -> Use.REFUSE_DOWNGRADE
        ping.protocolMax >= 2 -> Use.OFFER_UPGRADE
        else -> Use.V1
    }

    const val DOWNGRADE_REFUSED =
        "کامپیوترِ این نشانی فقط نسخهٔ قدیمِ اشتراک را نشان می‌دهد. این گوشی با نسخهٔ امن جفت شده و به رمزِ " +
            "شش‌رقمی برنمی‌گردد. اگر کامپیوتر به نسخهٔ پیش برگشته، آن را دوباره به ۱.۱۱ یا تازه‌تر به‌روز کنید."

    const val UPGRADE_OFFER =
        "این کامپیوتر نسخهٔ تازه دارد؛ برای امنیتِ بیشتر یک بار دوباره وصل شوید: روی کامپیوتر «افزودنِ گوشی» را بزنید و QRِ تازه را اسکن کنید."
}

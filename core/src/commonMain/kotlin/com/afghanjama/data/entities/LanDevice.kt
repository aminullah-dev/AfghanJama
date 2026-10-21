package com.afghanjama.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * یک گوشیِ جفت‌شده با کامپیوترِ کارگاه (قراردادِ ۲).
 *
 * **چرا در دفتر و نه در تنظیمات.** کلیدِ هر گوشی باید با دفتر جابه‌جا شود:
 * اگر کامپیوتر عوض شد و دفتر از پشتیبان برگشت، گوشی‌ها بی جفت شدنِ دوباره
 * وصل می‌مانند. پشتیبان همهٔ دفتر را دارد، پس این کلیدها را هم — همان
 * پشتیبانی که همهٔ حساب‌های کارگاه را دارد (`DELIVERY.md`).
 *
 * **قطعِ دسترسی کلید را پاک می‌کند** ([keyB64] خالی، [revokedAt] پر) و ردیف
 * برای تاریخچه می‌ماند؛ همگام‌سازیِ بعدیِ آن گوشی «ناشناس» است.
 *
 * نقش و آدم را کامپیوتر تعیین می‌کند، نه گوشی: کارگری که «تمام شد» می‌زند،
 * به نامِ [personLabel] ثبت می‌شود، هر نامی که گوشی بگوید.
 */
@Entity(tableName = "lan_devices")
data class LanDevice(
    @PrimaryKey val deviceId: String,
    val name: String,
    @ColumnInfo(defaultValue = "") val platform: String = "",
    /** `owner` / `worker` / `wall` — `HubRole.wire`. */
    val role: String,
    /** برچسبِ کارگر، مثلِ «[T10] احمد»؛ برای صاحب‌کار و تابلو خالی. */
    @ColumnInfo(defaultValue = "") val personLabel: String = "",
    /** کلیدِ ۳۲ بایتیِ دستگاه، Base64؛ خالی یعنی دسترسی برداشته شده. */
    val keyB64: String,
    val createdAt: Long,
    val lastSeenAt: Long? = null,
    @ColumnInfo(defaultValue = "") val lastIp: String = "",
    @ColumnInfo(defaultValue = "") val appVersion: String = "",
    val revokedAt: Long? = null,
) {
    val active: Boolean get() = revokedAt == null && keyB64.isNotEmpty()

    // کلید هرگز در گزارش نمی‌نشیند.
    override fun toString(): String =
        "LanDevice(deviceId=$deviceId, name=$name, role=$role, person=$personLabel, active=$active, key=…)"
}

/**
 * پاسخِ هر فرمانِ گوشیِ صاحب‌کار، با کلیدِ تکرارنشدنیِ همان فرمان.
 *
 * **چرا لازم شد (نقدِ ۱۰).** کارِ کارگر در `sync_requests` با `clientKey`ِ
 * یکتا می‌نشیند؛ ولی فرمانِ صاحب‌کار (تأیید، رد، پیگیریِ طلب) همان لحظه
 * اجرا می‌شود و جایی برای کلیدش نبود. جوابی که به گوشی نرسد، گوشی دوباره
 * می‌فرستد؛ بی این جدول، پیگیریِ طلب دو بار ثبت می‌شد. حالا همان جوابِ
 * اول برمی‌گردد.
 */
@Entity(tableName = "lan_commands")
data class LanCommand(
    @PrimaryKey val clientKey: String,
    val deviceId: String,
    val type: String,
    /** `APPLIED` یا `REJECTED`. */
    val status: String,
    @ColumnInfo(defaultValue = "") val result: String = "",
    val at: Long,
)

/**
 * شناسنامهٔ کارگاه برای گوشی‌ها — یک ردیف.
 *
 * **چرا در دفتر (نقدِ ۶).** گوشی کامپیوتر را با [hubId] می‌شناسد و فقط
 * کامپیوتری با همین شناسه را می‌پذیرد. اگر شناسه در تنظیماتِ کامپیوتر بود،
 * دفترِ بازیابی‌شده روی کامپیوترِ تازه شناسهٔ تازه می‌گرفت و همهٔ گوشی‌ها
 * آن را رد می‌کردند. حالا شناسه با دفتر می‌رود.
 *
 * [machine] کدِ دستگاهی است که آخرین بار سرور بود. دفتری که روی دستگاهِ
 * دیگری باز شود (بازیابی روی کامپیوترِ تازه) خودش روشن نمی‌شود تا کارفرما
 * بگوید «این کامپیوتر حالا مرکز است» — و اگر کامپیوترِ قبلی هنوز با همین
 * شناسه جواب بدهد، روشن نمی‌شود: دو مرکز با یک دفتر یعنی دو حسابِ جدا.
 */
@Entity(tableName = "lan_hub")
data class LanHubRow(
    @PrimaryKey val id: Int = 1,
    val hubId: String,
    @ColumnInfo(defaultValue = "") val hubName: String = "",
    @ColumnInfo(defaultValue = "") val machine: String = "",
    /** `AUTO` / `ON` / `OFF` — گوشی‌های قراردادِ ۱ ([LegacyMode]). */
    @ColumnInfo(defaultValue = "AUTO") val legacyV1: String = "AUTO",
    /** آخرین درخواستِ پذیرفتهٔ قراردادِ ۱. */
    val lastV1At: Long? = null,
    val createdAt: Long,
)

/**
 * گوشی‌های نسخهٔ قدیم (رمزِ شش‌رقمی، قراردادِ ۱) پذیرفته می‌شوند؟
 *
 * - [AUTO] پیش‌فرض: **فقط اگر در ۳۰ روزِ اخیر یکی آمده باشد.** کامپیوتری
 *   که هرگز گوشیِ قدیم نداشته درِ رمزِ شش‌رقمی را باز نگه نمی‌دارد (نقدِ
 *   ۳)، و کارگاهی که هنوز گوشیِ ۱.۱۰ دارد تا وقتی آن گوشی کار می‌کند
 *   بسته نمی‌شود؛ سی روز پس از آخرین بار خودش بسته می‌شود.
 * - [ON] و [OFF]: انتخابِ صریحِ کارفرما.
 */
enum class LegacyMode { AUTO, ON, OFF;
    companion object {
        const val AUTO_DAYS = 30
        fun of(s: String?): LegacyMode = entries.firstOrNull { it.name == s } ?: AUTO

        fun allowed(mode: LegacyMode, lastV1At: Long?, now: Long): Boolean = when (mode) {
            ON -> true
            OFF -> false
            AUTO -> lastV1At != null && now - lastV1At < AUTO_DAYS * 86_400_000L
        }
    }
}

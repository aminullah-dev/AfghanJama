package com.afghanjama.data

/**
 * یک گامِ اسکیما: از یک نسخه به نسخهٔ بعد، با SQLِ خالص.
 *
 * `sql` به ترتیب و در یک تراکنش اجرا می‌شود (تراکنش را خودِ Room باز
 * می‌کند). هیچ پارامترِ bind ندارد و عمداً: گامی که به مقدارِ زمانِ اجرا
 * یا به کرسر نیاز دارد، SQLِ خالص نیست و اینجا جایش نیست.
 */
class SchemaStep(val from: Int, val to: Int, val sql: List<String>)

/**
 * پایین‌ترین نسخه‌ای که نسخهٔ ویندوز می‌تواند باز کند.
 *
 * **۱۹ — همان‌جا که اندروید هم شروع می‌کند.** یعنی هر دفترِ گوشی که
 * مهاجرتِ واقعی دارد، روی پی‌سی هم بالا می‌آید.
 *
 * راهش سه تکه بود:
 *
 * | گام‌ها | کجا |
 * |---|---|
 * | ۴۰ گام (۱۰۲ دستور) | `LEGACY_STEPS` — از `Migrations.kt` استخراج، با `legacysql` نگهبانی |
 * | ۴۱→۴۲ و ۴۲→۴۳ | دستی در `:desktop` (`LegacyHandMigrations.kt`) — کرسر دارند و SQL نمی‌شوند |
 * | ۶۱ به بعد | `SCHEMA_STEPS`، مشترک با اندروید |
 *
 * سه مهاجرت (۳۵→۳۶، ۳۷→۳۸، ۳۸→۳۹) مهرِ زمان می‌خواستند؛ به‌جای دستی
 * نوشتنشان، نشانهٔ `{now}` در SQL پذیرفته شد و هر سکو سرِ اجرا پرش
 * می‌کند. یک تعریف ماند.
 *
 * `migrationgap` این عدد را با زنجیرهٔ واقعی می‌سنجد تا دروغ نشود —
 * با پایهٔ ساختگی آزموده شده.
 *
 * فایلِ قدیمی‌تر از پایه (یعنی زیرِ ۱۹، که مهاجرتی برایش وجود ندارد و
 * روی اندروید هم destructive بوده) با پیامِ صریح رد می‌شود، نه با خطای
 * خامِ Room و نه با پاک شدنِ داده.
 */
const val DESKTOP_BASELINE = 19

/**
 * همهٔ گام‌هایی که ویندوز می‌تواند اجرا کند: تاریخی + آینده.
 *
 * اندروید این را استفاده نمی‌کند — آنجا `ALL_MIGRATIONS`ِ دست‌نویس
 * اجرا می‌شود که روی گوشیِ کارفرما آزموده شده. فقط `SCHEMA_STEPS`
 * (آینده) بینِ دو سکو مشترک است.
 */
val DESKTOP_STEPS: List<SchemaStep> get() = LEGACY_STEPS + SCHEMA_STEPS

/**
 * گام‌های مشترکِ اسکیما — **از این‌جا به بعد، هر تغییرِ اسکیما اینجا
 * نوشته می‌شود، یک بار.**
 *
 * تا امروز مهاجرت‌ها فقط اندرویدی بودند و نسخهٔ ویندوز هیچ‌کدام را
 * نداشت. یعنی اولین باری که `DB_VERSION` جلو می‌رفت، دفترِ روی پی‌سی
 * دیگر باز نمی‌شد — و چون `fallbackToDestructiveMigration` روی دسکتاپ
 * عمداً نیست، کارگاه با یک پیغامِ خطا می‌ماند و کار می‌خوابید.
 *
 * حالا هر دو سکو از همین فهرست می‌خوانند:
 * - اندروید: `sharedMigrations()` که کنارِ همان ۴۲ تای قدیمی می‌نشیند.
 * - ویندوز: `desktopMigrations()`.
 *
 * دو پیاده‌سازیِ نازک، **یک تعریف**. همان استدلالی که با آن بازنویسی
 * با پایتون رد شد: دو تعریف از یک چیز، روزی سرِ پول با هم اختلاف پیدا
 * می‌کنند.
 *
 * **قاعده هنگام تغییرِ اسکیما:**
 * 1. `DB_VERSION` را یکی جلو ببرید.
 * 2. یک `SchemaStep` با همان `from`/`to` اینجا اضافه کنید.
 * 3. بررسیِ `migrationgap` می‌گوید اگر یادتان برود — پیش از CI.
 * 4. **ساختِ افزایشی را باور نکنید:** `DB_VERSION` یک `const val` است و
 *    سرِ کامپایل درجا نشانده می‌شود. در آزمایش دیده شد که با عوض شدنش
 *    KSP دوباره اجرا نمی‌شود و `DesktopDatabase_Impl` با **نسخهٔ قبلی**
 *    می‌ماند؛ آن‌وقت دفترِ تازه با عددِ قدیمی مهر می‌خورد و همه‌چیز
 *    سالم به نظر می‌رسد. پس بعد از تغییرِ این عدد، `desktop/build` و
 *    `core/build` پاک شوند. (`migrationSmoke` این را می‌گیرد — بندِ
 *    اولش دقیقاً همین‌جا قرمز شد.)
 *
 * اگر تغییری با SQLِ خالص بیان نمی‌شود (کرسر یا حسابِ زمانِ اجرا
 * می‌خواهد)، همان‌جا برای هر سکو دستی نوشته شود و اینجا نیاید؛ ولی
 * آن‌وقت هر دو سکو باید نوشته شوند، نه یکی.
 */
val SCHEMA_STEPS: List<SchemaStep> = listOf(

    /*
     * ۶۱ → ۶۲ — صندوقِ خروجیِ رویدادها.
     *
     * **اولین گامِ مشترک.** تا امروز این فهرست خالی بود و هر دو سکو
     * روی ۶۱ می‌ایستادند؛ این گام از همین‌جا به هر دو می‌رسد
     * (`sharedMigrations()` برای اندروید، `desktopMigrations()` برای
     * ویندوز) — یک تعریف، دو اجرا.
     *
     * **چرا بی‌خطر است:** فقط `CREATE TABLE` و ایندکس. هیچ جدولِ
     * موجودی خوانده یا نوشته نمی‌شود، پس روی دفترِ کارگاه — که
     * سال‌ها داده دارد — کاری جز اضافه شدنِ یک جدولِ خالی نمی‌کند.
     * برگشتش هم فقط `DROP TABLE` است.
     *
     * **چرا `IF NOT EXISTS`:** اگر مهاجرت نیمه‌کاره بماند و دوباره
     * اجرا شود، `CREATE TABLE`ِ خالی می‌شکند و کاربر با دفترِ
     * بازنشدنی می‌ماند. این شرط همان مسیر را بی‌خطر می‌کند.
     *
     * ستون‌ها عیناً با `DomainEvent` می‌خوانند. اگر یکی جا بماند،
     * Room سرِ باز کردن با «اسکیما با انتظار نمی‌خواند» می‌شکند —
     * و `schemacheck` پیش از آن قرمز می‌شود.
     */
    SchemaStep(
        61, 62,
        listOf(
            """
            CREATE TABLE IF NOT EXISTS domain_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                type TEXT NOT NULL,
                aggregate TEXT NOT NULL,
                aggregateId TEXT NOT NULL,
                payload TEXT NOT NULL,
                user TEXT NOT NULL,
                role TEXT NOT NULL,
                at INTEGER NOT NULL,
                processedAt INTEGER
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS index_domain_events_at ON domain_events (at)",
            "CREATE INDEX IF NOT EXISTS index_domain_events_processedAt " +
                "ON domain_events (processedAt)",
            "CREATE INDEX IF NOT EXISTS index_domain_events_aggregate_aggregateId " +
                "ON domain_events (aggregate, aggregateId)"
        )
    ),

    /**
     * «چه کسی» به تایم‌لاینِ سفارش اضافه می‌شود.
     *
     * `order_stage_logs` از روزِ اول هر تغییرِ مرحله را ثبت می‌کرد و
     * صفحهٔ جزئیاتِ سفارش هم نشانش می‌داد — ولی فقط «از کجا به کجا» و
     * «کِی». نامِ کسی که مرحله را جلو برد هیچ‌جا نبود.
     *
     * در کارگاه همین یک ستون است که اختلاف را تمام می‌کند: وقتی
     * سفارشی زودتر از موعد «تحویل شد» خورده، سؤال این نیست که کِی —
     * سؤال این است که **چه کسی**.
     *
     * `DEFAULT ''` لازم است، نه سلیقه: سطرهای تاریخی نامی ندارند و
     * ستونِ `NOT NULL` بی مقدارِ پیش‌فرض روی جدولِ پر اصلاً اضافه
     * نمی‌شود. موجودیت هم `@ColumnInfo(defaultValue = "")` دارد تا
     * انتظارِ Room با همین بخواند — همان الگوی `QcRecord` و
     * `OrderPhoto`. اگر یکی از این دو جا بماند، Room سرِ باز کردنِ
     * دفتر می‌شکند.
     */
    SchemaStep(
        62, 63,
        listOf(
            "ALTER TABLE `order_stage_logs` ADD COLUMN `user` TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE `order_stage_logs` ADD COLUMN `role` TEXT NOT NULL DEFAULT ''"
        )
    )
)

/**
 * گام‌ها به ترتیب، از `from` تا `to` — و اگر زنجیره پاره باشد، خطا.
 *
 * پارگی یعنی Room سرِ اجرا نمی‌تواند از نسخهٔ فایل به نسخهٔ امروز
 * برسد. بهتر است همین‌جا معلوم شود تا آن‌جا.
 */
fun schemaChain(from: Int, to: Int): List<SchemaStep> {
    if (to <= from) return emptyList()
    val byFrom = DESKTOP_STEPS.associateBy { it.from }
    val out = mutableListOf<SchemaStep>()
    var v = from
    while (v < to) {
        val step = byFrom[v]
            ?: throw IllegalStateException(
                "زنجیرهٔ مهاجرت پاره است: گامِ $v به ${v + 1} در SCHEMA_STEPS نیست"
            )
        out += step
        v = step.to
    }
    return out
}
